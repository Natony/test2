package com.example.test2

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the shuttle function UI and logic for coordinate input, function selection,
 * and coordinate monitoring.
 */
class ShuttleFunctionHandler(
    private val fragment: Fragment,
    private var modbusManager: ModbusManager?,
    private val canExecuteCommandCheck: () -> Boolean
) {
    // UI Components
    private lateinit var spinnerFunctions: Spinner
    private lateinit var etStartX: EditText
    private lateinit var etStartY: EditText
    private lateinit var etStartZ: EditText
    private lateinit var etEndX: EditText
    private lateinit var etEndY: EditText
    private lateinit var etEndZ: EditText
    private lateinit var tvActualX: TextView
    private lateinit var tvActualY: TextView
    private lateinit var tvActualZ: TextView
    private lateinit var btnRunFunction: Button

    private var pollingJob: Job? = null
    private val context: Context get() = fragment.requireContext()
    private val lifecycleOwner: LifecycleOwner get() = fragment.viewLifecycleOwner

    /**
     * Binds the UI components and sets up listeners
     */
    fun initialize(
        spinnerFunctions: Spinner,
        etStartX: EditText,
        etStartY: EditText,
        etStartZ: EditText,
        etEndX: EditText,
        etEndY: EditText,
        etEndZ: EditText,
        tvActualX: TextView,
        tvActualY: TextView,
        tvActualZ: TextView,
        btnRunFunction: Button
    ) {
        this.spinnerFunctions = spinnerFunctions
        this.etStartX = etStartX
        this.etStartY = etStartY
        this.etStartZ = etStartZ
        this.etEndX = etEndX
        this.etEndY = etEndY
        this.etEndZ = etEndZ
        this.tvActualX = tvActualX
        this.tvActualY = tvActualY
        this.tvActualZ = tvActualZ
        this.btnRunFunction = btnRunFunction

        setupSpinner()
        setupRunButton()
        startCoordinatePolling()
    }

    /**
     * Sets up the spinner selection listener
     */
    fun setupSpinner() {
        spinnerFunctions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val isCoordinateMode = position == 0 || position == 1 // "Chạy không tải" hoặc "Di chuyển pallet"
                etStartX.isEnabled = isCoordinateMode
                etStartY.isEnabled = isCoordinateMode
                etStartZ.isEnabled = isCoordinateMode
                etEndX.isEnabled = isCoordinateMode
                etEndY.isEnabled = isCoordinateMode
                etEndZ.isEnabled = isCoordinateMode
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Không làm gì
            }
        }
    }

    /**
     * Sets up the run function button click listener
     */
    fun setupRunButton() {
        btnRunFunction.setOnClickListener {
            if (!canExecuteCommandCheck()) {
                showToast("Đang xử lý lệnh khác…")
                return@setOnClickListener
            }

            val selectedPosition = spinnerFunctions.selectedItemPosition
            if (selectedPosition == 0 || selectedPosition == 1) { // "Chạy không tải" hoặc "Di chuyển pallet"
                if (!validateCoordinates()) {
                    showToast("Vui lòng nhập đầy đủ và đúng định dạng cho các tọa độ.")
                    return@setOnClickListener
                }
            }

            fragment.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    when (selectedPosition) {
                        0, 1 -> { // "Chạy không tải" hoặc "Di chuyển pallet"
                            val startX = etStartX.text.toString().toInt()
                            val startY = etStartY.text.toString().toInt()
                            val startZ = etStartZ.text.toString().toInt()
                            val endX = etEndX.text.toString().toInt()
                            val endY = etEndY.text.toString().toInt()
                            val endZ = etEndZ.text.toString().toInt()

                            modbusManager?.writeCommand(ModbusCommand.START_X.address, startX)
                            modbusManager?.writeCommand(ModbusCommand.START_Y.address, startY)
                            modbusManager?.writeCommand(ModbusCommand.START_Z.address, startZ)
                            modbusManager?.writeCommand(ModbusCommand.END_X.address, endX)
                            modbusManager?.writeCommand(ModbusCommand.END_Y.address, endY)
                            modbusManager?.writeCommand(ModbusCommand.END_Z.address, endZ)
                        }
                        2 -> { // "Sạc shuttle"
                            // Không cần gửi tọa độ
                        }
                    }

                    val functionNumber = when (selectedPosition) {
                        0 -> 2 // "Chạy không tải"
                        1 -> 3 // "Di chuyển pallet"
                        2 -> 1 // "Sạc shuttle"
                        else -> 0
                    }
                    modbusManager?.writeCommand(ModbusCommand.FUNCTION_MODE.address, functionNumber)

                    withContext(Dispatchers.Main) {
                        showToast("Đã gửi lệnh thành công")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Lỗi: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Validates that all coordinate inputs are present and valid
     */
    fun validateCoordinates(): Boolean {
        val startX = etStartX.text.toString().trim()
        val startY = etStartY.text.toString().trim()
        val startZ = etStartZ.text.toString().trim()
        val endX = etEndX.text.toString().trim()
        val endY = etEndY.text.toString().trim()
        val endZ = etEndZ.text.toString().trim()

        if (startX.isEmpty() || startY.isEmpty() || startZ.isEmpty() ||
            endX.isEmpty() || endY.isEmpty() || endZ.isEmpty()) {
            return false
        }

        try {
            startX.toInt()
            startY.toInt()
            startZ.toInt()
            endX.toInt()
            endY.toInt()
            endZ.toInt()
        } catch (e: NumberFormatException) {
            return false
        }

        return true
    }

    /**
     * Starts polling for actual coordinate values
     */
    fun startCoordinatePolling() {
        pollingJob?.cancel()
        pollingJob = fragment.lifecycleScope.launch {
            while (isActive) {
                try {
                    val actualX = modbusManager?.readRegister(ModbusCommand.ACTUAL_X.address)
                    val actualY = modbusManager?.readRegister(ModbusCommand.ACTUAL_Y.address)
                    val actualZ = modbusManager?.readRegister(ModbusCommand.ACTUAL_Z.address)
                    withContext(Dispatchers.Main) {
                        tvActualX.text = "X: ${actualX ?: "N/A"}"
                        tvActualY.text = "Y: ${actualY ?: "N/A"}"
                        tvActualZ.text = "Z: ${actualZ ?: "N/A"}"
                    }
                } catch (e: Exception) {
                    // Log error
                }
                delay(1000) // Cập nhật mỗi giây
            }
        }
    }

    /**
     * Stops coordinate polling
     */
    fun stopCoordinatePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setModbusManager(manager: ModbusManager?) {
        this.modbusManager = manager
    }
}