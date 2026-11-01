package com.rayo.karoo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("MissingPermission")
class SettingsActivity : AppCompatActivity() {

    private lateinit var tvPairedStatus: TextView
    private lateinit var btnScanPair: Button
    private lateinit var swAutoStart: Switch
    private lateinit var spnDefaultProfile: Spinner
    private lateinit var btnTestToggle: Button
    private lateinit var tvSettingsLog: TextView

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val bleListener = object : BleManager.Listener {
        override fun onConnectionStateChange(connected: Boolean, deviceName: String?, mac: String?) {
            runOnUiThread {
                if (connected) {
                    val bat = RayoExtension.instance?.bleManager?.batteryPercent ?: -1
                    val batStr = if (bat >= 0) " ($bat%)" else ""
                    tvPairedStatus.text = "Verbunden: ${deviceName ?: "RAYO"} [$mac]$batStr"
                    tvPairedStatus.setTextColor(0xFF00E676.toInt())
                    tvSettingsLog.text = "Rayo verbunden!"
                    updateTestButtonText(RayoExtension.instance?.bleManager?.isLightOn ?: false)
                } else {
                    val savedMac = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE).getString(RayoExtension.PREF_MAC, null)
                    if (!savedMac.isNullOrEmpty()) {
                        tvPairedStatus.text = "Gekoppelt: $savedMac (getrennt)"
                        tvPairedStatus.setTextColor(0xFFFFAA00.toInt())
                    } else {
                        tvPairedStatus.text = getString(R.string.not_paired)
                        tvPairedStatus.setTextColor(0xFFFFFFFF.toInt())
                    }
                    btnTestToggle.text = "Rayo verbinden & testen"
                    tvSettingsLog.text = "Nicht verbunden."
                }
            }
        }

        override fun onBatteryUpdate(percent: Int) {
            runOnUiThread {
                val name = RayoExtension.instance?.bleManager?.connectedDeviceName ?: "RAYO"
                val mac = RayoExtension.instance?.bleManager?.connectedMac ?: ""
                tvPairedStatus.text = "Verbunden: $name [$mac] ($percent%)"
            }
        }

        override fun onLightStateUpdate(isOn: Boolean) {
            runOnUiThread {
                updateTestButtonText(isOn)
            }
        }

