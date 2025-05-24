package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.test2.ui.home.HomeFragment
import com.example.test2.ui.alarm.AlarmFragment
import com.example.test2.ui.control.ControlFragment
import com.example.test2.ui.history.HistoryFragment
import com.example.test2.ui.config.ConfigFragment
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.example.test2.ui.modbus.ModbusViewModel
import com.example.test2.ui.modbus.ModbusViewModelFactory
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

class MainActivity : AppCompatActivity() {
    private lateinit var btnHome: ImageButton
    private lateinit var btnAlarm: ImageButton
    private lateinit var btnConfig: ImageButton
    private lateinit var btnControl: ImageButton
    private lateinit var btnHistory: ImageButton

    private lateinit var icBattery: ImageView
    private lateinit var tvBattery: TextView
    private lateinit var tvOperationStatus: TextView

    lateinit var modbusViewModel: ModbusViewModel

    companion object {
        private const val INACTIVITY_TIMEOUT = 60_000L  // 1 phút
    }

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        navigateToHomeFragment()
    }

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

        icBattery = findViewById(R.id.icBattery)
        tvBattery = findViewById(R.id.tvBattery)

        tvOperationStatus = findViewById(R.id.tvOperationStatus)

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

        // Khởi tạo ModbusViewModel và observe dữ liệu pin
        modbusViewModel = ViewModelProvider(this,
            ModbusViewModelFactory(applicationContext)
        ).get(ModbusViewModel::class.java)

        modbusViewModel.batteryLevel.observe(this, Observer { level ->
            updateBatteryUI(level)
        })

        modbusViewModel.operationStatus.observe(this, Observer { status ->
            tvOperationStatus.text = status.displayText
            tvOperationStatus.setTextColor(status.textColor)
            status.iconResId?.let {
                tvOperationStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(it, 0, 0, 0)
            }
        })

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

    private fun updateBatteryUI(level: Int?) {
        level?.let {
            val batteryIcon = when {
                it > 80 -> R.drawable.ic_battery_full
                it > 20 -> R.drawable.ic_battery_medium
                else -> R.drawable.ic_battery_low
            }
            icBattery.setImageResource(batteryIcon)
            tvBattery.text = "$it%"
        }
    }

    override fun onResume() {
        super.onResume()
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        stopInactivityTimer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Mỗi lần người dùng chạm vào màn hình, reset lại timer
        if (ev.action == MotionEvent.ACTION_DOWN ||
            ev.action == MotionEvent.ACTION_MOVE ||
            ev.action == MotionEvent.ACTION_UP) {
            resetInactivityTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
    }

    private fun stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun navigateToHomeFragment() {
        // Nếu đang không phải HomeFragment, thì chuyển về
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current !is HomeFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commitAllowingStateLoss()
        }
    }
}