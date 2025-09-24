package com.example.batteryactivity

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max


class MainActivity : AppCompatActivity() {
    private lateinit var broadcastReceiver: BatteryBroadcastReceiver
    private lateinit var alarmHelper: AlarmHelper
//    private val batteryManager: BatteryManager by lazy {
//        getSystemService(BATTERY_SERVICE) as BatteryManager
//    }
    private lateinit var graph: GraphView
    private var alarminterval: Int = 5
    private var voltageSeries: LineGraphSeries<DataPoint>? = null
    private var currentSeries: LineGraphSeries<DataPoint>? = null
    private val baseTime = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private var time = -5.0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up About button click listener
        // 1. Initialize AlarmHelper
        alarmHelper = AlarmHelper(this)

        // 2. Set up the receiver
        alarmHelper.initialize(BatteryBroadcastReceiver::class.java)
        // Setup graph
        graph = findViewById(R.id.graph)
        graph.setBackgroundColor(Color.WHITE)
        // Voltage series (left Y-axis)
        voltageSeries = LineGraphSeries<DataPoint>().apply {
            color = Color.RED
            title = "Voltage (V)"
            thickness = 2
        }
        graph.addSeries(voltageSeries)

        // Current series (right Y-axis)
        currentSeries = LineGraphSeries<DataPoint>().apply {
            color = Color.BLUE
            title = "Current (A)"
            thickness = 2
        }
        with(graph.secondScale) {
//            graph.secondScale.verticalAxisTitle = "Current"
//            graph.secondScale.verticalAxisTitleTextSize = 12f
//            graph.secondScale.verticalAxisTitleColor = Color.BLACK
            // Ensure proper rendering
            addSeries(currentSeries)
            // Set bounds for right axis
            setMinY(-4.0)
            setMaxY(4.0) // Adjust based on expected current range
        }
        graph.getGridLabelRenderer().reloadStyles();

