package com.example.batteryactivity

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.batteryactivity.R.drawable.ic_battery_monitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class BatteryMonitorService : Service() {

    companion object {
        private const val TAG = "BatteryMonitorService"  // Add logging tag
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "BatteryMonitorForeground"
        private const val CHANNEL_NAME = "Battery Monitor Service"
        private const val DEFAULT_UPDATE_INTERVAL = 300000L
        private var wakeLock: PowerManager.WakeLock? = null

        // Actions
        const val ACTION_START = "com.example.batteryactivity.ACTION_START"
        const val ACTION_STOP = "com.example.batteryactivity.ACTION_STOP"
        const val ACTION_UPDATE_INTERVAL = "com.example.batteryactivity.ACTION_UPDATE_INTERVAL"
        const val ACTION_BATTERY_DATA = "com.example.batteryactivity.ACTION_BATTERY_DATA"

        // Extras
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_VOLTAGE = "voltage"
        const val EXTRA_CURRENT = "current"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_TEMPERATURE = "temperature"
        const val EXTRA_STATUS = "status"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val ACTION_SAVE_LOG = "com.example.batteryactivity.ACTION_SAVE_LOG"
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private var scheduledTask: ScheduledFuture<*>? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var updateInterval = DEFAULT_UPDATE_INTERVAL

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate() called")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        createNotificationChannel()
        Log.d(TAG, "Notification manager and battery manager initialized")
    }
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting foreground monitoring")
                startMonitoring()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground monitoring")
                stopMonitoring()
                stopSelf()
            }
            ACTION_UPDATE_INTERVAL -> {
                updateInterval = intent.getLongExtra(EXTRA_INTERVAL, DEFAULT_UPDATE_INTERVAL)
                Log.d(TAG, "Update interval changed to: $updateInterval ms")
                restartMonitoring()
            }
            ACTION_SAVE_LOG -> {
                Log.d(TAG, "Saving log to Downloads")
                saveBatteryLogToDownloads()
            }
            else -> {
                Log.d(TAG, "No action specified, starting monitoring by default")
                startMonitoring()
            }
        }
        return START_STICKY
    }
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryMonitorService::WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)  // 10 minutes timeout
            Log.d(TAG, "WakeLock acquired")
        }
    }
    private fun startMonitoring() {
        Log.d(TAG, "startMonitoring() called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
        try {
            val notification = buildNotification()
            Log.d(TAG, "Notification built successfully")

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Service moved to foreground with notification ID: $NOTIFICATION_ID")

            restartMonitoring()

            logToFile("Service started - ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
            Log.d(TAG, "Battery monitoring started with interval: $updateInterval ms")

            Handler(mainLooper).postDelayed({
                collectBatteryData()
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring: ${e.message}", e)
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "stopMonitoring() called")
        scheduledTask?.cancel(true)
        scheduledTask = null

        logToFile("Service stopped - ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
    }

    private fun restartMonitoring() {
        Log.d(TAG, "restartMonitoring() called")
        scheduledTask?.cancel(true)

        scheduledTask = scheduler.scheduleAtFixedRate(
            {
                Log.d(TAG, "Scheduled task executing - collecting battery data")
                collectBatteryData()
            },
            0,
            updateInterval,
            TimeUnit.MILLISECONDS
        )
        Log.d(TAG, "Scheduled task restarted with interval: $updateInterval ms")
    }

    private fun collectBatteryData() {
        // Acquire wake lock before starting battery data collection
        acquireWakeLock()

        try {
            Log.d(TAG, "collectBatteryData() - Starting data collection with WakeLock")

            val batteryIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_EXPORTED  // System broadcast
                )
            } else {
                applicationContext.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            } ?: run {
                Log.e(TAG, "Battery intent is null")
                return
            }

            val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } else -1

            val propertyCapacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else -1

            val propertyChargeCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } else -1

            val propertyEnergyCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            } else -1L

            val percentage = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).roundToInt()
            } else -1

            Log.d(TAG, "Battery Data - Level: $percentage%, Voltage: ${voltage.toFloat() / 1000}V, " +
                    "Current: ${current.toFloat() / 1000}mA, Temp: ${temperature/10f}°C, " +
                    "Status: ${getStatusString(status)}")

            // log
            logBatteryData(voltage, current, temperature, percentage, status,
                propertyChargeCounter, propertyCapacity, propertyEnergyCounter)

            // ui update
            val broadcastIntent = Intent(ACTION_BATTERY_DATA).apply {
                putExtra(EXTRA_VOLTAGE, voltage)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_LEVEL, percentage)
                putExtra(EXTRA_TEMPERATURE, temperature)
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
                // Add additional data if needed by UI
                putExtra("capacity", propertyCapacity)
                putExtra("chargeCounter", propertyChargeCounter)
                putExtra("energyCounter", propertyEnergyCounter)
            }

            sendBroadcast(broadcastIntent)
            Log.d(TAG, "Broadcast sent to activity")

            updateNotification(percentage, status)
            Log.d(TAG, "Notification updated")

        } catch (e: Exception) {
            Log.e(TAG, "Error collecting battery data: ${e.message}", e)
        } finally {
            releaseWakeLock()
            Log.d(TAG, "WakeLock released after data collection")
        }
    }

    private fun logBatteryData(voltage: Int, current: Int, temperature: Int,
                               level: Int, status: Int, chargeCounter: Int,
                               capacity: Int, energyCounter: Long) {
        val timestamp = SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault()).format(Date())

        val fileContent = "$timestamp\t${voltage.toFloat() / 1000}V\t" +
                "${temperature/10f}°C\t${current.toFloat()/1000}mA\t" +
                "${level}%\t${getStatusString(status)}\t" +
                "${chargeCounter}mAH\t${capacity}%\t${energyCounter}nWh\n"

        try {
            applicationContext.openFileOutput("battery_log_data.txt", Context.MODE_APPEND).use { outputStream ->
                outputStream.write(fileContent.toByteArray())
            }
            Log.d(TAG, "Data logged to file: $level%, ${voltage.toFloat()/1000}V")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging battery data to file", e)
        }
    }
    private fun generateLogHeader(): String {
        return buildString {
            append("timestamp\t\t\tvoltage\ttemp\tcurrent\tlevel\tstatus\tcapacity\tcapacity %\tenergy\n")
            append("--------------------------------------------------------------------------------------------------------\n")
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

    private fun logToFile(message: String) {
        try {
            val file = File(filesDir, "battery_foreground_log.txt")
            file.appendText("$message\n")
            Log.d(TAG, "Service log updated: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to service log file", e)
        }
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel() called - SDK: ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background battery monitoring service"
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                    enableLights(false)
                }

                // Check if channel already exists
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel == null) {
                    notificationManager.createNotificationChannel(channel)
                    Log.d(TAG, "Notification channel created: $CHANNEL_ID")
                } else {
                    Log.d(TAG, "Notification channel already exists: $CHANNEL_ID")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Notification channel not needed (API < 26)")
        }
    }

    private fun buildNotification(): Notification {
        Log.d(TAG, "buildNotification() called")

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconResId = if (resources.getIdentifier("ic_battery_monitor", "drawable", packageName) != 0) {
            ic_battery_monitor
        } else {
            android.R.drawable.ic_lock_idle_charging  // Fallback system icon
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Monitor Running")
            .setContentText("Collecting battery data every 5 minutes")
            .setSmallIcon(iconResId)
            .setContentIntent(openIntent)               // Click opens app
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)                           // Cannot be dismissed while service runs
            .setOnlyAlertOnce(true)                     // No sound/vibration on updates
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)                       // Don't auto-cancel
            .build()
    }

    private fun updateNotification(level: Int, status: Int) {
        Log.d(TAG, "updateNotification() called with level: $level%, status: $status")

        val iconResId = if (resources.getIdentifier("ic_battery_monitor", "drawable", packageName) != 0) {
            ic_battery_monitor
        } else {
            android.R.drawable.ic_lock_idle_charging
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery: $level%")
            .setContentText("Status: ${getStatusString(status)}")
            .setSmallIcon(iconResId)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated with ID: $NOTIFICATION_ID")
    }

    // save/export log file on demand
    fun saveBatteryLogToDownloads() {
        Log.d(TAG, "saveBatteryLogToDownloads() called")
        try {
            val fileName = "battery_log_data.txt"
            val sourceFile = File(filesDir, fileName)

            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                Toast.makeText(this, "No battery data collected yet", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "No data to save - file empty or doesn't exist")
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destFileName = "battery_log_$timestamp.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ (API 29+)
                saveUsingMediaStore(destFileName, sourceFile)
                verifyDownload(destFileName)
            } else {
                saveToDownloadsLegacy(destFileName, sourceFile)
            }
            clearLogFile(sourceFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Download error", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveUsingMediaStore(destFileName: String, sourceFile: File) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, destFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        try {
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create file in Downloads")

            resolver.openOutputStream(uri)?.use { outputStream ->
                val header="timestamp\tvoltage\ttemp\tcurrent\tlevel%\tstatus\tcapacity\tcapacity %\tenergy\n" +
                        "--------------------------------------------------------------------------------------------------------\n"
                outputStream.write(header.toByteArray())
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Toast.makeText(this, "File saved to Downloads/$destFileName", Toast.LENGTH_LONG).show()
            showDownloadCompleteNotification(destFileName, uri)
            Log.d(TAG, "File saved using MediaStore: $destFileName")
        } catch (e: Exception) {
            throw Exception("Failed to save file: ${e.message}")
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun saveToDownloadsLegacy(destFileName: String, sourceFile: File) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            if (!downloadsDir.mkdirs()) {
                throw Exception("Couldn't create Downloads directory")
            }
        }

        val destFile = File(downloadsDir, destFileName)
        destFile.bufferedWriter().use { writer ->
            // Write header at the top
            writer.write("timestamp\tvoltage\ttemp\tcurrent\tlevel%\tstatus\tcapacity\tcapacity %\tenergy\n")
            writer.write("--------------------------------------------------------------------------\n")

            // Then copy all the data from source file
            sourceFile.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    writer.write(line)
                    writer.newLine()
                }
            }
        }

        destFile.setReadable(true, false)

        MediaScannerConnection.scanFile(
            this,
            arrayOf(destFile.absolutePath),
            arrayOf("text/plain"),
            null
        )

        Toast.makeText(this, "File saved to Downloads/$destFileName", Toast.LENGTH_LONG).show()
        showDownloadCompleteNotification(destFileName, Uri.fromFile(destFile))
        Log.d(TAG, "File saved to legacy Downloads: ${destFile.absolutePath}")
    }

    private fun showDownloadCompleteNotification(fileName: String, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "download_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Battery log downloaded")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Download complete notification shown for: $fileName")
    }

    private fun verifyDownload(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "File exists in MediaStore: $fileName")
                } else {
                    Log.e(TAG, "File NOT FOUND in MediaStore: $fileName")
                }
            }
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (file.exists()) {
                Log.d(TAG, "File exists: ${file.absolutePath}")
            } else {
                Log.e(TAG, "File NOT FOUND: ${file.absolutePath}")
            }
        }
    }

    private fun clearLogFile(file: File) {
        try {
            // Instead of clearing, rename or archive if you want to keep history
            // For now, just truncate
            file.writeText("")
            Toast.makeText(this, "Log file cleared", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Log file cleared")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear log file", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Clear log error", e)
        }
    }

    override fun onDestroy() {

        releaseWakeLock()
        Log.d(TAG, "Service onDestroy() called")
        scheduledTask?.cancel(true)
        scheduler.shutdown()
        stopForeground(true)  // Remove notification when service stops
        Log.d(TAG, "Service stopped and notification removed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}