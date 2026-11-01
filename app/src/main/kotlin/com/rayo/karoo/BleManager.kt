package com.rayo.karoo

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
class BleManager(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnectionStateChange(connected: Boolean, deviceName: String?, mac: String?)
        fun onBatteryUpdate(percent: Int)
        fun onLightStateUpdate(isOn: Boolean)
        fun onActiveModeUpdate(mode: Int)
    }

    companion object {
        private const val TAG = "RayoKarooBle"

        // Nordic UART Service (NUS)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // Battery Service
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    var isConnected = false
        private set
    var isLightOn = false
        private set
    var currentMode = 0
        private set
    var batteryPercent = -1
        private set

    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(targetMac: String? = null) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (isConnected) return

        if (!targetMac.isNullOrEmpty()) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(targetMac)
                connectToDevice(device)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving remote device MAC: $targetMac", e)
            }
        }

        // Otherwise scan for Rayo
        startScan(targetMac)
    }

    private fun startScan(filterMac: String?) {
        if (isScanning) return
        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        isScanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val name = device.name ?: ""
                val address = device.address

                val matchesMac = !filterMac.isNullOrEmpty() && address.equals(filterMac, ignoreCase = true)
                val matchesName = name.contains("RAYO", ignoreCase = true) || name.contains("CLIQ", ignoreCase = true)

                if (matchesMac || matchesName) {
                    stopScan(this)
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
            }
        }

        try {
            bleScanner?.startScan(null, settings, callback)
            mainHandler.postDelayed({ stopScan(callback) }, 15000)
        } catch (e: Exception) {
            isScanning = false
        }
    }

    private fun stopScan(callback: ScanCallback) {
        if (isScanning) {
            try {
                bleScanner?.stopScan(callback)
            } catch (ignored: Exception) {}
            isScanning = false
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting GATT", e)
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (ignored: Exception) {}
        isConnected = false
        rxChar = null
        listener.onConnectionStateChange(false, null, null)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                mainHandler.postDelayed({
                    gatt?.discoverServices()
                }, 400)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                rxChar = null
                mainHandler.post {
                    listener.onConnectionStateChange(false, null, null)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) return

            val nus = gatt.getService(NUS_SERVICE_UUID)
            if (nus != null) {
                rxChar = nus.getCharacteristic(NUS_RX_CHAR_UUID)
                val txChar = nus.getCharacteristic(NUS_TX_CHAR_UUID)

                if (txChar != null) {
                    gatt.setCharacteristicNotification(txChar, true)
                    val desc = txChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                    if (desc != null) {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                    }
                }
            }

            // Battery service
            val bat = gatt.getService(BATTERY_SERVICE_UUID)
            val batChar = bat?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
            if (batChar != null) {
                mainHandler.postDelayed({
                    gatt.readCharacteristic(batChar)
                }, 800)
            }

            mainHandler.post {
                listener.onConnectionStateChange(true, gatt.device.name ?: "RAYO", gatt.device.address)
            }

            // Query initial status after 1 second
            mainHandler.postDelayed({
                queryStatus()
            }, 1000)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val value = characteristic?.value ?: return
            if (value.size < 2) return

            val cmd = value[1].toInt() and 0xFF

            if (cmd == 0x30 && value.size >= 5) {
                // Status packet: [Len, 0x30, Battery, LightState, Mode, Theft]
                batteryPercent = value[2].toInt() and 0xFF
                isLightOn = (value[3].toInt() and 0xFF) % 2 == 1
                currentMode = value[4].toInt() and 0xFF

                mainHandler.post {
                    listener.onBatteryUpdate(batteryPercent)
                    listener.onLightStateUpdate(isLightOn)
                    listener.onActiveModeUpdate(currentMode)
                }
            } else if (cmd == 0x20 && value.size >= 10) {
                // Mode info
                currentMode = value[2].toInt() and 0xFF
                mainHandler.post {
                    listener.onActiveModeUpdate(currentMode)
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_LEVEL_CHAR_UUID == characteristic?.uuid) {
                val value = characteristic?.value
                if (value != null && value.isNotEmpty()) {
                    batteryPercent = value[0].toInt() and 0xFF
                    mainHandler.post {
                        listener.onBatteryUpdate(batteryPercent)
                    }
                }
            }
        }
    }

    fun setLight(on: Boolean) {
        val payload = byteArrayOf(if (on) 1.toByte() else 0.toByte(), 0.toByte())
        if (sendCommand(0x10.toByte(), payload)) {
            isLightOn = on
            listener.onLightStateUpdate(on)
        }
    }

    fun selectProfile(index: Int) {
        val payload = byteArrayOf((index and 0x03).toByte())
        if (sendCommand(0x22.toByte(), payload)) {
            currentMode = index
            listener.onActiveModeUpdate(index)
        }
    }

    fun queryStatus() {
        sendCommand(0x11.toByte(), null)
    }

    private fun sendCommand(cmd: Byte, payload: ByteArray?): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = rxChar ?: return false
        val pLen = payload?.size ?: 0
        val packet = ByteArray(pLen + 2)
        packet[0] = (pLen + 1).toByte()
        packet[1] = cmd
        if (payload != null && pLen > 0) {
            System.arraycopy(payload, 0, packet, 2, pLen)
        }

        char.value = packet
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return gatt.writeCharacteristic(char)
    }
}
