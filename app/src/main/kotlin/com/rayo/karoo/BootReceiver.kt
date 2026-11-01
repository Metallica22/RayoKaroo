package com.rayo.karoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            Log.d("RayoBootReceiver", "Boot completed / package replaced: starting RayoExtension")
            try {
                context.startService(Intent(context, RayoExtension::class.java))
            } catch (e: Exception) {
                Log.e("RayoBootReceiver", "Failed to start RayoExtension on boot", e)
            }
        }
    }
}
