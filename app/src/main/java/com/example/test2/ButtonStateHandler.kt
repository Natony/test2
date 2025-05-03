package com.example.test2

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.widget.ImageButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles both “momentary” (press-and-hold) and “toggle” behaviors for ImageButton.
 *
 * @param scope       CoroutineScope to launch IO/Main work (e.g. viewLifecycleOwner.lifecycleScope)
 */
class ButtonStateHandler(private val scope: CoroutineScope) {

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
     * Toggle button: each click flips between active/inactive.
     */
    fun setupToggleButton(
        button: ImageButton,
        command: ModbusCommand,
        modbusManager: ModbusManager,
        getCurrentState: () -> Boolean,
        updateButtonUI: (ModbusCommand, Boolean) -> Unit,
        showToast: (String) -> Unit,
        canExecuteCommand: () -> Boolean
    ) {
        var isProcessing = false

        button.setOnClickListener {
            if (isProcessing || !canExecuteCommand()) {
                if (!canExecuteCommand()) showToast("System busy, please wait…")
                return@setOnClickListener
            }
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
                        // rollback UI
                        updateButtonUI(command, !newState)
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }
}
