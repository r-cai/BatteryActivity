package com.example.batteryactivity
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()  // Close current activity
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}