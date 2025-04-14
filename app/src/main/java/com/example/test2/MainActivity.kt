package com.example.test2

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import net.wimpi.modbus.io.ModbusTCPTransaction
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import net.wimpi.modbus.msg.WriteSingleRegisterRequest
import net.wimpi.modbus.net.TCPMasterConnection
import net.wimpi.modbus.procimg.SimpleRegister
import java.net.InetAddress
import java.net.Socket

class MainActivity : ComponentActivity() {

    // Views kết nối và hiển thị cấu hình
    private lateinit var tvPLCAddress: TextView   // Hiển thị địa chỉ PLC đã config
    private lateinit var etIp: EditText            // Hiển thị địa chỉ (disabled)
    private lateinit var btnConnectDisconnect: Button
    private lateinit var tvStatus: TextView

    // Các nút điều khiển boolean (icon)
    private lateinit var btnPower: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnBuzzer: ImageButton
    private lateinit var btnPosition: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnHandlingMode: ImageButton
    private lateinit var btnEmergencyStop: ImageButton

    // Các trường hiển thị số
    private lateinit var tvPalletCount: TextView
    private lateinit var tvLoadCount: TextView
    private lateinit var tvUnloadCount: TextView

    // Các thành phần Load/Unload
    private lateinit var etLoadInput: EditText
    private lateinit var btnLoadConfirm: Button
    private lateinit var etUnloadInput: EditText
    private lateinit var btnUnloadConfirm: Button

    // Các nút điều khiển manual
    private lateinit var btnManualUp: Button
    private lateinit var btnManualDown: Button
    private lateinit var btnManualLeft: Button
    private lateinit var btnManualRight: Button
    private lateinit var btnManualStop: Button

    // Biến trạng thái nội bộ
    private var isPowerOn = false
    private var isLocked = false
    private var isBuzzerOn = false
    private var isPositionA = true
    private var isAutoMode = true
    private var isFIFO = true