        override fun onActiveModeUpdate(mode: Int) {
            runOnUiThread {
                if (spnDefaultProfile.selectedItemPosition != mode) {
                    spnDefaultProfile.setSelection(mode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvPairedStatus = findViewById(R.id.tvPairedStatus)
        btnScanPair = findViewById(R.id.btnScanPair)
        swAutoStart = findViewById(R.id.swAutoStart)
        spnDefaultProfile = findViewById(R.id.spnDefaultProfile)
        btnTestToggle = findViewById(R.id.btnTestToggle)
        tvSettingsLog = findViewById(R.id.tvSettingsLog)

        checkPermissions()
        loadPreferences()

        // Ensure background extension service is running
        try {
            startService(Intent(this, RayoExtension::class.java))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error starting RayoExtension service", e)
        }

        // Profile spinner setup
        val profiles = arrayOf("Profile 1", "Profile 2", "Profile 3", "Profile 4")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, profiles)
        spnDefaultProfile.adapter = adapter
        val prefs = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE)
        spnDefaultProfile.setSelection(prefs.getInt(RayoExtension.PREF_DEFAULT_PROFILE, 0))

        spnDefaultProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(RayoExtension.PREF_DEFAULT_PROFILE, position).apply()
                RayoExtension.instance?.bleManager?.let { bm ->
                    if (bm.isConnected && bm.currentMode != position) {
                        bm.selectProfile(position)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        swAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(RayoExtension.PREF_AUTO_START, isChecked).apply()
        }

        btnScanPair.setOnClickListener {
            startPairingScan()
        }

        btnTestToggle.setOnClickListener {
            val ext = RayoExtension.instance
            if (ext == null) {
                startService(Intent(this, RayoExtension::class.java))
                tvSettingsLog.text = "Starte Rayo-Dienst… bitte gleich nochmal tippen."
                return@setOnClickListener
            }

            val bm = ext.bleManager
            if (!bm.isConnected) {
                val mac = prefs.getString(RayoExtension.PREF_MAC, null)
                if (mac.isNullOrEmpty()) {
                    tvSettingsLog.text = "Bitte zuerst ein Rayo koppeln!"
                } else {
                    tvSettingsLog.text = "Verbinde mit Rayo ($mac)…"
                    ext.connectBle(mac)
                }
                return@setOnClickListener
            }

            // Togglen
            val newState = !bm.isLightOn
            bm.setLight(newState)
            ext.updateAllDataFields()
            tvSettingsLog.text = if (newState) "Befehl gesendet: LICHT AN!" else "Befehl gesendet: LICHT AUS!"
            updateTestButtonText(newState)
        }
    }

    override fun onResume() {
        super.onResume()
        RayoExtension.instance?.bleManager?.addListener(bleListener)

        val bm = RayoExtension.instance?.bleManager
        if (bm != null && bm.isConnected) {
            val bat = if (bm.batteryPercent >= 0) " (${bm.batteryPercent}%)" else ""
            tvPairedStatus.text = "Verbunden: ${bm.connectedDeviceName ?: "RAYO"} [${bm.connectedMac}]$bat"
            tvPairedStatus.setTextColor(0xFF00E676.toInt())
            updateTestButtonText(bm.isLightOn)
        } else {
            val mac = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE).getString(RayoExtension.PREF_MAC, null)
            if (!mac.isNullOrEmpty()) {
                tvPairedStatus.text = "Gekoppelt: $mac (getrennt)"
                tvPairedStatus.setTextColor(0xFFFFAA00.toInt())
                // Try connecting in background
                RayoExtension.instance?.connectBle(mac)
            }
            btnTestToggle.text = "Rayo verbinden & testen"
        }
    }

    override fun onPause() {
        super.onPause()
        RayoExtension.instance?.bleManager?.removeListener(bleListener)
    }

    private fun updateTestButtonText(isLightOn: Boolean) {
        if (isLightOn) {
            btnTestToggle.text = "LICHT IST AN (Tippen für AUS)"
            btnTestToggle.setBackgroundColor(0xFF00E676.toInt())
            btnTestToggle.setTextColor(0xFF000000.toInt())
        } else {
            btnTestToggle.text = "LICHT IST AUS (Tippen für AN)"
            btnTestToggle.setBackgroundColor(0xFF282A33.toInt())
            btnTestToggle.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE)
        val mac = prefs.getString(RayoExtension.PREF_MAC, null)
        if (!mac.isNullOrEmpty()) {
            tvPairedStatus.text = getString(R.string.paired_device_format, mac)
            tvPairedStatus.setTextColor(0xFFFFAA00.toInt())
        } else {
            tvPairedStatus.text = getString(R.string.not_paired)
            tvPairedStatus.setTextColor(0xFFFFFFFF.toInt())
        }
        swAutoStart.isChecked = prefs.getBoolean(RayoExtension.PREF_AUTO_START, true)
    }

    private fun startPairingScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            tvSettingsLog.text = "Bluetooth ist deaktiviert!"
            return
        }
        if (isScanning) return

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        isScanning = true
        tvSettingsLog.text = getString(R.string.status_scanning)
        btnScanPair.isEnabled = false

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dev = result?.device ?: return
                val name = dev.name ?: ""
                val address = dev.address

                if (name.contains("RAYO", ignoreCase = true) || name.contains("CLIQ", ignoreCase = true)) {
                    stopScan(scanner, this)
                    savePairedDevice(name, address)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                btnScanPair.isEnabled = true
                tvSettingsLog.text = "Scan fehlgeschlagen ($errorCode)"
            }
        }

        try {
            scanner.startScan(null, settings, callback)
            handler.postDelayed({
                if (isScanning) {
                    stopScan(scanner, callback)
                    tvSettingsLog.text = getString(R.string.status_not_found)
                }
            }, 12000)
        } catch (e: Exception) {
            isScanning = false
            btnScanPair.isEnabled = true
            tvSettingsLog.text = "Fehler beim Scannen: ${e.message}"
        }
    }

    private fun stopScan(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback) {
        if (isScanning) {
            try { scanner.stopScan(callback) } catch (ignored: Exception) {}
            isScanning = false
            handler.post { btnScanPair.isEnabled = true }
        }
    }

    private fun savePairedDevice(name: String, mac: String) {
        val prefs = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(RayoExtension.PREF_MAC, mac).apply()

        tvPairedStatus.text = "Gekoppelt: $name ($mac)"
        tvPairedStatus.setTextColor(0xFF00E676.toInt())
        tvSettingsLog.text = "Rayo gekoppelt! Verbinde…"

        // Ensure service is running and connecting
        startService(Intent(this, RayoExtension::class.java))
        RayoExtension.instance?.connectBle(mac)
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        }
    }
}
