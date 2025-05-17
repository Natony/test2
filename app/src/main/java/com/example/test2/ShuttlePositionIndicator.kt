package com.example.test2

import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.test2.ModbusCommand
import com.example.test2.ModbusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the shuttle position indicators by binding provided ImageViews
 * to their corresponding Modbus registers and updating icons on value changes.
 * Mirrors OperationStatusManager's API.
 */
class ShuttlePositionIndicator(
    private val modbusManager: ModbusManager,
    private val lifecycleOwner: LifecycleOwner,
    private val icons: List<ImageView>,
    private val onRes: List<Int>,
    private val offRes: List<Int>
) {
    init {
        require(icons.size == onRes.size && icons.size == offRes.size) {
            "icons, onRes, offRes lists must have the same size"
        }
    }

    private val commands = listOf(
        ModbusCommand.POS1, ModbusCommand.POS2, ModbusCommand.POS3,
        ModbusCommand.POS4, ModbusCommand.POS5, ModbusCommand.POS6,
        ModbusCommand.POS7, ModbusCommand.POS8, ModbusCommand.POS9,
        ModbusCommand.POS10, ModbusCommand.POS11, ModbusCommand.POS12,
        ModbusCommand.POS13
    )

    /**
     * Start monitoring registers and updating ImageViews. Call after icons are initialized.
     */
    fun startMonitoring() {
        commands.forEachIndexed { index, cmd ->
            modbusManager.registerPollingCallback(cmd.address) { value ->
                lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    val img = icons[index]
                    img.setImageResource(if (value == 1) onRes[index] else offRes[index])
                }
            }
        }
    }

    /**
     * Stop monitoring and unregister callbacks. Call in onDestroyView or similar.
     */
    fun stopMonitoring() {
        commands.forEach { cmd ->
            modbusManager.unregisterPollingCallback(cmd.address)
        }
    }
}
