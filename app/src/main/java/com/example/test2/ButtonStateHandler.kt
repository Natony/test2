package com.example.test2

import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Class này đảm nhiệm việc xử lý hành vi nhấn giữ và nhả cho một nút manual.
 * Khi người dùng nhấn giữ (ACTION_DOWN) sẽ gửi command có giá trị 1,
 * còn khi nhả (ACTION_UP hoặc ACTION_CANCEL) thì gửi command có giá trị 0.
 */
object ButtonStateHandler {
    /**
     * Thiết lập nút theo dạng "nhấn – nhả" (momentary button):
     * - Khi nhấn giữ (ACTION_DOWN), gửi lệnh kích hoạt (ví dụ: 1).
     * - Khi nhả (ACTION_UP hoặc ACTION_CANCEL), gửi lệnh tắt (ví dụ: 0).
     *
     * @param button Nút ImageButton cần xử lý.
     * @param command Command ứng với nút (được định nghĩa trong enum, data class, …).
     * @param modbusManager Đối tượng quản lý giao tiếp Modbus.
     * @param updateButtonUI Hàm callback để cập nhật lại giao diện nút theo trạng thái (true: active, false: inactive).
     * @param showToast Hàm callback hiển thị thông báo lỗi/ thông điệp.
     * @param canExecuteCommand Hàm callback kiểm tra điều kiện cho phép thực hiện lệnh.
     */
    fun setupMomentaryButton(
        button: ImageButton,
        command: ModbusCommand,
        modbusManager: ModbusManager,
        updateButtonUI: (command: ModbusCommand, active: Boolean) -> Unit,
        showToast: (message: String) -> Unit,
        canExecuteCommand: () -> Boolean
    ) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!canExecuteCommand()) {
                        showToast("Đang xử lý lệnh khác, vui lòng chờ...")
                        return@setOnTouchListener true
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            modbusManager.writeCommand(command.address, 1)
                            updateButtonUI(command, true)
                        } catch (e: Exception) {
                            showToast("Lỗi: ${e.message}")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            modbusManager.writeCommand(command.address, 0)
                            updateButtonUI(command, false)
                        } catch (e: Exception) {
                            showToast("Lỗi: ${e.message}")
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Thiết lập nút theo dạng "nhấn giữ trạng thái" (toggle button):
     * Mỗi lần nhấn sẽ đảo trạng thái của nút từ active -> inactive hoặc ngược lại.
     *
     * @param button Nút ImageButton cần xử lý.
     * @param command Command ứng với nút.
     * @param modbusManager Đối tượng quản lý giao tiếp Modbus.
     * @param getCurrentState Hàm callback trả về trạng thái hiện tại của nút (true nếu đang active).
     * @param updateButtonUI Hàm callback cập nhật lại giao diện nút theo trạng thái (true/false).
     * @param showToast Hàm callback hiển thị thông báo lỗi.
     * @param canExecuteCommand Hàm callback kiểm tra điều kiện cho phép thực hiện lệnh.
     */
    fun setupToggleButton(
        button: ImageButton,
        command: ModbusCommand,
        modbusManager: ModbusManager,
        getCurrentState: () -> Boolean,
        updateButtonUI: (command: ModbusCommand, active: Boolean) -> Unit,
        showToast: (message: String) -> Unit,
        canExecuteCommand: () -> Boolean
    ) {
        button.setOnClickListener {
            if (!canExecuteCommand()) {
                showToast("Đang xử lý lệnh khác, vui lòng chờ...")
                return@setOnClickListener
            }
            val currentState = getCurrentState()
            val newState = !currentState  // Đảo trạng thái
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val commandValue = if (newState) 1 else 0
                    modbusManager.writeCommand(command.address, commandValue)
                    updateButtonUI(command, newState)
                } catch (e: Exception) {
                    showToast("Lỗi: ${e.message}")
                }
            }
        }
    }
}
