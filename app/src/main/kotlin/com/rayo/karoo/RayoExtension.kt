package com.rayo.karoo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooExtension
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.ViewEmitter
import kotlinx.coroutines.*

class RayoExtension : KarooExtension("rayo", "1.0.0") {

    companion object {
        var instance: RayoExtension? = null
            private set

        const val ACTION_TOGGLE_LIGHT = "com.rayo.karoo.ACTION_TOGGLE_LIGHT"
        const val ACTION_CYCLE_PROFILE = "com.rayo.karoo.ACTION_CYCLE_PROFILE"

        const val PREFS_NAME = "rayo_karoo_prefs"
        const val PREF_MAC = "paired_mac"
        const val PREF_AUTO_START = "auto_start_enabled"
        const val PREF_DEFAULT_PROFILE = "default_profile"
    }

    private lateinit var bleManager: BleManager
    private lateinit var karooSystem: KarooSystemService
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val activeEmitters = mutableListOf<ViewEmitter>()

    override fun onCreate() {
        super.onCreate()
        instance = this

        karooSystem = KarooSystemService(this)

        bleManager = BleManager(this, object : BleManager.Listener {
            override fun onConnectionStateChange(connected: Boolean, deviceName: String?, mac: String?) {
                updateAllDataFields()
            }

            override fun onBatteryUpdate(percent: Int) {
                updateAllDataFields()
            }

            override fun onLightStateUpdate(isOn: Boolean) {
                updateAllDataFields()
            }

            override fun onActiveModeUpdate(mode: Int) {
                updateAllDataFields()
            }
        })

        // Auto-connect to paired MAC if available
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMac = prefs.getString(PREF_MAC, null)
        if (!savedMac.isNullOrEmpty()) {
            bleManager.connect(savedMac)
        }

        // Listen to Karoo Ride State (Auto-Start / Auto-Stop)
        karooSystem.addConsumer<RideState> { rideState ->
            val autoStart = prefs.getBoolean(PREF_AUTO_START, true)
            if (!autoStart) return@addConsumer

            when (rideState) {
                is RideState.Recording -> {
                    // Ride started or resumed! Turn on light
                    if (!bleManager.isConnected) {
                        bleManager.connect(prefs.getString(PREF_MAC, null))
                    }
                    scope.launch {
                        delay(1200) // Small delay for GATT connection handshake
                        val defProfile = prefs.getInt(PREF_DEFAULT_PROFILE, 0)
                        bleManager.selectProfile(defProfile)
                        bleManager.setLight(true)
                    }
                }
                is RideState.Stopped -> {
                    // Ride ended! Turn off light
                    if (bleManager.isConnected) {
                        bleManager.setLight(false)
                    }
                }
                else -> {}
            }
        }
    }

    fun toggleLight() {
        if (!bleManager.isConnected) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            bleManager.connect(prefs.getString(PREF_MAC, null))
            return
        }
        bleManager.setLight(!bleManager.isLightOn)
        updateAllDataFields()
    }

    fun cycleProfile() {
        if (!bleManager.isConnected) return
        val next = (bleManager.currentMode + 1) % 4
        bleManager.selectProfile(next)
        updateAllDataFields()
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        synchronized(activeEmitters) {
            activeEmitters.add(emitter)
        }
        emitDataField(emitter)

        emitter.setCancellable {
            synchronized(activeEmitters) {
                activeEmitters.remove(emitter)
            }
        }
    }

    private fun updateAllDataFields() {
        synchronized(activeEmitters) {
            for (emitter in activeEmitters) {
                emitDataField(emitter)
            }
        }
    }

    private fun emitDataField(emitter: ViewEmitter) {
        val rv = RemoteViews(packageName, R.layout.view_rayo_datafield)

        // 1. Light Toggle Button
        val toggleIntent = Intent(this, RayoActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_LIGHT
        }
        val pToggle = PendingIntent.getBroadcast(this, 101, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btnToggleLight, pToggle)

        if (!bleManager.isConnected) {
            rv.setTextViewText(R.id.btnToggleLight, "CONNECT RAYO")
            rv.setTextColor(R.id.btnToggleLight, Color.parseColor("#9E9EA7"))
        } else if (bleManager.isLightOn) {
            rv.setTextViewText(R.id.btnToggleLight, "LIGHT ON")
            rv.setTextColor(R.id.btnToggleLight, Color.parseColor("#00E676"))
        } else {
            rv.setTextViewText(R.id.btnToggleLight, "LIGHT OFF")
            rv.setTextColor(R.id.btnToggleLight, Color.parseColor("#FF2A55"))
        }

        // 2. Profile Cycle Button
        val cycleIntent = Intent(this, RayoActionReceiver::class.java).apply {
            action = ACTION_CYCLE_PROFILE
        }
        val pCycle = PendingIntent.getBroadcast(this, 102, cycleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.btnCycleProfile, pCycle)
        rv.setTextViewText(R.id.btnCycleProfile, "PROFILE ${bleManager.currentMode + 1}")

        // 3. Battery Text
        val batText = if (bleManager.batteryPercent >= 0) "${bleManager.batteryPercent}%" else "--%"
        rv.setTextViewText(R.id.tvBatteryField, batText)

        emitter.onNext(rv)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        bleManager.disconnect()
        instance = null
    }
}
