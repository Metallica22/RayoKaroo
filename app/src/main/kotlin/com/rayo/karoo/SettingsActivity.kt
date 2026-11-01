package com.rayo.karoo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        // Profile spinner setup
        val profiles = arrayOf("Profile 1", "Profile 2", "Profile 3", "Profile 4")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, profiles)
        spnDefaultProfile.adapter = adapter
        val prefs = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE)
        spnDefaultProfile.setSelection(prefs.getInt(RayoExtension.PREF_DEFAULT_PROFILE, 0))

        spnDefaultProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(RayoExtension.PREF_DEFAULT_PROFILE, position).apply()
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
            RayoExtension.instance?.toggleLight()
            tvSettingsLog.text = "Sent light toggle command to Rayo!"
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(RayoExtension.PREFS_NAME, Context.MODE_PRIVATE)
        val mac = prefs.getString(RayoExtension.PREF_MAC, null)
        if (!mac.isNullOrEmpty()) {
            tvPairedStatus.text = getString(R.string.paired_device_format, mac)
            tvPairedStatus.setTextColor(0xFF00E676.toInt())
        } else {
            tvPairedStatus.text = getString(R.string.not_paired)
            tvPairedStatus.setTextColor(0xFFFFFFFF.toInt())
        }
        swAutoStart.isChecked = prefs.getBoolean(RayoExtension.PREF_AUTO_START, true)
    }

    private fun startPairingScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            tvSettingsLog.text = "Bluetooth is disabled!"
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
                tvSettingsLog.text = "Scan failed ($errorCode)"
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
            tvSettingsLog.text = "Error scanning: ${e.message}"
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

        tvPairedStatus.text = getString(R.string.paired_device_format, "$name ($mac)")
        tvPairedStatus.setTextColor(0xFF00E676.toInt())
        tvSettingsLog.text = getString(R.string.status_paired)

        // Trigger immediate connect in extension
        RayoExtension.instance?.let { ext ->
            ext.toggleLight()
        }
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
