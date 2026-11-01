package com.rayo.karoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RayoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            RayoExtension.ACTION_TOGGLE_LIGHT -> {
                RayoExtension.instance?.toggleLight()
            }
            RayoExtension.ACTION_CYCLE_PROFILE -> {
                RayoExtension.instance?.cycleProfile()
            }
        }
    }
}
