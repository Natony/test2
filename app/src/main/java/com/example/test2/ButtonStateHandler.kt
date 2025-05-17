package com.example.test2

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.MotionEvent
import android.widget.ImageButton
import com.example.test2.rules.LockRuleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse

/**
 * Handles both “momentary” (press-and-hold), “toggle” behaviors and dynamic cross-locking for ImageButton.
 * Usage:
 *   val handler = ButtonStateHandler(lifecycleScope)
 *   // rules auto-loaded from LockRuleConfig
 *   handler.setupToggleButton(...)
 *   handler.setupMomentaryButton(...)
 *   // apply locks in updateUI
 *   handler.applyCrossLocking(response, buttonLockStates)
 */
class ButtonStateHandler(private val scope: CoroutineScope) {

    // Data class định nghĩa một rule khóa
    data class LockRule(
        val trigger: ModbusCommand,
        val lockWhenValue: Int,
        val toLock: List<ModbusCommand>,
        val toUnlock: List<ModbusCommand> = emptyList()
    )

    // Danh sách rule động; tự động load từ LockRuleConfig
    private val dynamicRules: List<LockRule> = LockRuleConfig.getRules()

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

    fun updateQuantityButton(
        button: ImageButton,
        quantity: Int,
        activeResId: Int,
        inactiveResId: Int
    ) {
        button.setImageResource(if (quantity > 0) activeResId else inactiveResId)
    }

    /**
     * Áp dụng các rule khóa động (dynamicRules) dựa trên giá trị từ response
     * Gọi hàm này trong ControlFragment.updateUI
     */
    fun applyCrossLocking(
        response: ReadMultipleRegistersResponse,
        buttonLockStates: MutableMap<ModbusCommand, Boolean>
    ) {
        // 1) Emergency stop overrides tất cả
        val emVal = response.getRegisterValue(ModbusCommand.EMERGENCY_STOP.address - 1)
        if (emVal == 1) {
            buttonLockStates.keys.forEach { cmd ->
                buttonLockStates[cmd] = cmd != ModbusCommand.EMERGENCY_STOP
            }
            return
        }

        // Reset all unlock
        buttonLockStates.keys.forEach { buttonLockStates[it] = false }

        // Áp dụng từng rule
        val manualGroup = listOf(
            ModbusCommand.FORWARD,
            ModbusCommand.REVERSE,
            ModbusCommand.UP,
            ModbusCommand.DOWN
        )
        val manualActiveCount = manualGroup.count {
            response.getRegisterValue(it.address - 1) == 1
        }

        for (rule in dynamicRules) {
            // Với manual nhóm: chỉ lock nếu <=1 is active
            if (rule.trigger in manualGroup && manualActiveCount > 1) continue

            val curr = response.getRegisterValue(rule.trigger.address - 1)
            val shouldLock = if (rule.lockWhenValue < 0) curr != 0 else curr == rule.lockWhenValue

            if (shouldLock) {
                rule.toLock.forEach   { buttonLockStates[it] = true  }
                rule.toUnlock.forEach { buttonLockStates[it] = false }
            }
        }
    }
}
