package com.example.test2

import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorInt
import android.graphics.Color
import com.example.test2.ModbusManager.ModbusResult
import com.example.test2.ModbusManager.ModbusResult.Success

class OperationStatusManager(
    private val modbusManager: ModbusManager,
    private val statusMap: Map<Int, OperationStatus>,
    private val defaultStatus: OperationStatus = OperationStatus(
        -1,
        "Trạng thái không xác định",
        null,
        Color.GRAY
    )
) {
    private var currentStatus: OperationStatus? = null
    private var textView: TextView? = null
    private val pollingAddress = ModbusCommand.OPERATION_STATUS.address
    private val TAG = "OperationStatusManager"

    fun bindTextView(view: TextView) {
        this.textView = view
        Log.d(TAG, "TextView bound: $view")
        updateDisplay()
    }

    fun startMonitoring() {
        Log.d(TAG, "Starting monitoring at address: $pollingAddress")
        modbusManager.registerPollingCallback(pollingAddress) { value ->
            Log.d(TAG, "Received status update from callback: $value")
            handleStatusUpdate(value)
        }
    }

    private fun handleStatusUpdate(value: Int) {
        val newStatus = statusMap[value] ?: defaultStatus
        Log.d(TAG, "Status update: value=$value, mapped to status=${newStatus.displayText}")

        if (newStatus.statusCode != currentStatus?.statusCode) {
            Log.d(TAG, "Status changed from ${currentStatus?.statusCode} to ${newStatus.statusCode}")
            currentStatus = newStatus
            updateDisplay()
        } else {
            Log.d(TAG, "Status unchanged")
        }
    }

    private fun updateDisplay() {
        Log.d(TAG, "Updating display with status: ${currentStatus?.displayText}")
        textView?.post {
            currentStatus?.let { status ->
                textView?.text = status.displayText
                status.iconResId?.let {
                    textView?.setCompoundDrawablesRelativeWithIntrinsicBounds(it, 0, 0, 0)
                }
                // The key change here - directly use the color value
                textView?.setTextColor(status.textColor)
                Log.d(TAG, "Display updated: text=${status.displayText}, color=${status.textColor}")
            }
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring")
        modbusManager.unregisterPollingCallback(pollingAddress)
    }
}