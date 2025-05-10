package com.example.test2

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.MotionEvent
import android.widget.ImageButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse

/**
 * Handles both “momentary” (press-and-hold), “toggle” behaviors and dynamic cross-locking for ImageButton.
 *
 * @param scope CoroutineScope to launch IO/Main work (e.g. viewLifecycleOwner.lifecycleScope)
 */
class ButtonStateHandler(private val scope: CoroutineScope) {

    // Data class định nghĩa một rule khóa
    data class LockRule(
        val trigger: ModbusCommand,
        val lockWhenValue: Int,
        val toLock: List<ModbusCommand>,
        val toUnlock: List<ModbusCommand> = emptyList()
    )

    // Danh sách rule động; có thể bổ sung thêm từ bên ngoài
    val dynamicRules: MutableList<LockRule> = mutableListOf()

    /**
     * Momentary button: ACTION_DOWN → send “1”, ACTION_UP/CANCEL → send “0”.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setupMomentaryButton(
        button: ImageButton,
        command: ModbusCommand,
        modbusManager: ModbusManager,
        updateButtonUI: (ModbusCommand, Boolean) -> Unit,
        showToast: (String) -> Unit,
        canExecuteCommand: () -> Boolean
    ) {
        var isDownProcessed = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isDownProcessed && canExecuteCommand()) {
                        isDownProcessed = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                modbusManager.writeCommand(command.address, 1)
                                withContext(Dispatchers.Main) {
                                    updateButtonUI(command, true)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    showToast("Error: ${e.message}")
                                    updateButtonUI(command, false)
                                }
                            }
                        }
                    } else if (!canExecuteCommand()) {
                        showToast("System busy, please wait…")
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            modbusManager.writeCommand(command.address, 0)
                            withContext(Dispatchers.Main) {
                                updateButtonUI(command, false)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showToast("Reset error: ${e.message}")
                            }
                        } finally {
                            isDownProcessed = false
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Toggle button with confirmation: each click shows dialog, then flips state.
     */
    fun setupToggleButton(
        button: ImageButton,
        command: ModbusCommand,
        modbusManager: ModbusManager,
        getCurrentState: () -> Boolean,
        updateButtonUI: (ModbusCommand, Boolean) -> Unit,
        showToast: (String) -> Unit,
        canExecuteCommand: () -> Boolean,
        context: Context,
        confirmMessage: String = "Bạn có chắc muốn thực hiện hành động này?"
    ) {
        var isProcessing = false

        button.setOnClickListener {
            if (isProcessing || !canExecuteCommand()) {
                if (!canExecuteCommand()) showToast("System busy, please wait…")
                return@setOnClickListener
            }

            AlertDialog.Builder(context)
                .setMessage(confirmMessage)
                .setPositiveButton("Đồng ý") { dialog, _ ->
                    dialog.dismiss()
                    isProcessing = true
                    val newState = !getCurrentState()
                    scope.launch(Dispatchers.IO) {
                        try {
                            modbusManager.writeCommand(command.address, if (newState) 1 else 0)
                            withContext(Dispatchers.Main) {
                                updateButtonUI(command, newState)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showToast("Error: ${e.message}")
                                updateButtonUI(command, !newState)
                            }
                        } finally {
                            isProcessing = false
                        }
                    }
                }
                .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    /**
     * Áp dụng các rule khóa động (dynamicRules) dựa trên giá trị từ response
     */
    private fun applyDynamicLocking(
        response: ReadMultipleRegistersResponse,
        buttonLockStates: MutableMap<ModbusCommand, Boolean>
    ) {
        val manualCmds = listOf(
            ModbusCommand.FORWARD,
            ModbusCommand.REVERSE,
            ModbusCommand.UP,
            ModbusCommand.DOWN
        )
        val manualActive = manualCmds.count {
            response.getRegisterValue(it.address - 1) == 1
        }

        for (rule in dynamicRules) {
            if (rule.trigger in manualCmds && manualActive > 1) continue

            val curr = response.getRegisterValue(rule.trigger.address - 1)
            if (curr == rule.lockWhenValue) {
                rule.toLock.forEach   { buttonLockStates[it] = true  }
                rule.toUnlock.forEach { buttonLockStates[it] = false }
            }
        }
    }

    /**
     * Hàm tổng hợp để gọi từ ControlFragment.updateUI():
     * 1) ưu tiên Emergency → khóa hết trừ EMERGENCY_STOP
     * 2) áp động các rule
     */
    fun applyCrossLocking(
        response: ReadMultipleRegistersResponse,
        buttonLockStates: MutableMap<ModbusCommand, Boolean>
    ) {
        val emVal = response.getRegisterValue(ModbusCommand.EMERGENCY_STOP.address - 1)
        if (emVal == 1) {
            buttonLockStates.keys.forEach { cmd ->
                buttonLockStates[cmd] = cmd != ModbusCommand.EMERGENCY_STOP
            }
            return
        }

        buttonLockStates.keys.forEach { buttonLockStates[it] = false }
        applyDynamicLocking(response, buttonLockStates)
    }
}
