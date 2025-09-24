package com.example.batteryactivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class AlarmHelper(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private lateinit var alarmIntent: PendingIntent

    fun initialize(receiverClass: Class<out BroadcastReceiver>) {
        val intent = Intent(context, receiverClass)
        alarmIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setAlarm(intervalMinutes: Int) {
        cancelAlarm()

        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMillis

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        alarmIntent
                    )
                }
            }
            true -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    alarmIntent
                )
            }
            else -> {
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    intervalMillis,
                    alarmIntent
                )
            }
        }
    }

    fun cancelAlarm() {
        try {
            alarmManager.cancel(alarmIntent)
        } catch (e: Exception) {
            Log.e("AlarmHelper", "Error canceling alarm", e)
        }
    }
}