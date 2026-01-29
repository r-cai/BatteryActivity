package com.example.batteryactivity

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ServiceCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val TAG = "ServiceCheckWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "ServiceCheckWorker checking if service is running...")

        return try {
            val isRunning = isServiceRunning(applicationContext)

            if (!isRunning) {
                Log.d(TAG, "Service not running, attempting to restart...")
                restartService(applicationContext)
            } else {
                Log.d(TAG, "Service is already running")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ServiceCheckWorker", e)
            Result.failure()
        }
    }

    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BatteryMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun restartService(context: Context) {
        try {
            val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_START
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "Service restart attempted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service", e)
        }
    }
}