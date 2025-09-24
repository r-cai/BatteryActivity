package com.example.batteryactivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class BatteryBroadcastReceiver(private val onBatteryDataReceived: (DataList) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // Start a service to do the actual work
        val serviceIntent = Intent(context, MainActivity::class.java)

        val serviceIntent1 = Intent(context, BatteryMonitorService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val data = DataList(
                timestamp = Date(),
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
                temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false),
                batteryLow = intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
                propertyCapacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                } else -1,
                propertyChargeCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                } else -1,
                propertyCurrentAverage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                } else -1,
                propertyEnergyCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.getSystemService(BatteryManager::class.java)
                        .getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                } else -1L,
                isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(BatteryManager::class.java)
                        .isCharging
                } else null
            )
//             2. Create and send broadcast with battery data
            val updateIntent = Intent("BATTERY_DATA_UPDATE").apply {
                putExtra("TIMESTAMP", data.timestamp.time)
                putExtra("LEVEL", data.level)
                putExtra("VOLTAGE", data.voltage)
                putExtra("TEMPERATURE", data.temperature)
                putExtra("CURRENT  ", data.temperature)
                putExtra("CAPACITY", data.propertyCapacity)
                putExtra("CHARGE", data.propertyChargeCounter)
                putExtra("ENERGY", data.propertyEnergyCounter)
            }

            val activity = context as MainActivity
            activity.addDataPoint(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).toDouble()/1000, context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toDouble()/1000000)
            onBatteryDataReceived(data)
            // Write to file using application context
            val fileName = "battery_log_data.txt"
            val timestamp = SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault())
                .format(data.timestamp)

            val fileContent = "$timestamp\t${data.voltage.toFloat() / 1000}V\t${data.temperature/10f}°C\t${data.propertyCurrentAverage.toFloat()/1000}mA\t${data.level}%\t${getStatusString(data.status)}\t${data.propertyChargeCounter}mAH\n"

            // 3. Send broadcast (use LocalBroadcastManager for better security)
            LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
            // Use application context to avoid memory leaks
            context.applicationContext.openFileOutput(fileName, Context.MODE_APPEND).use { outputStream ->
                outputStream.write(fileContent.toByteArray())
            }
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