    package com.example.batteryactivity
    
    import android.annotation.SuppressLint
    import android.app.ActivityManager
    import android.content.Context
    import android.content.Intent
    import androidx.core.app.ActivityCompat
    import android.content.IntentFilter
    import androidx.work.ExistingPeriodicWorkPolicy
    import androidx.work.PeriodicWorkRequestBuilder
    import androidx.work.WorkManager
    import java.util.concurrent.TimeUnit
    import android.content.pm.PackageManager
    import android.graphics.Color
    import android.os.BatteryManager
    import android.os.Build
    import android.os.Bundle
    import android.os.Handler
    import android.os.Looper
    import android.util.Log
    import android.widget.Button
    import android.widget.TextView
    import android.widget.Toast
    import androidx.activity.enableEdgeToEdge
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.content.ContextCompat
    import androidx.core.view.ViewCompat
    import androidx.core.view.WindowInsetsCompat
    import com.jjoe64.graphview.DefaultLabelFormatter
    import com.jjoe64.graphview.GraphView
    import com.jjoe64.graphview.series.DataPoint
    import com.jjoe64.graphview.series.LineGraphSeries
    import java.text.SimpleDateFormat
    import java.util.Date
    import java.util.Locale
    import kotlin.math.max
    
    
    class MainActivity : AppCompatActivity() {
        private lateinit var broadcastReceiver: BatteryBroadcastReceiver
        private lateinit var graph: GraphView
        private var voltageSeries: LineGraphSeries<DataPoint>? = null
        private var currentSeries: LineGraphSeries<DataPoint>? = null
        private var time = -5.0
    
        // Service data receiver
        private val serviceDataReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BatteryMonitorService.ACTION_BATTERY_DATA) {
                    updateFromService(intent)
                }
            }
        }
    
        @SuppressLint("UnspecifiedRegisterReceiverFlag", "SuspiciousIndentation")
        override fun onCreate(savedInstanceState: Bundle?) {
            try{
                super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContentView(R.layout.activity_main)
                checkNotificationPermission()
            //TextView references
            val batteryLevelText = findViewById<TextView>(R.id.text_level_scale)
            val batteryTempText = findViewById<TextView>(R.id.text_temperature)
            val batteryVoltageText = findViewById<TextView>(R.id.text_voltage)
            val batteryStatusText = findViewById<TextView>(R.id.text_property_status)
            val currentText = findViewById<TextView>(R.id.text_property_current_average)
            val capacityText = findViewById<TextView>(R.id.text_property_capacity)
            val batcapacityText = findViewById<TextView>(R.id.text_property_charge_counter)
            val batteryRemainingEnergy = findViewById<TextView>(R.id.text_property_energy_counter)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ requires export flag
                    registerReceiver(
                        serviceDataReceiver,
                        IntentFilter(BatteryMonitorService.ACTION_BATTERY_DATA),
                        Context.RECEIVER_NOT_EXPORTED  // Since it's for internal app use only
                    )
                } else {
                    // For older Android versions
                    registerReceiver(
                        serviceDataReceiver,
                        IntentFilter(BatteryMonitorService.ACTION_BATTERY_DATA)
                    )
                }
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
                addSeries(currentSeries)
                // Set bounds for right axis
                setMinY(-4.0)
                setMaxY(4.0)
            }
            graph.gridLabelRenderer.reloadStyles()

                graph.gridLabelRenderer.apply {
                // Add padding for labels
                padding = 20 // Adjust as needed (in pixels)
                graph.legendRenderer.isVisible = true
                graph.legendRenderer.setFixedPosition(0,420)
                graph.legendRenderer.textSize=14f
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

                setHumanRounding(false)
    
                // For Y-axis
                numVerticalLabels = 5
                graph.gridLabelRenderer.isVerticalLabelsVisible = true
    
                // For X-axis time labels
                labelsSpace = 10
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
                setMaxY(5.0)
            }
            graph.viewport.setMinX(0.0)
            graph.viewport.setMaxX(60.0)
    
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
    
            //button setup
            findViewById<Button>(R.id.aboutButton).setOnClickListener {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            setupDownloadButton()
    
            broadcastReceiver = BatteryBroadcastReceiver { data ->
                runOnUiThread {
                    findViewById<TextView>(R.id.text_timestamp).text =
                        SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
                            .format(Date())
                    batteryStatusText.text = "${getStatusString(data.status)}"
                    batteryLevelText.text = "${data.level}% (${data.level.toFloat() / data.scale * 100}%)"
                    batteryTempText.text = "${data.temperature / 10f}°C"
                    batteryVoltageText.text = "${data.voltage.toFloat() / 1000} V"
                    capacityText.text = "${data.propertyCapacity}"
                    batcapacityText.text = "${data.propertyChargeCounter.toFloat() / 1000000} Ah"
                    currentText.text = "${data.propertyCurrentAverage.toFloat() / 1000} mA"
                    batteryRemainingEnergy.text = "${data.propertyEnergyCounter/1000000000000} kWh"
                }
            }
    
            startForegroundService()
                scheduleServiceRestart()
                Handler(Looper.getMainLooper()).postDelayed({
                    val isRunning = isServiceRunning()
                    Log.d("MainActivity", "Service running check: $isRunning")
                    Toast.makeText(this,
                        if (isRunning) "✅ Service is running" else "❌ Service not running",
                        Toast.LENGTH_SHORT
                    ).show()
                }, 2000)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // ACTION_BATTERY_CHANGED is a system broadcast, use RECEIVER_EXPORTED
            registerReceiver(
                broadcastReceiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(broadcastReceiver, filter)
        }
        } catch (e: Exception) {
            Log.e("MainActivity", "Crash on create: ${e.message}", e)
            Toast.makeText(this, "App crashed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            finish()
        }
        }
        private fun checkNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        100
                    )
                    Log.d("MainActivity", "Notification permission requested")
                } else {
                    Log.d("MainActivity", "Notification permission already granted")
                }
            }
        }
        private fun startForegroundService() {
            try {
                val serviceIntent = Intent(this, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_START
                }
    
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Init error: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(this, "Init failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
        private fun scheduleServiceRestart() {
            try {
                // Create a periodic work request to check service every 15 minutes
                val serviceCheckWork = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
                    15, TimeUnit.MINUTES  // Minimum interval is 15 minutes
                )
                    .addTag("service_monitor")
                    .build()

                // Enqueue unique work (replace if already exists)
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "battery_service_monitor",
                    ExistingPeriodicWorkPolicy.KEEP,  // Keep existing work if already scheduled
                    serviceCheckWork
                )

                Log.d("MainActivity", "Service restart monitoring scheduled (every 15 min)")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to schedule service restart", e)
            }
        }
        private fun updateFromService(intent: Intent) {
            val voltage = intent.getIntExtra(BatteryMonitorService.EXTRA_VOLTAGE, 0)
            val current = intent.getIntExtra(BatteryMonitorService.EXTRA_CURRENT, 0)
    
            // Update graph with service data
            runOnUiThread {
                addDataPoint(voltage.toDouble() / 1000, current.toDouble() / 1000)
            }
        }
    
        fun addDataPoint(voltage: Double, current: Double) {
            time += 5.0
            voltageSeries?.appendData(DataPoint(time, voltage), false, 15)
            currentSeries?.appendData(DataPoint(time, current), false, 15)
    
            if (time > graph.viewport.getMaxX(false) + 5) {
                graph.viewport.apply {
                    setMaxX(time)
                    setMinX(max(time - 60.0, 0.0))
                }
            }
        }

        private fun isServiceRunning(): Boolean {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (BatteryMonitorService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
        private fun setupDownloadButton() {
            val downloadButton = findViewById<Button>(R.id.downloadButton)
            downloadButton.setOnClickListener {
                val saveIntent = Intent(this, BatteryMonitorService::class.java).apply {
                    action = BatteryMonitorService.ACTION_SAVE_LOG
                }
                startService(saveIntent)
                Toast.makeText(this, "Saving log file...", Toast.LENGTH_SHORT).show()
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
    
    
        override fun onDestroy() {
            // stop service when activity is destroyed
            // val stopIntent = Intent(this, BatteryMonitorService::class.java).apply {
            //     action = BatteryMonitorService.ACTION_STOP
            // }
            // stopService(stopIntent)

            if (::broadcastReceiver.isInitialized) {
                try {
                    unregisterReceiver(broadcastReceiver)
                    Log.d("MainActivity", "broadcastReceiver unregistered")
                } catch (e: IllegalArgumentException) {
                    Log.d("MainActivity", "broadcastReceiver not registered")
                }
            }
            // check if serviceDataReceiver is registered
            try {
                unregisterReceiver(serviceDataReceiver)
            } catch (e: IllegalArgumentException) {
                Log.d("MainActivity", "serviceDataReceiver not registered")
            }
            try {
                unregisterReceiver(serviceDataReceiver)
                Log.d("MainActivity", "serviceDataReceiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.d("MainActivity", "serviceDataReceiver not registered")
            }
            super.onDestroy()
    }
    }