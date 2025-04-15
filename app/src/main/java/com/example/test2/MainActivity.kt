package com.example.test2

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.View
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

    // --- Views cấu hình ---
    private lateinit var tvPLCAddress: TextView   // Hiển thị địa chỉ PLC đã config
    private lateinit var etIp: EditText            // Hiển thị địa chỉ (disabled)
    private lateinit var btnConnectDisconnect: Button
    private lateinit var tvStatus: TextView

    // --- Các nút điều khiển ở panel giữa ---
    private lateinit var btnPower: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnBuzzer: ImageButton
    private lateinit var btnPosition: ImageButton
    private lateinit var btnMode: ImageButton       // Dùng để chuyển đổi giao diện Auto/Manual
    private lateinit var btnHandlingMode: ImageButton
    private lateinit var btnEmergencyStop: ImageButton
    private lateinit var btnCountPallet: ImageButton

    // --- Các TextView hiển thị số liệu ---
//    private lateinit var tvPalletCount: TextView
//    private lateinit var tvLoadCount: TextView
//    private lateinit var tvUnloadCount: TextView

    // --- Center panel: vị trí Shuttle ---
    private lateinit var tvShuttlePosition: TextView

    // --- Panel bên trái ---
    // Auto
    private lateinit var layoutAutoLeft: LinearLayout
    private lateinit var btnPickPallets: ImageButton
    private lateinit var btnPickPallet: ImageButton
    private lateinit var btnStackA: ImageButton
    // Manual
    private lateinit var layoutManualLeft: LinearLayout
    private lateinit var btnManualForward: ImageButton
    private lateinit var btnManualReverse: ImageButton

    // --- Panel bên phải ---
    // Auto
    private lateinit var layoutAutoRight: LinearLayout
    private lateinit var btnTakePallets: ImageButton
    private lateinit var btnTakePallet: ImageButton
    private lateinit var btnStackB: ImageButton
    // Manual
    private lateinit var layoutManualRight: LinearLayout
    private lateinit var btnManualUp: ImageButton
    private lateinit var btnManualDown: ImageButton

    // --- Các biến trạng thái nội bộ (được cập nhật từ PLC) ---
    private var isPowerOn = false
    private var isLocked = false
    private var isBuzzerOn = false
    private var isPositionA = false
    private var isAutoMode = false
    private var isFIFO = false
    private var isCountPallet = false

    private var isPickPallets = false
    private var isPickPallet = false
    private var isStackA = false
    private var isForward = false
    private var isReverse = false
    private var isTakePallets = false
    private var isTakePallet = false
    private var isStackB = false
    private var isUp = false
    private var isDown = false

    // Biến điều khiển UI (Auto vs Manual)
    private var isAutoModeState = true  // true: UI hiển thị Auto; false: hiển thị Manual

    // --- Modbus ---
    private var connection: TCPMasterConnection? = null
    @Volatile private var isCommandRunning = false
    private val modbusScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    // --- Thông tin PLC (lấy từ ConfigActivity, lưu trong SharedPreferences) ---
    private var plcIp: String = ""
    private var plcPort: Int = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lấy thông tin PLC từ SharedPreferences
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        plcIp = prefs.getString("modbus_ip", "") ?: ""
        plcPort = prefs.getInt("modbus_port", 502)
        setContentView(R.layout.activity_main)
        initViews()
        // Hiển thị thông tin PLC (ô nhập bị disable)
        etIp.setText(plcIp)
        etIp.isEnabled = false
        tvPLCAddress.text = "PLC: $plcIp:$plcPort"
        setListeners()
    }

    private fun initViews() {
        // --- Các view cấu hình ---
        tvPLCAddress = findViewById(R.id.tvPLCAddress)
        etIp = findViewById(R.id.etIp)
        btnConnectDisconnect = findViewById(R.id.btnConnectDisconnect)
        tvStatus = findViewById(R.id.tvStatus)

        // --- Các nút ở panel giữa ---
        btnPower = findViewById(R.id.btnPower)
        btnLock = findViewById(R.id.btnLock)
        btnBuzzer = findViewById(R.id.btnBuzzer)
        btnPosition = findViewById(R.id.btnPosition)
        btnMode = findViewById(R.id.btnMode)
        btnHandlingMode = findViewById(R.id.btnHandlingMode)
        btnEmergencyStop = findViewById(R.id.btnEmergencyStop)
        btnCountPallet = findViewById(R.id.btnCountPallet)

//        tvPalletCount = findViewById(R.id.tvPalletCount)
//        tvLoadCount = findViewById(R.id.tvLoadCount)
//        tvUnloadCount = findViewById(R.id.tvUnloadCount)

        tvShuttlePosition = findViewById(R.id.tvShuttlePosition)

        // --- Panel bên trái ---
        layoutAutoLeft = findViewById(R.id.layoutAutoLeft)
        layoutManualLeft = findViewById(R.id.layoutManualLeft)
        btnPickPallets = findViewById(R.id.btnPickPallets)
        btnPickPallet = findViewById(R.id.btnPickPallet)
        btnStackA = findViewById(R.id.btnStackA)

        // --- Panel bên trái Manual ---
        // (Nếu chưa được dùng, nhưng khai báo cho an toàn)
        // layoutManualLeft đã khai báo trên
        btnManualForward = findViewById(R.id.btnManualForward)
        btnManualReverse = findViewById(R.id.btnManualReverse)

        // --- Panel bên phải ---
        layoutAutoRight = findViewById(R.id.layoutAutoRight)
        layoutManualRight = findViewById(R.id.layoutManualRight)
        btnTakePallets = findViewById(R.id.btnTakePallets)
        btnTakePallet = findViewById(R.id.btnTakePallet)
        btnStackB = findViewById(R.id.btnStackB)

        // --- Panel bên phải Manual ---
        btnManualUp = findViewById(R.id.btnManualUp)
        btnManualDown = findViewById(R.id.btnManualDown)
    }

    private fun setListeners() {
        btnConnectDisconnect.setOnClickListener {
            if (connection == null || !connection!!.isConnected)
                connectToPLC()
            else
                disconnectPLC()
        }

        // Nút btnMode: chuyển đổi giữa giao diện Auto và Manual (các panel bên trái và bên phải)
        // Các nút ở panel giữa (gửi lệnh ghi)
        btnPower.setOnClickListener { handleBooleanCommand("power") }
        btnLock.setOnClickListener { handleBooleanCommand("lock") }
        btnBuzzer.setOnClickListener { handleBooleanCommand("buzzer") }
        btnPosition.setOnClickListener { handleBooleanCommand("position") }
        btnMode.setOnClickListener { handleBooleanCommand("mode")
            isAutoModeState = !isAutoModeState
            if (isAutoModeState) {
                layoutAutoLeft.visibility = View.VISIBLE
                layoutAutoRight.visibility = View.VISIBLE
                layoutManualLeft.visibility = View.GONE
                layoutManualRight.visibility = View.GONE
                btnMode.setImageResource(R.drawable.ic_mode_auto)
            } else {
                layoutAutoLeft.visibility = View.GONE
                layoutAutoRight.visibility = View.GONE
                layoutManualLeft.visibility = View.VISIBLE
                layoutManualRight.visibility = View.VISIBLE
                btnMode.setImageResource(R.drawable.ic_mode_manual)
            }
        }
        btnHandlingMode.setOnClickListener { handleBooleanCommand("handling") }
        btnEmergencyStop.setOnClickListener {
            if (canExecuteCommand())
                sendBooleanCommand("emergencyStop", true)
            else
                showBusyMessage()
        }
        btnCountPallet.setOnClickListener { handleBooleanCommand("countPallet")}

        // --- Các nút trong panel bên trái Auto ---
        btnPickPallets.setOnClickListener {handleBooleanCommand("pickPallets")}
        btnPickPallet.setOnClickListener {handleBooleanCommand("pickPallet")}
        btnStackA.setOnClickListener {handleBooleanCommand("stackA")}

        // --- Các nút trong panel bên trái Manual ---
        btnManualForward.setOnClickListener {handleBooleanCommand("forward")}
        btnManualReverse.setOnClickListener {handleBooleanCommand("reverse")}

        // --- Các nút trong panel bên phải Auto ---
        btnTakePallets.setOnClickListener {handleBooleanCommand("takePallets")}
        btnTakePallet.setOnClickListener {handleBooleanCommand("takePallet")}
        btnStackB.setOnClickListener {handleBooleanCommand("stackB")}

        // --- Các nút trong panel bên phải Manual ---
        btnManualUp.setOnClickListener {handleBooleanCommand("up")}
        btnManualDown.setOnClickListener {handleBooleanCommand("down")}
    }


    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun showBusyMessage() = showToast("Đang xử lý lệnh khác, vui lòng chờ...")

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

    // Hàm polling để đọc dữ liệu từ PLC và cập nhật UI (polling mỗi 500ms)
    private fun startPollingState() {
        pollingJob?.cancel()
        pollingJob = modbusScope.launch {
            while (isActive && connection?.isConnected == true) {
                try {
                    val trans = ModbusTCPTransaction(connection)
                    trans.request = ReadMultipleRegistersRequest(0, 64)
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

                            btnCountPallet.setImageResource(if (it.getRegisterValue(7) == 1) R.drawable.ic_count_pallet_on else R.drawable.ic_count_pallet_on)
                            btnPickPallets.setImageResource(if (it.getRegisterValue(8) == 1) R.drawable.ic_pallets_plus1 else R.drawable.ic_pallets_plus2)
                            btnPickPallet.setImageResource(if (it.getRegisterValue(9) == 1) R.drawable.ic_pallet_plus1 else R.drawable.ic_pallet_plus2)
                            btnStackA.setImageResource(if (it.getRegisterValue(10) == 1) R.drawable.ic_pallet_plus1 else R.drawable.ic_pallet_plus2)
                            btnTakePallets.setImageResource(if (it.getRegisterValue(11) == 1) R.drawable.ic_pallets_minus1 else R.drawable.ic_pallets_minus2)
                            btnTakePallet.setImageResource(if (it.getRegisterValue(12) == 1) R.drawable.ic_pallet_minus1 else R.drawable.ic_pallet_minus2)
                            btnStackB.setImageResource(if (it.getRegisterValue(13) == 1) R.drawable.ic_pallet_plus1 else R.drawable.ic_pallet_plus2)
                            btnManualForward.setImageResource(if (it.getRegisterValue(14) == 1) R.drawable.ic_shuttle_forward_on else R.drawable.ic_shuttle_forward_off)
                            btnManualReverse.setImageResource(if (it.getRegisterValue(15) == 1) R.drawable.ic_shuttle_reverse_on else R.drawable.ic_shuttle_reverse_off)
                            btnManualUp.setImageResource(if (it.getRegisterValue(16) == 1) R.drawable.ic_shuttle_up_on else R.drawable.ic_shuttle_up_off)
                            btnManualDown.setImageResource(if (it.getRegisterValue(17) == 1) R.drawable.ic_shuttle_down_on else R.drawable.ic_shuttle_down_off)

//                            tvPalletCount.text = "${it.getRegisterValue(19)}"
//                            tvLoadCount.text = "${it.getRegisterValue(20)}"
//                            tvUnloadCount.text = "${it.getRegisterValue(21)}"
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
            "countPallet" -> {
                isCountPallet = !isCountPallet
                btnCountPallet.setImageResource(if (isCountPallet) R.drawable.ic_count_pallet_on else R.drawable.ic_count_pallet_off)
            }
            "pickPallets" -> {
                isPickPallets = !isPickPallets
                btnPickPallets.setImageResource(if (isPickPallets) R.drawable.ic_pallets_plus1 else R.drawable.ic_pallets_plus2)
            }
            "pickPallet" -> {
                isPickPallet = !isPickPallet
                btnPickPallet.setImageResource(if (isPickPallet) R.drawable.ic_pallet_plus1 else R.drawable.ic_pallet_plus2)
            }
            "stackA" -> {
                isStackA = !isStackA
                btnStackA.setImageResource(if (isStackA) R.drawable.ic_stack_pallets_a1 else R.drawable.ic_stack_pallets_a2)
            }
            "forward" -> {
                isForward = !isForward
                btnManualForward.setImageResource(if (isForward) R.drawable.ic_shuttle_forward_on else R.drawable.ic_shuttle_forward_off)
            }
            "reverse" -> {
                isReverse = !isReverse
                btnManualReverse.setImageResource(if (isReverse) R.drawable.ic_shuttle_reverse_on else R.drawable.ic_shuttle_reverse_off)
            }
            "takePallets" -> {
                isTakePallets = !isTakePallets
                btnTakePallets.setImageResource(if (isTakePallets) R.drawable.ic_pallets_minus1 else R.drawable.ic_pallets_minus2)
            }
            "takePallet" -> {
                isTakePallet = !isTakePallet
                btnTakePallet.setImageResource(if (isTakePallet) R.drawable.ic_pallet_minus1 else R.drawable.ic_pallet_minus2)
            }
            "stackB" -> {
                isStackB = !isStackB
                btnStackB.setImageResource(if (isStackB) R.drawable.ic_stack_pallets_b1 else R.drawable.ic_stack_pallets_b2)
            }
            "up" -> {
                isUp = !isUp
                btnManualUp.setImageResource(if (isUp) R.drawable.ic_shuttle_up_on else R.drawable.ic_shuttle_up_off)
            }
            "down" -> {
                isDown = !isDown
                btnManualDown.setImageResource(if (isDown) R.drawable.ic_shuttle_down_on else R.drawable.ic_shuttle_down_off)
            }
        }
        val state = when (command) {
            "power" -> isPowerOn
            "lock" -> isLocked
            "buzzer" -> isBuzzerOn
            "position" -> isPositionA
            "mode" -> isAutoMode
            "handling" -> isFIFO
            "countPallet" -> isCountPallet

            "pickPallets" -> isPickPallets
            "pickPallet" -> isPickPallet
            "stackA" -> isStackA
            "forward" -> isForward
            "reverse" -> isReverse
            "takePallets" -> isTakePallets
            "takePallet" -> isTakePallet
            "stackB" -> isStackB
            "up" -> isUp
            "down" -> isDown
            else -> false
        }
        sendBooleanCommandTest(command, state)
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
                    "countPallet" ->8
                    "pickPallets" -> 9
                    "pickPallet" -> 10
                    "stackA" -> 11
                    "forward" -> 12
                    "reverse" -> 13
                    "takePallets" -> 14
                    "takePallet" -> 15
                    "stackB" -> 16
                    "up" -> 17
                    "down" -> 18
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

    private fun sendBooleanCommandTest(command: String, state: Boolean) {
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
                    "countPallet" ->8
                    "pickPallets" -> 9
                    "pickPallet" -> 10
                    "stackA" -> 11
                    "forward" -> 12
                    "reverse" -> 13
                    "takePallets" -> 14
                    "takePallet" -> 15
                    "stackB" -> 16
                    "up" -> 17
                    "down" -> 18
                    else -> return@launch
                }
                // Bước 1: Đọc giá trị hiện tại
                val readTrans = ModbusTCPTransaction(connection)
                readTrans.request = ReadMultipleRegistersRequest(registerAddress - 1, 1)
                readTrans.execute()
                val response = readTrans.response as? ReadMultipleRegistersResponse
                response?.let { res -> val currentValue = res.getRegisterValue(0)
                    val valueToSend = if (currentValue == 1) 0 else 1
                    // Bước 2: Ghi giá trị mới
                    val writeTrans = ModbusTCPTransaction(connection)
                    val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(valueToSend))
                    writeTrans.request = writeReq
                    writeTrans.execute()
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

    // Ví dụ hàm toggle dựa trên giá trị hiện tại từ PLC
    private fun sendBooleanCommandTest1(command: String, state: Boolean) {
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
                    val currentValue = res.getRegisterValue(0)
                    val valueToSend = if (currentValue == 1) 0 else 1
                    // Bước 2: Ghi giá trị mới
                    val writeTrans = ModbusTCPTransaction(connection)
                    val writeReq = WriteSingleRegisterRequest(registerAddress - 1, SimpleRegister(valueToSend))
                    writeTrans.request = writeReq
                    writeTrans.execute()
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

    private fun updateStatus(msg: String) {
        runOnUiThread { tvStatus.text = msg }
    }

    private fun canExecuteCommand() = !isCommandRunning && (connection?.isConnected == true)

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        modbusScope.cancel()
        connection?.close()
    }
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////