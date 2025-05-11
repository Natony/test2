package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.test2.ui.home.HomeFragment
import com.example.test2.ui.alarm.AlarmFragment
import com.example.test2.ui.control.ControlFragment
import com.example.test2.ui.history.HistoryFragment
import com.example.test2.ui.config.ConfigFragment


class MainActivity : AppCompatActivity() {
    private lateinit var btnHome: ImageButton
    private lateinit var btnAlarm: ImageButton
    private lateinit var btnConfig: ImageButton
    private lateinit var btnControl: ImageButton
    private lateinit var btnHistory: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        // 1. Bind navigation buttons
        btnHome    = findViewById(R.id.btnHome)
        btnAlarm   = findViewById(R.id.btnAlarm)
        btnConfig  = findViewById(R.id.btnConfig)
        btnControl = findViewById(R.id.btnControl)
        btnHistory = findViewById(R.id.btnHistory)

        // 2. Determine initial fragment based on intent extra
        val start = intent.getStringExtra("startPage")
        val initialFragment: Fragment = when (start) {
            "HOME"    -> HomeFragment()
            "ALARM"   -> AlarmFragment()
            "CONFIG"  -> ConfigFragment()
            "CONTROL" -> ControlFragment()
            "HISTORY" -> HistoryFragment()
            else       -> ControlFragment()
        }
        openFragment(initialFragment)

        // 3. Set up listeners
        btnHome   .setOnClickListener { openFragment(HomeFragment()) }
        btnAlarm  .setOnClickListener { openFragment(AlarmFragment()) }
        btnConfig .setOnClickListener { openFragment(ConfigFragment()) }
        btnControl.setOnClickListener { openFragment(ControlFragment()) }
        btnHistory.setOnClickListener { openFragment(HistoryFragment()) }

    }

    fun openFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

}