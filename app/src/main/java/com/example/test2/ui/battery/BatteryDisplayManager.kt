package com.example.test2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Class quản lý và hiển thị thông tin pin trên giao diện
 */
class BatteryDisplayManager(
    private val context: Context,
    private val iconView: ImageView,
    private val textView: TextView
) : DefaultLifecycleObserver {

    // Icons cho các mức pin
    private val iconLow = R.drawable.ic_battery_low      // Dưới 20%
    private val iconMedium = R.drawable.ic_battery_medium // 20-60%
    private val iconFull = R.drawable.ic_battery_full     // Trên 60%

    private var isRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryUI(intent)
            }
        }
    }

    /**
     * Cập nhật UI hiển thị pin dựa trên thông tin từ hệ thống
     */
    private fun updateBatteryUI(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val batteryPercent = if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            0
        }

        // Cập nhật TextView
        textView.text = "$batteryPercent%"

        // Cập nhật icon theo mức pin
        val iconRes = when {
            batteryPercent < 20 -> iconLow
            batteryPercent < 60 -> iconMedium
            else -> iconFull
        }
        iconView.setImageResource(iconRes)

        // Log để debug
        Log.d("BatteryManager", "Battery level: $batteryPercent%, using icon: $iconRes")

        // Nếu pin dưới 15%, có thể thêm cảnh báo
        if (batteryPercent < 15) {
            textView.setTextColor(context.getColor(android.R.color.holo_red_light))
        } else {
            textView.setTextColor(context.getColor(android.R.color.white))
        }
    }

    /**
     * Đăng ký lắng nghe sự kiện pin
     */
    fun register() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            isRegistered = true

            // Kiểm tra pin hiện tại
            val batteryStatus = context.registerReceiver(null, filter)
            batteryStatus?.let { updateBatteryUI(it) }

            Log.d("BatteryManager", "Battery receiver registered")
        }
    }

    /**
     * Hủy đăng ký lắng nghe sự kiện pin
     */
    fun unregister() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
                isRegistered = false
                Log.d("BatteryManager", "Battery receiver unregistered")
            } catch (e: Exception) {
                Log.e("BatteryManager", "Error unregistering receiver: ${e.message}")
            }
        }
    }

    // Lifecycle methods
    override fun onResume(owner: LifecycleOwner) {
        register()
    }

    override fun onPause(owner: LifecycleOwner) {
        unregister()
    }
}