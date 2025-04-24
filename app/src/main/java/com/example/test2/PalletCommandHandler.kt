package com.example.test2

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Class xử lý lệnh pallet: hiển thị dialog nhập số lượng và gửi lệnh đến PLC.
 */
class PalletCommandHandler(
    private val context: Context,
    private val modbusManager: ModbusManager,
    private val canExecuteCommand: () -> Boolean
) {

    /**
     * Thiết lập hành vi cho nút Pick Pallets hoặc Take Pallets.
     * Khi nhấn nút, hiển thị dialog để nhập số lượng pallet, sau đó gửi lệnh đến PLC.
     *
     * @param button Nút cần thiết lập (ví dụ: btnPickPallets hoặc btnTakePallets).
     * @param command Lệnh Modbus tương ứng (PICK_PALLETS hoặc TAKE_PALLETS).
     */
    fun setupPalletButton(button: ImageButton, command: ModbusCommand) {
        button.setOnClickListener {
            if (!canExecuteCommand()) {
                showToast("Hệ thống đang bận")
                return@setOnClickListener
            }
            showQuantityInputDialog { quantity ->
                sendPalletCommand(command, quantity)
            }
        }
    }

    private fun showQuantityInputDialog(onQuantityEntered: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_quantity_input, null)
        val etQuantity = dialogView.findViewById<EditText>(R.id.etQuantity)

        AlertDialog.Builder(context)
            .setTitle("Nhập số lượng pallet")
            .setView(dialogView)
            .setPositiveButton("Xác nhận") { _, _ ->
                val quantityStr = etQuantity.text.toString()
                val quantity = quantityStr.toIntOrNull()
                if (quantity != null && quantity > 0) {
                    onQuantityEntered(quantity)
                } else {
                    showToast("Vui lòng nhập số lượng hợp lệ")
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun sendPalletCommand(command: ModbusCommand, quantity: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val quantityAddress = when (command) {
                    ModbusCommand.PICK_PALLETS -> 19 // Địa chỉ giả định
                    ModbusCommand.TAKE_PALLETS -> 20 // Địa chỉ giả định
                    else -> throw IllegalArgumentException("Command không hợp lệ")
                }
                // Gửi lệnh kích hoạt
                modbusManager.writeCommand(command.address, 1)
                // Gửi số lượng pallet
                modbusManager.writeCommand(quantityAddress, quantity)
                showToast("Đã gửi lệnh thành công: $quantity pallet")
            } catch (e: Exception) {
                showToast("Lỗi: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}