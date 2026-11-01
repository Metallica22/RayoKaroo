package com.rayo.karoo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
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

        const val TYPE_RAYO_CONTROL = "rayo_control"
        const val TYPE_RAYO_BATTERY = "rayo_battery"
    }

    lateinit var bleManager: BleManager
        private set

    private lateinit var karooSystem: KarooSystemService
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val activeViewEmitters = mutableListOf<ViewEmitter>()
    private val activeBatteryEmitters = mutableListOf<Emitter<StreamState>>()

    override val types by lazy {
        listOf(
            RayoControlDataType(this, extension),
            RayoBatteryDataType(this, extension)
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        karooSystem = KarooSystemService(this)

        bleManager = BleManager(this, object : BleManager.Listener {
            override fun onConnectionStateChange(connected: Boolean, deviceName: String?, mac: String?) {
                updateAllDataFields()
                updateBatteryStreams()
            }

            override fun onBatteryUpdate(percent: Int) {
                updateAllDataFields()
                updateBatteryStreams()
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

        // Connect Karoo System Service and listen to Karoo Ride State (Auto-Start / Auto-Stop)
        karooSystem.connect { connected ->
            if (connected) {
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
                        is RideState.Idle -> {
                            // Ride ended! Turn off light
                            if (bleManager.isConnected) {
                                bleManager.setLight(false)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMac = prefs.getString(PREF_MAC, null)
        if (!savedMac.isNullOrEmpty() && !bleManager.isConnected) {
            bleManager.connect(savedMac)
        }
        return START_STICKY
    }

    fun connectBle(mac: String? = null) {
        val targetMac = mac ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_MAC, null)
        if (!targetMac.isNullOrEmpty()) {
            bleManager.connect(targetMac)
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

    fun registerViewEmitter(emitter: ViewEmitter) {
        synchronized(activeViewEmitters) {
            activeViewEmitters.add(emitter)
        }
        emitDataField(emitter)
    }

    fun unregisterViewEmitter(emitter: ViewEmitter) {
        synchronized(activeViewEmitters) {
            activeViewEmitters.remove(emitter)
        }
    }

    fun registerBatteryEmitter(emitter: Emitter<StreamState>) {
        synchronized(activeBatteryEmitters) {
            activeBatteryEmitters.add(emitter)
        }
        emitBatteryStream(emitter)
    }

    fun unregisterBatteryEmitter(emitter: Emitter<StreamState>) {
        synchronized(activeBatteryEmitters) {
            activeBatteryEmitters.remove(emitter)
        }
    }

    fun updateAllDataFields() {
        synchronized(activeViewEmitters) {
            for (emitter in activeViewEmitters) {
                emitDataField(emitter)
            }
        }
    }

    private fun updateBatteryStreams() {
        synchronized(activeBatteryEmitters) {
            for (emitter in activeBatteryEmitters) {
                emitBatteryStream(emitter)
            }
        }
    }

    private fun emitBatteryStream(emitter: Emitter<StreamState>) {
        if (!bleManager.isConnected) {
            emitter.onNext(StreamState.Searching)
        } else if (bleManager.batteryPercent >= 0) {
            val state = StreamState.Streaming(
                DataPoint(
                    dataTypeId = DataType.dataTypeId(extension, TYPE_RAYO_BATTERY),
                    values = mapOf(DataType.Field.SINGLE to bleManager.batteryPercent.toDouble())
                )
            )
            emitter.onNext(state)
        } else {
            emitter.onNext(StreamState.NotAvailable)
        }
    }

    private fun emitDataField(emitter: ViewEmitter) {
        val rv = RemoteViews(packageName, R.layout.view_rayo_datafield)

        // 1. Light Toggle Button
        val toggleIntent = Intent(this, RayoActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_LIGHT
        }
        val pToggle = PendingIntent.getBroadcast(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val pCycle = PendingIntent.getBroadcast(
            this,
            102,
            cycleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btnCycleProfile, pCycle)
        rv.setTextViewText(R.id.btnCycleProfile, "PROFILE ${bleManager.currentMode + 1}")

        // 3. Battery Text
        val batText = if (bleManager.batteryPercent >= 0) "${bleManager.batteryPercent}%" else "--%"
        rv.setTextViewText(R.id.tvBatteryField, batText)

        emitter.updateView(rv)
    }

    override fun onDestroy() {
        scope.cancel()
        bleManager.disconnect()
        karooSystem.disconnect()
        instance = null
        super.onDestroy()
    }
}

class RayoControlDataType(
    private val extensionContext: RayoExtension,
    extension: String,
) : DataTypeImpl(extension, RayoExtension.TYPE_RAYO_CONTROL) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        extensionContext.registerViewEmitter(emitter)
        emitter.setCancellable {
            extensionContext.unregisterViewEmitter(emitter)
        }
    }
}

class RayoBatteryDataType(
    private val extensionContext: RayoExtension,
    extension: String,
) : DataTypeImpl(extension, RayoExtension.TYPE_RAYO_BATTERY) {
    override fun startStream(emitter: Emitter<StreamState>) {
        extensionContext.registerBatteryEmitter(emitter)
        emitter.setCancellable {
            extensionContext.unregisterBatteryEmitter(emitter)
        }
    }
}