        graph.gridLabelRenderer.apply {
            // Add padding for labels
            padding = 20 // Adjust as needed (in pixels)
            graph.legendRenderer.isVisible = true
            graph.legendRenderer.setFixedPosition(0,420)
            graph.legendRenderer.textSize=14f;
            horizontalAxisTitle = "Time"
            horizontalAxisTitleColor = Color.BLACK
            horizontalLabelsColor = Color.BLACK
//            verticalAxisTitle = "Voltage"
//            verticalAxisTitleTextSize = 12f


            // Enable horizontal/vertical label scaling
            graph.gridLabelRenderer.isHorizontalLabelsVisible = true // Angle labels if needed
            numHorizontalLabels = 7 // Reduce number of labels if crowded

            // Customize text size
            textSize = 14f // In SP

            // Ensure labels don't get clipped
            setHumanRounding(false) // Disable automatic rounding

            // For Y-axis
            numVerticalLabels = 5
            graph.gridLabelRenderer.isVerticalLabelsVisible = true // For secondary Y-axis

            // For X-axis time labels
            labelsSpace = 10 // Extra space around labels
            verticalLabelsSecondScaleColor = Color.BLUE
            verticalLabelsColor = Color.RED

        }
        graph.gridLabelRenderer.labelFormatter = object : DefaultLabelFormatter() {
            override fun formatLabel(value: Double, isValueX: Boolean): String {
                if (isValueX) {
                    val hours = (value / 60).toInt()
                    val mins = (value % 60).toInt()
                    return String.format("%02d:%02d", hours, mins)
                }
                return super.formatLabel(value, isValueX)
            }
        }
        graph.viewport.apply {
            setMinX(0.0)
            setMaxX(60.0)
            isXAxisBoundsManual = true
            setYAxisBoundsManual(true)
            setMinY(1.0)
            setMaxY(5.0) // Example voltage range
        }
        graph.viewport.setMinX(0.0)
        graph.viewport.setMaxX(60.0)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        setupDownloadButton()
        val batteryLevelText = findViewById<TextView>(R.id.text_level_scale)
        val batteryTempText = findViewById<TextView>(R.id.text_temperature)
        val batteryVoltageText = findViewById<TextView>(R.id.text_voltage)
//        val batteryTimeText = findViewById<TextView>(R.id.text_timestamp)
        val batteryStatusText = findViewById<TextView>(R.id.text_property_status)
        val currentText = findViewById<TextView>(R.id.text_property_current_average)
        val capacityText = findViewById<TextView>(R.id.text_property_capacity)
        val batcapacityText = findViewById<TextView>(R.id.text_property_charge_counter)
        val batteryRemainingEnergy = findViewById<TextView>(R.id.text_property_energy_counter)
        broadcastReceiver = BatteryBroadcastReceiver { data ->
            runOnUiThread {
                // Update all UI elements
                findViewById<TextView>(R.id.text_timestamp).text =
                    SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
                        .format(Date())
                batteryStatusText.text = "${getStatusString(data.status)}"
                batteryLevelText.text = "${data.level}% (${data.level.toFloat() / data.scale * 100}%)"
                batteryTempText.text = "${data.temperature / 10f}°C"
                batteryVoltageText.text = "${data.voltage.toFloat() / 1000} V"
//                batteryTimeText.text = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(data.timestamp)}"
                capacityText.text = "${data.propertyCapacity}"
                batcapacityText.text = "${data.propertyChargeCounter.toFloat() / 1000000} Ah"
                currentText.text = "${data.propertyCurrentAverage.toFloat() / 1000} mA"
                batteryRemainingEnergy.text = "${data.propertyEnergyCounter/1000000000000} kWh"
            }
        }

//        val serviceIntent = Intent(
//            this,
//            BatteryMonitorService::class.java
//        )
//        ContextCompat.startForegroundService(this, serviceIntent)
        startBatteryMonitorService()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(broadcastReceiver, filter)
        scheduleAlarm()
    }
    private fun startBatteryMonitorService() {
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android 8.0+ we use startForegroundService
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            // For older versions use regular startService
            startService(serviceIntent)
        }
    }
    private fun updateAlarmInterval() {
        alarmHelper.setAlarm(alarminterval)
        Toast.makeText(this, "Alarm set to ${alarminterval} minute intervals",
            Toast.LENGTH_SHORT).show()
    }
    fun addDataPoint(voltage: Double, current: Double) {
        time += 5.0
//
        voltageSeries?.appendData(DataPoint(time.toDouble(), voltage.toDouble()), false, 15)
        currentSeries?.appendData(DataPoint(time.toDouble(), current.toDouble()), false, 15)

//         Auto-adjust viewport
        if (time > graph.viewport.getMaxX(false)+5) {
            graph.viewport.setMaxX(time)
            graph.viewport.apply {
                setMaxX(time)
                setMinX(max(time - 60.0, 0.0)) // Never go below 0
            }
        }

//        // Auto-scale right axis if needed
//        if (current > graph.secondScale.getMaxY(true)) {
//            graph.secondScale.setMaxY(current * 1.1)
//        }
    }
    private fun setupDownloadButton() {
        val downloadButton = findViewById<Button>(R.id.downloadButton)
        downloadButton.setOnClickListener {
            saveBatteryLogToDownloads()
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

    private fun getHealthString(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
    }

    private fun getPluggedString(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not plugged"
        }
    }
    private fun scheduleAlarm() {
        alarmHelper.setAlarm(alarminterval)
    }
//    override fun onPause() {
//        saveBatteryLogToDownloads()
//        super.onPause()
//    }
    override fun onDestroy() {
        alarmHelper.cancelAlarm()
        saveBatteryLogToDownloads()
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
    private fun saveBatteryLogToDownloads() {
        try {
            val fileName = "battery_log_data.txt"
            val sourceFile = File(filesDir, fileName)

            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                Toast.makeText(this, "No battery data collected yet", Toast.LENGTH_SHORT).show()
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
            Log.e("BatteryActivity", "Download error", e)
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
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Toast.makeText(this, "File saved to Downloads/$destFileName", Toast.LENGTH_LONG).show()
            showDownloadCompleteNotification(destFileName, uri)
        } catch (e: Exception) {
            throw Exception("Failed to save file: ${e.message}")
        }
    }
    private fun saveToDownloadsLegacy(destFileName: String, sourceFile: File) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            if (!downloadsDir.mkdirs()) {
                throw Exception("Couldn't create Downloads directory")
            }
        }

        val destFile = File(downloadsDir, destFileName)
        sourceFile.copyTo(destFile, overwrite = true)

        // Make file readable by other apps
        destFile.setReadable(true, false)

        // Scan file so it appears immediately in file managers
        MediaScannerConnection.scanFile(
            this,
            arrayOf(destFile.absolutePath),
            arrayOf("text/plain"),
            null
        )

        Toast.makeText(this, "File saved to Downloads/$destFileName", Toast.LENGTH_LONG).show()
        showDownloadCompleteNotification(destFileName, Uri.fromFile(destFile))
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
    }
    private fun clearLogFile(file: File) {
        try {
            val header = ""
            file.writeText(header)
            Toast.makeText(this, "Log file cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear log file", Toast.LENGTH_SHORT).show()
            Log.e("BatteryActivity", "Clear log error", e)
        }
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
                    Log.d("DownloadVerify", "File exists in MediaStore")
                } else {
                    Log.e("DownloadVerify", "File NOT FOUND in MediaStore")
                }
            }
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (file.exists()) {
                Log.d("DownloadVerify", "File exists: ${file.absolutePath}")
            } else {
                Log.e("DownloadVerify", "File NOT FOUND: ${file.absolutePath}")
            }
        }
    }

}