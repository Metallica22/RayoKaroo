package com.rayo.karoo

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class BleManager(private val context: Context, initialListener: Listener? = null) {

    interface Listener {
        fun onConnectionStateChange(connected: Boolean, deviceName: String?, mac: String?)
        fun onBatteryUpdate(percent: Int)
        fun onLightStateUpdate(isOn: Boolean)
        fun onActiveModeUpdate(mode: Int)
    }

    companion object {
        private const val TAG = "RayoBleManager"

        // Nordic UART Service (NUS)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify (Device -> Host)
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write (Host -> Device)
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // Battery Service
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

        private const val SCAN_TIMEOUT_MS = 10000L
        private const val SETUP_TIMEOUT_MS = 8000L
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    init {
        if (initialListener != null) {
            listeners.add(initialListener)
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            if (isConnected) {
                listener.onConnectionStateChange(true, connectedDeviceName, connectedMac)
                if (batteryPercent >= 0) listener.onBatteryUpdate(batteryPercent)
                listener.onLightStateUpdate(isLightOn)
                listener.onActiveModeUpdate(currentMode)
            }
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    var isConnected = false
        private set
    var isConnecting = false
        private set
    var connectedDeviceName: String? = null
        private set
    var connectedMac: String? = null
        private set
    var isLightOn = false
        private set
    var currentMode = 0
        private set
    var batteryPercent = -1
        private set

    var targetMacAddress: String? = null
        private set

    // Pending action after connect
    var pendingLightToggleAfterConnect = false

    private val isScanning = AtomicBoolean(false)
    private var currentScanCallback: ScanCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var retryAttempt = 0
    private var autoReconnectEnabled = true

    private val scanTimeoutRunnable = Runnable {
        if (isScanning.get()) {
            Log.d(TAG, "Scan timeout reached without finding Rayo. Scheduling retry...")
            stopScan()
            scheduleReconnectRetry()
        }
    }

    private val gattSetupWatchdogRunnable = Runnable {
        if (isConnecting && !isConnected) {
            Log.w(TAG, "GATT setup watchdog timed out. Closing stalled GATT connection and retrying...")
            disconnectGattInternal()
            scheduleReconnectRetry()
        }
    }

    /**
     * Connect to Rayo using Scan-then-Connect.
     * On Karoo's busy radio, a blind connectGatt hangs; connecting immediately after
     * receiving an advertisement packet ensures the connection request lands in the
     * peripheral's listening window.
     */
    fun connect(mac: String? = null, autoTurnOnAfterConnect: Boolean = false) {
        val target = mac ?: targetMacAddress
        if (!target.isNullOrEmpty()) {
            targetMacAddress = target
        }

        if (autoTurnOnAfterConnect) {
            pendingLightToggleAfterConnect = true
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Cannot connect: Bluetooth adapter is null or disabled.")
            return
        }

        if (isConnected) {
            Log.d(TAG, "Already connected to Rayo ($connectedMac)")
            if (pendingLightToggleAfterConnect) {
                pendingLightToggleAfterConnect = false
                setLight(true)
            }
            return
        }

        retryAttempt = 0
        autoReconnectEnabled = true
        startScanThenConnect()
    }

    private fun startScanThenConnect() {
        if (isConnected || isScanning.get()) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE Scanner not available. Scheduling retry...")
            scheduleReconnectRetry()
            return
        }
        bleScanner = scanner

        val filters = mutableListOf<ScanFilter>()
        val targetMac = targetMacAddress

        if (!targetMac.isNullOrEmpty() && BluetoothAdapter.checkBluetoothAddress(targetMac)) {
            filters.add(ScanFilter.Builder().setDeviceAddress(targetMac).build())
        }

        // Also allow matching by name or NUS Service UUID
        filters.add(ScanFilter.Builder().setDeviceName("RAYO").build())
        filters.add(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(NUS_SERVICE_UUID)).build())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val address = device.address ?: return
                val devName = try { device.name ?: "" } catch (e: SecurityException) { "" }
                val scanRecord = result.scanRecord
                val recordName = scanRecord?.deviceName ?: ""
                val effectiveName = if (devName.isNotBlank()) devName else recordName

                val matchesMac = !targetMac.isNullOrEmpty() && address.equals(targetMac, ignoreCase = true)
                val matchesName = effectiveName.contains("RAYO", ignoreCase = true) || effectiveName.contains("CLIQ", ignoreCase = true)
                val hasNus = scanRecord?.serviceUuids?.any { it.uuid == NUS_SERVICE_UUID } == true

                if (matchesMac || matchesName || hasNus) {
                    Log.i(TAG, "🎯 Found Rayo: $effectiveName [$address] (RSSI=${result.rssi}). Stopping scan & connecting GATT...")
                    stopScan()

                    targetMacAddress = address
                    connectGattInternal(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
                isScanning.set(false)
                scheduleReconnectRetry()
            }
        }

        try {
            isScanning.set(true)
            currentScanCallback = scanCallback
            isConnecting = true
            scanner.startScan(filters, settings, scanCallback)
            Log.d(TAG, "Started Scan-then-Connect for Rayo (${targetMac ?: "any"})...")

            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan for Rayo", e)
            isScanning.set(false)
            scheduleReconnectRetry()
        }
    }

    private fun stopScan() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        if (isScanning.compareAndSet(true, false)) {
            try {
                currentScanCallback?.let { bleScanner?.stopScan(it) }
            } catch (ignored: Exception) {}
            currentScanCallback = null
        }
    }

    private fun connectGattInternal(device: BluetoothDevice) {
        disconnectGattInternal()

        isConnecting = true
        mainHandler.removeCallbacks(gattSetupWatchdogRunnable)
        mainHandler.postDelayed(gattSetupWatchdogRunnable, SETUP_TIMEOUT_MS)

        try {
            Log.d(TAG, "Invoking device.connectGatt(TRANSPORT_LE) for ${device.address}")
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connectGatt", e)
            isConnecting = false
            scheduleReconnectRetry()
        }
    }

    private fun disconnectGattInternal() {
        mainHandler.removeCallbacks(gattSetupWatchdogRunnable)
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (ignored: Exception) {}
        bluetoothGatt = null
        rxChar = null
        isConnected = false
        isConnecting = false
    }

    fun disconnect() {
        autoReconnectEnabled = false
        stopScan()
        disconnectGattInternal()
        connectedDeviceName = null
        connectedMac = null

        mainHandler.post {
            for (l in listeners) {
                l.onConnectionStateChange(false, null, null)
            }
        }
    }

    private fun scheduleReconnectRetry() {
        if (!autoReconnectEnabled || isConnected) return

        retryAttempt++
        // Backoff: 2s -> 4s -> 8s -> max 12s
        val delayMs = (2000L * (1 shl (retryAttempt.coerceAtMost(3) - 1))).coerceAtMost(12000L)
        Log.d(TAG, "Will retry Rayo Scan-then-Connect in ${delayMs / 1000}s (attempt #$retryAttempt)...")

        mainHandler.postDelayed({
            if (autoReconnectEnabled && !isConnected) {
                startScanThenConnect()
            }
        }, delayMs)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "GATT onConnectionStateChange status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnecting = true
                connectedDeviceName = gatt?.device?.name ?: "RAYO"
                connectedMac = gatt?.device?.address

                // Request HIGH priority for fast service discovery and initialization
                try {
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (ignored: Exception) {}

                mainHandler.postDelayed({
                    try {
                        val success = gatt?.discoverServices() ?: false
                        Log.d(TAG, "discoverServices initiated: $success")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in discoverServices", e)
                    }
                }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT disconnected (status=$status)")
                mainHandler.removeCallbacks(gattSetupWatchdogRunnable)
                val wasConnected = isConnected

                disconnectGattInternal()

                mainHandler.post {
                    for (l in listeners) {
                        l.onConnectionStateChange(false, null, null)
                    }
                }

                if (autoReconnectEnabled) {
                    scheduleReconnectRetry()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            mainHandler.removeCallbacks(gattSetupWatchdogRunnable)

            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.w(TAG, "onServicesDiscovered failed with status: $status")
                disconnectGattInternal()
                scheduleReconnectRetry()
                return
            }

            Log.i(TAG, "✅ Services discovered successfully on Rayo!")

            val nus = gatt.getService(NUS_SERVICE_UUID)
            if (nus != null) {
                rxChar = nus.getCharacteristic(NUS_RX_CHAR_UUID)
                val txChar = nus.getCharacteristic(NUS_TX_CHAR_UUID)

                if (rxChar != null) {
                    val props = rxChar!!.properties
                    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                        rxChar!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    } else {
                        rxChar!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    }
                }

                if (txChar != null) {
                    gatt.setCharacteristicNotification(txChar, true)
                    val desc = txChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                    if (desc != null) {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                    }
                }
            } else {
                Log.w(TAG, "NUS service not found on device!")
            }

            // Battery service
            val bat = gatt.getService(BATTERY_SERVICE_UUID)
            val batChar = bat?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
            if (batChar != null) {
                mainHandler.postDelayed({
                    try {
                        gatt.readCharacteristic(batChar)
                    } catch (ignored: Exception) {}
                }, 500)
            }

            isConnected = true
            isConnecting = false
            retryAttempt = 0

            // Request BALANCED priority after successful setup to conserve battery
            try {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
            } catch (ignored: Exception) {}

            val devName = gatt.device?.name ?: "RAYO"
            val devAddress = gatt.device?.address ?: ""
            connectedDeviceName = devName
            connectedMac = devAddress

            mainHandler.post {
                for (l in listeners) {
                    l.onConnectionStateChange(true, devName, devAddress)
                }
            }

            // Query initial mode & status
            mainHandler.postDelayed({
                selectProfile(0)
            }, 700)

            // If a light toggle was requested while connecting, execute it now!
            if (pendingLightToggleAfterConnect) {
                pendingLightToggleAfterConnect = false
                mainHandler.postDelayed({
                    setLight(true)
                }, 900)
            }
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
                    for (l in listeners) {
                        l.onBatteryUpdate(batteryPercent)
                        l.onLightStateUpdate(isLightOn)
                        l.onActiveModeUpdate(currentMode)
                    }
                }
            } else if (cmd == 0x20 && value.size >= 10) {
                // Mode info
                currentMode = value[2].toInt() and 0xFF
                mainHandler.post {
                    for (l in listeners) {
                        l.onActiveModeUpdate(currentMode)
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_LEVEL_CHAR_UUID == characteristic?.uuid) {
                val value = characteristic?.value
                if (value != null && value.isNotEmpty()) {
                    batteryPercent = value[0].toInt() and 0xFF
                    mainHandler.post {
                        for (l in listeners) {
                            l.onBatteryUpdate(batteryPercent)
                        }
                    }
                }
            }
        }
    }

    /**
     * Send light ON/OFF command to Rayo.
     * Uses opcode 0x11 with full 4-byte payload [0xFF, 0xFF, 0, on ? 1 : 0].
     */
    fun setLight(on: Boolean) {
        if (!isConnected || rxChar == null) {
            Log.w(TAG, "Cannot setLight: not connected or rxChar is null.")
            return
        }

        val payload = byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            if (on) 0x01.toByte() else 0x00.toByte()
        )
        val packet = buildPacket(0x11, payload)
        writeCommand(packet)
        isLightOn = on

        mainHandler.post {
            for (l in listeners) {
                l.onLightStateUpdate(on)
            }
        }
    }

    /**
     * Select profile 0..3 (Profile 1..4 in UI)
     * Rayo cmd_get_mode opcode 0x20 switches active profile
     */
    fun selectProfile(index: Int) {
        if (!isConnected || rxChar == null) return
        val clamped = index.coerceIn(0, 3)
        val packet = buildPacket(0x20, byteArrayOf(clamped.toByte()))
        writeCommand(packet)
        currentMode = clamped

        mainHandler.post {
            for (l in listeners) {
                l.onActiveModeUpdate(clamped)
            }
        }
    }

    private fun writeCommand(bytes: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = rxChar ?: return

        char.value = bytes
        try {
            val success = gatt.writeCharacteristic(char)
            Log.d(TAG, "writeCharacteristic(0x${bytes.joinToString("") { "%02X".format(it) }}) success=$success")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing command", e)
        }
    }

    private fun buildPacket(command: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 1
        val packet = ByteArray(len + 1)
        packet[0] = len.toByte()
        packet[1] = command.toByte()
        System.arraycopy(payload, 0, packet, 2, payload.size)
        return packet
    }
}
