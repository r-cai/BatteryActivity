package com.example.batteryactivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryBroadcastReceiver(private val onBatteryDataReceived: (DataList) -> Unit) : BroadcastReceiver() {
    private var lastUpdateTime = 0L
    private val updateInterval = 1000L
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val currentTime = System.currentTimeMillis()

//                } else null
                if (currentTime - lastUpdateTime >= updateInterval) {
                    lastUpdateTime = currentTime
                    val data = DataList(
                        timestamp = Date(),
                        health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
                        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                        voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
                        temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
                        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false),
                        batteryLow = intent.getBooleanExtra(
                            BatteryManager.EXTRA_BATTERY_LOW,
                            false
                        ),
                        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
                        propertyCapacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && batteryManager != null) {
                            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        } else -1,
                        propertyChargeCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && batteryManager != null) {
                            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                        } else -1,
                        propertyCurrentAverage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && batteryManager != null) {
                            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                        } else -1,
                        propertyEnergyCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && batteryManager != null) {
                            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                        } else -1L,
                        isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && batteryManager != null) {
                            batteryManager.isCharging
                        } else null
                    )

                    onBatteryDataReceived(data)
                }
            }
        } catch (e: Exception) {
            Log.e("BatteryReceiver", "Error in onReceive: ${e.message}")
        }
    }


    private fun getStatusString(status: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
    }
}