    private var connection: TCPMasterConnection? = null
    @Volatile
    private var isCommandRunning = false
    private val modbusScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    // Địa chỉ PLC được lưu từ ConfigActivity
    private var plcIp: String = ""
    private var plcPort: Int = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lấy địa chỉ PLC từ SharedPreferences
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        plcIp = prefs.getString("modbus_ip", "") ?: ""
        plcPort = prefs.getInt("modbus_port", 502)
        setContentView(R.layout.activity_main)
        initViews()
        // Hiển thị địa chỉ PLC đã lưu (ô nhập bị disable)
        etIp.setText(plcIp)
        etIp.isEnabled = false
        tvPLCAddress.text = "PLC: $plcIp:$plcPort"
        setListeners()
    }

    private fun initViews() {
        tvPLCAddress = findViewById(R.id.tvPLCAddress)
        etIp = findViewById(R.id.etIp)
        btnConnectDisconnect = findViewById(R.id.btnConnectDisconnect)
        tvStatus = findViewById(R.id.tvStatus)

        btnPower = findViewById(R.id.btnPower)
        btnLock = findViewById(R.id.btnLock)
        btnBuzzer = findViewById(R.id.btnBuzzer)
        btnPosition = findViewById(R.id.btnPosition)
        btnMode = findViewById(R.id.btnMode)
        btnHandlingMode = findViewById(R.id.btnHandlingMode)
        btnEmergencyStop = findViewById(R.id.btnEmergencyStop)

        tvPalletCount = findViewById(R.id.tvPalletCount)
        tvLoadCount = findViewById(R.id.tvLoadLabel)
        tvUnloadCount = findViewById(R.id.tvUnloadLabel)

        etLoadInput = findViewById(R.id.etLoadInput)
        btnLoadConfirm = findViewById(R.id.btnLoadConfirm)
        etUnloadInput = findViewById(R.id.etUnloadInput)
        btnUnloadConfirm = findViewById(R.id.btnUnloadConfirm)

        btnManualUp = findViewById(R.id.btnManualUp)
        btnManualDown = findViewById(R.id.btnManualDown)
        btnManualLeft = findViewById(R.id.btnManualLeft)
        btnManualRight = findViewById(R.id.btnManualRight)
        btnManualStop = findViewById(R.id.btnManualStop)
    }

    private fun setListeners() {
        btnConnectDisconnect.setOnClickListener {
            if (connection == null || !connection!!.isConnected)
                connectToPLC()
            else
                disconnectPLC()
        }

        btnPower.setOnClickListener { handleBooleanCommand("power") }
        btnLock.setOnClickListener { handleBooleanCommand("lock") }
        btnBuzzer.setOnClickListener { handleBooleanCommand("buzzer") }
        btnPosition.setOnClickListener { handleBooleanCommand("position") }
        btnMode.setOnClickListener { handleBooleanCommand("mode") }
        btnHandlingMode.setOnClickListener { handleBooleanCommand("handling") }
        btnEmergencyStop.setOnClickListener {
            if (canExecuteCommand())
                sendBooleanCommand("emergencyStop", true)
            else
                showBusyMessage()
        }

        btnLoadConfirm.setOnClickListener {
            val quantity = etLoadInput.text.toString().toIntOrNull()
            if (quantity == null)
                showToast("Nhập số hợp lệ cho Load")
            else if (canExecuteCommand())
                sendNumericCommand("load", quantity)
            else
                showBusyMessage()
        }

        btnUnloadConfirm.setOnClickListener {
            val quantity = etUnloadInput.text.toString().toIntOrNull()
            if (quantity == null)
                showToast("Nhập số hợp lệ cho Unload")
            else if (canExecuteCommand())
                sendNumericCommand("unload", quantity)
            else
                showBusyMessage()
        }

        btnManualUp.setOnClickListener { sendManualCommand("up") }
        btnManualDown.setOnClickListener { sendManualCommand("down") }
        btnManualLeft.setOnClickListener { sendManualCommand("left") }
        btnManualRight.setOnClickListener { sendManualCommand("right") }
        btnManualStop.setOnClickListener { sendManualCommand("stop") }
    }

    private fun handleBooleanCommand(command: String) {
        if (!canExecuteCommand()) {
            showBusyMessage()
            return
        }
        when (command) {
            "power" -> {
                isPowerOn = !isPowerOn
                btnPower.setImageResource(if (isPowerOn) R.drawable.ic_power_on else R.drawable.ic_power_off)
            }
            "lock" -> {
                isLocked = !isLocked
                btnLock.setImageResource(if (isLocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open)
            }
            "buzzer" -> {
                isBuzzerOn = !isBuzzerOn
                btnBuzzer.setImageResource(if (isBuzzerOn) R.drawable.ic_buzzer_on else R.drawable.ic_buzzer_off)
            }
            "position" -> {
                isPositionA = !isPositionA
                btnPosition.setImageResource(if (isPositionA) R.drawable.ic_position_a else R.drawable.ic_position_b)
            }
            "mode" -> {
                isAutoMode = !isAutoMode
                btnMode.setImageResource(if (isAutoMode) R.drawable.ic_mode_auto else R.drawable.ic_mode_manual)
            }
            "handling" -> {
                isFIFO = !isFIFO
                btnHandlingMode.setImageResource(if (isFIFO) R.drawable.ic_fifo else R.drawable.ic_lifo)
            }
        }
        val state = when (command) {
            "power" -> isPowerOn
            "lock" -> isLocked
            "buzzer" -> isBuzzerOn
            "position" -> isPositionA
            "mode" -> isAutoMode
            "handling" -> isFIFO
            else -> false
        }
        sendBooleanCommandtest(command, state)
    }

    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun showBusyMessage() = showToast("Đang xử lý lệnh khác, vui lòng chờ...")
    private fun canExecuteCommand() = !isCommandRunning && (connection?.isConnected == true)

    private fun connectToPLC() {
        val ipAddress = etIp.text.toString().trim()
        if (ipAddress.isEmpty()) {
            updateStatus("IP không được để trống")
            return
        }
        modbusScope.launch {
            try {
                val addr = InetAddress.getByName(ipAddress)
                val con = TCPMasterConnection(addr).apply {
                    port = 502
                    connect()
                }
                connection = con
                updateStatus("Đã kết nối tới PLC")
                runOnUiThread { btnConnectDisconnect.text = "Ngắt kết nối" }
                startPollingState()
            } catch (e: Exception) {
                updateStatus("Lỗi kết nối: ${e.message}")
            }
        }
    }

    private suspend fun readInitialState() {
        try {
            val trans = ModbusTCPTransaction(connection)
            trans.request = ReadMultipleRegistersRequest(0, 22)
            trans.execute()
            val response = trans.response as? ReadMultipleRegistersResponse
            response?.let {
                withContext(Dispatchers.Main) {
                    if (it.getRegisterValue(0) == 1) {
                        btnPower.setImageResource(R.drawable.ic_power_on)
                    } else {
                        btnPower.setImageResource(R.drawable.ic_power_off)
                    }

                    if (it.getRegisterValue(1) == 1) {
                        btnLock.setImageResource(R.drawable.ic_lock_closed)
                    } else {
                        btnLock.setImageResource(R.drawable.ic_lock_open)
                    }

                    if (it.getRegisterValue(2) == 1) {
                        btnBuzzer.setImageResource(R.drawable.ic_buzzer_on)
                    } else {
                        btnBuzzer.setImageResource(R.drawable.ic_buzzer_off)
                    }

                    if (it.getRegisterValue(3) == 1) {
                        btnPosition.setImageResource(R.drawable.ic_position_a)
                    } else {
                        btnPosition.setImageResource(R.drawable.ic_position_b)
                    }

                    if (it.getRegisterValue(4) == 1) {
                        btnMode.setImageResource(R.drawable.ic_mode_auto)
                    } else {
                        btnMode.setImageResource(R.drawable.ic_mode_manual)
                    }

                    if (it.getRegisterValue(5) == 1) {
                        btnHandlingMode.setImageResource(R.drawable.ic_fifo)
                    } else {
                        btnHandlingMode.setImageResource(R.drawable.ic_lifo)
                    }

                    tvPalletCount.text = "${it.getRegisterValue(19)}"
                    tvLoadCount.text = "${it.getRegisterValue(20)}"
                    tvUnloadCount.text = "${it.getRegisterValue(21)}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("Lỗi readInitialState: ${e.localizedMessage}")
        }
    }

    private fun disconnectPLC() {
        modbusScope.launch {
            try {
                pollingJob?.cancel()
                connection?.close()
                connection = null
                updateStatus("Đã ngắt kết nối")
                runOnUiThread { btnConnectDisconnect.text = "Kết nối" }
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Lỗi khi ngắt kết nối: ${e.localizedMessage}")
            }
        }
    }

    private fun startPollingState() {
        pollingJob?.cancel()
        pollingJob = modbusScope.launch {
            while (isActive && connection?.isConnected == true) {
                try {
                    val trans = ModbusTCPTransaction(connection)
                    trans.request = ReadMultipleRegistersRequest(0, 22)
                    trans.execute()
                    val response = trans.response as? ReadMultipleRegistersResponse
                    response?.let {
                        runOnUiThread {
                            btnPower.setImageResource(if (it.getRegisterValue(0) == 1) R.drawable.ic_power_on else R.drawable.ic_power_off)
                            btnLock.setImageResource(if (it.getRegisterValue(1) == 1) R.drawable.ic_lock_closed else R.drawable.ic_lock_open)
                            btnBuzzer.setImageResource(if (it.getRegisterValue(2) == 1) R.drawable.ic_buzzer_on else R.drawable.ic_buzzer_off)
                            btnPosition.setImageResource(if (it.getRegisterValue(3) == 1) R.drawable.ic_position_a else R.drawable.ic_position_b)
                            btnMode.setImageResource(if (it.getRegisterValue(4) == 1) R.drawable.ic_mode_auto else R.drawable.ic_mode_manual)
                            btnHandlingMode.setImageResource(if (it.getRegisterValue(5) == 1) R.drawable.ic_fifo else R.drawable.ic_lifo)

                            tvPalletCount.text = "${it.getRegisterValue(19)}"
                            tvLoadCount.text = "${it.getRegisterValue(20)}"
                            tvUnloadCount.text = "${it.getRegisterValue(21)}"
                        }
                    }
                    delay(500) // Delay 0.5s như yêu cầu
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateStatus("Polling error: ${e.localizedMessage}")
                    delay(1000) // Delay nếu gặp lỗi
                }
            }
        }
    }


    private fun sendBooleanCommand(command: String, state: Boolean) {
        modbusScope.launch {
            isCommandRunning = true
            try {
                val registerAddress = when (command) {
                    "power" -> 1
                    "lock" -> 2
                    "buzzer" -> 3
                    "position" -> 4
                    "mode" -> 5
                    "handling" -> 6
                    "emergencyStop" -> 7
                    else -> return@launch
                }
                val valueToSend = if (state) 1 else 0
                val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(valueToSend))
                val trans = ModbusTCPTransaction(connection)
                trans.request = writeReq
                trans.execute()
                updateStatus("Lệnh $command gửi: $valueToSend")
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Lỗi $command: ${e.localizedMessage}")
            } finally {
                isCommandRunning = false
            }
        }
    }

    private fun sendBooleanCommandtest(command: String, state: Boolean) {
        modbusScope.launch {
            isCommandRunning = true
            try {
                val registerAddress = when (command) {
                    "power" -> 1
                    "lock" -> 2
                    "buzzer" -> 3
                    "position" -> 4
                    "mode" -> 5
                    "handling" -> 6
                    "emergencyStop" -> 7
                    else -> return@launch
                }
                // Bước 1: Đọc giá trị hiện tại
                val readTrans = ModbusTCPTransaction(connection)
                readTrans.request = ReadMultipleRegistersRequest(registerAddress - 1, 1)
                readTrans.execute()
                val response = readTrans.response as? ReadMultipleRegistersResponse
                response?.let { res ->
                // Lấy giá trị từ register đầu tiên trong response (index 0)
                    val currentValue = res.getRegisterValue(0)
                    val valueToSend = if (currentValue == 1) 0 else 1
                // Bước 2: Ghi giá trị mới
                    val writeTrans = ModbusTCPTransaction(connection)
                    val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(valueToSend))
                    writeTrans.request = writeReq
                    writeTrans.execute()
                    // Cập nhật UI nếu cần
                    withContext(Dispatchers.Main) {
                        updateStatus("Lệnh $command gửi: $valueToSend")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Lỗi $command: ${e.localizedMessage}")
            } finally {
                isCommandRunning = false
            }
        }
    }


    private fun sendNumericCommand(command: String, value: Int) {
        modbusScope.launch {
            isCommandRunning = true
            try {
                val registerAddress = when (command) {
                    "load" -> 21
                    "unload" -> 22
                    else -> return@launch
                }
                val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(value))
                val trans = ModbusTCPTransaction(connection)
                trans.request = writeReq
                trans.execute()
                updateStatus("Lệnh $command gửi: $value")
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Lỗi $command: ${e.localizedMessage}")
            } finally {
                isCommandRunning = false
            }
        }
    }

    private fun sendManualCommand(direction: String) {
        modbusScope.launch {
            isCommandRunning = true
            try {
                val registerAddress = when (direction) {
                    "up" -> 30
                    "down" -> 31
                    "left" -> 32
                    "right" -> 33
                    "stop" -> 34
                    else -> return@launch
                }
                val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(1))
                val trans = ModbusTCPTransaction(connection)
                trans.request = writeReq
                trans.execute()
                updateStatus("Manual $direction gửi")
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatus("Lỗi manual $direction: ${e.localizedMessage}")
            } finally {
                isCommandRunning = false
            }
        }
    }

    private fun updateStatus(msg: String) {
        runOnUiThread { tvStatus.text = msg }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        modbusScope.cancel()
        connection?.close()
    }
}
