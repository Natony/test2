package com.example.test2

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import net.wimpi.modbus.io.ModbusTCPTransaction
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import net.wimpi.modbus.msg.WriteSingleRegisterRequest
import net.wimpi.modbus.net.TCPMasterConnection
import java.net.InetAddress
import net.wimpi.modbus.procimg.SimpleRegister

// Lớp chứa tham chiếu đến các view của mỗi hàng register
data class RegisterRow(
    val labelTextView: TextView,         // Hiển thị "R0 ="
    val currentValueTextView: TextView,    // Hiển thị giá trị đang đọc
    val editValueEditText: EditText,       // Ô nhập giá trị mới
    val confirmButton: Button              // Nút xác nhận ghi giá trị mới
)

class MainActivity : ComponentActivity() {

    private lateinit var etIp: EditText
    private lateinit var btnStartStop: Button
    private lateinit var tvResult: TextView
    private lateinit var registerValuesLayout: LinearLayout

    private var isRunning = false
    private var connection: TCPMasterConnection? = null
    private var registerValues = List(10) { "0" }
    private val registerRows = mutableListOf<RegisterRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Khởi tạo các view chính
        etIp = findViewById(R.id.etIp)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvResult = findViewById(R.id.tvResult)
        registerValuesLayout = findViewById(R.id.registerValuesLayout)

        // Tạo giao diện của các register một lần
        setupRegisterUI()

        // Xử lý sự kiện nút Start/Stop
        btnStartStop.setOnClickListener {
            if (!isRunning) {
                startModbusConnection()
            } else {
                stopModbusConnection()
            }
        }
    }

    // Hàm tạo giao diện cho các hàng register (chỉ tạo 1 lần)
    private fun setupRegisterUI() {
        registerValuesLayout.removeAllViews()
        registerRows.clear()
        for (index in 0 until 10) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val labelTextView = TextView(this).apply {
                text = "R$index = "
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val currentValueTextView = TextView(this).apply {
                text = "0"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val editValueEditText = EditText(this).apply {
                setText("0")
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }
            val confirmButton = Button(this).apply {
                text = "Xác nhận"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Xử lý sự kiện khi nhấn nút "Xác nhận"
            confirmButton.setOnClickListener {
                val newValue = editValueEditText.text.toString()
                // Cập nhật giá trị hiển thị
                currentValueTextView.text = newValue
                // Lưu giá trị mới vào danh sách registerValues
                registerValues = registerValues.toMutableList().apply { this[index] = "R$index = $newValue" }
                // Ghi giá trị mới lên ESP32 nếu đã kết nối
                if (connection?.isConnected == true) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val writeReq = WriteSingleRegisterRequest(index, SimpleRegister(newValue.toInt()))
                            val writeTrans = ModbusTCPTransaction(connection)
                            writeTrans.request = writeReq
                            writeTrans.execute()
                        } catch (e: Exception) {
                            Log.e("MODBUS", "Error writing to register: ${e.message}")
                        }
                    }
                }
            }

            // Thêm các view vào row
            rowLayout.addView(labelTextView)
            rowLayout.addView(currentValueTextView)
            rowLayout.addView(editValueEditText)
            rowLayout.addView(confirmButton)
            // Thêm row vào layout chứa
            registerValuesLayout.addView(rowLayout)
            // Lưu lại tham chiếu vào registerRows
            registerRows.add(RegisterRow(labelTextView, currentValueTextView, editValueEditText, confirmButton))
        }
    }

    // Hàm cập nhật giá trị hiển thị của các register (không tạo lại toàn bộ giao diện)
    private fun updateRegisterValues(newValues: List<String>) {
        registerRows.forEachIndexed { index, row ->
            val newValue = newValues.getOrNull(index)?.split(" = ")?.getOrElse(1) { "0" } ?: "0"
            // Chỉ cập nhật nếu người dùng không đang nhập (không có focus)
            if (!row.editValueEditText.hasFocus()) {
                row.currentValueTextView.text = newValue
                row.editValueEditText.setText(newValue)
            }
        }
    }

    // Hàm kết nối và đọc dữ liệu từ ESP32
    private fun startModbusConnection() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ipAddress = etIp.text.toString().trim()
                val addr = InetAddress.getByName(ipAddress)
                val con = TCPMasterConnection(addr).apply {
                    port = 502
                    connect()
                }
                connection = con
                Log.d("MODBUS", "Đã kết nối ESP32")

                while (isRunning) {
                    try {
                        // Đọc 10 thanh ghi
                        val readReq = ReadMultipleRegistersRequest(0, 10)
                        val readTrans = ModbusTCPTransaction(con)
                        readTrans.request = readReq
                        readTrans.execute()
                        val res = readTrans.response as ReadMultipleRegistersResponse

                        val data = mutableListOf<String>()
                        for (i in 0 until res.wordCount) {
                            data.add("R$i = ${res.getRegisterValue(i)}")
                        }

                        // Cập nhật giao diện trên UI thread
                        withContext(Dispatchers.Main) {
                            registerValues = data
                            tvResult.text = data.joinToString("\n")
                            updateRegisterValues(data)
                        }
                    } catch (e: Exception) {
                        Log.e("MODBUS", "Lỗi trong vòng lặp: ${e.message}")
                    }
                    delay(500) // Cập nhật mỗi 0.5 giây
                }
                con.close()
            } catch (e: Exception) {
                Log.e("MODBUS", "Lỗi kết nối: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Kết nối thất bại", Toast.LENGTH_SHORT).show()
                    tvResult.text = "Lỗi: ${e.message}"
                    isRunning = false
                }
            }
        }
    }

    // Hàm dừng kết nối
    private fun stopModbusConnection() {
        isRunning = false
        connection?.close()
        tvResult.text = "Đã dừng cập nhật"
    }
}
