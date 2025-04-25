package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse

class MainActivity : ComponentActivity() {

    // Region: View declarations
    private lateinit var tvNameDevice: TextView
    private var deviceName: String = "PLC"
    private lateinit var etIp: EditText
    private lateinit var btnConnectDisconnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvShuttlePosition: TextView

    private lateinit var btnBack: ImageButton

    // Center panel buttons
    private lateinit var btnPower: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnBuzzer: ImageButton
    private lateinit var btnPosition: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnHandlingMode: ImageButton
    private lateinit var btnEmergencyStop: ImageButton
    private lateinit var btnCountPallet: ImageButton

    // Left panel
    private lateinit var layoutAutoLeft: LinearLayout
    private lateinit var layoutManualLeft: LinearLayout
    private lateinit var btnPickPallets: ImageButton
    private lateinit var btnPickPallet: ImageButton
    private lateinit var btnStackA: ImageButton
    private lateinit var btnManualForward: ImageButton
    private lateinit var btnManualReverse: ImageButton

    // Right panel
    private lateinit var layoutAutoRight: LinearLayout
    private lateinit var layoutManualRight: LinearLayout
    private lateinit var btnTakePallets: ImageButton
    private lateinit var btnTakePallet: ImageButton
    private lateinit var btnStackB: ImageButton
    private lateinit var btnManualUp: ImageButton
    private lateinit var btnManualDown: ImageButton
    // End region

    private lateinit var modbusManager: ModbusManager
    private lateinit var palletHandler: PalletCommandHandler
    private lateinit var shuttleIndicator: ShuttlePositionIndicator

    private val buttonLockStates = mutableMapOf<ModbusCommand, Boolean>()
    private val commandToButtonMap = mutableMapOf<ModbusCommand, ImageButton>()
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isConnected = false

    private data class ButtonState(
        val command: ModbusCommand,
        var isActive: Boolean = false,
        val activeResId: Int,
        val inactiveResId: Int
    )
    private data class ButtonLockCondition(
        val command: ModbusCommand,
        val lockCondition: () -> Boolean
    )

    private val buttonStates = mapOf(
        ModbusCommand.POWER to ButtonState(ModbusCommand.POWER, activeResId = R.drawable.ic_power_on, inactiveResId = R.drawable.ic_power_off),
        ModbusCommand.LOCK to ButtonState(ModbusCommand.LOCK, activeResId = R.drawable.ic_lock_closed, inactiveResId = R.drawable.ic_lock_open),
        ModbusCommand.BUZZER to ButtonState(ModbusCommand.BUZZER, activeResId = R.drawable.ic_buzzer_on, inactiveResId = R.drawable.ic_buzzer_off),
        ModbusCommand.POSITION to ButtonState(ModbusCommand.POSITION, activeResId = R.drawable.ic_position_a, inactiveResId = R.drawable.ic_position_b),
        ModbusCommand.MODE to ButtonState(ModbusCommand.MODE, activeResId = R.drawable.ic_mode_auto, inactiveResId = R.drawable.ic_mode_manual),
        ModbusCommand.HANDLING to ButtonState(ModbusCommand.HANDLING, activeResId = R.drawable.ic_fifo, inactiveResId = R.drawable.ic_lifo),
        ModbusCommand.EMERGENCY_STOP to ButtonState(ModbusCommand.EMERGENCY_STOP, activeResId = R.drawable.ic_emergency_stop, inactiveResId = R.drawable.ic_emergency_stop),
        ModbusCommand.COUNT_PALLET to ButtonState(ModbusCommand.COUNT_PALLET, activeResId = R.drawable.ic_count_pallet, inactiveResId = R.drawable.ic_count_pallet),
        ModbusCommand.PICK_PALLETS to ButtonState(ModbusCommand.PICK_PALLETS, activeResId = R.drawable.ic_pallets_plus1, inactiveResId = R.drawable.ic_pallets_plus2),
        ModbusCommand.PICK_PALLET to ButtonState(ModbusCommand.PICK_PALLET, activeResId = R.drawable.ic_pallet_plus1, inactiveResId = R.drawable.ic_pallet_plus2),
        ModbusCommand.STACK_A to ButtonState(ModbusCommand.STACK_A, activeResId = R.drawable.ic_stack_pallets_a1, inactiveResId = R.drawable.ic_stack_pallets_a2),
        ModbusCommand.FORWARD to ButtonState(ModbusCommand.FORWARD, activeResId = R.drawable.ic_shuttle_forward_on, inactiveResId = R.drawable.ic_shuttle_forward_off),
        ModbusCommand.REVERSE to ButtonState(ModbusCommand.REVERSE, activeResId = R.drawable.ic_shuttle_reverse_on, inactiveResId = R.drawable.ic_shuttle_reverse_off),
        ModbusCommand.TAKE_PALLETS to ButtonState(ModbusCommand.TAKE_PALLETS, activeResId = R.drawable.ic_pallets_minus1, inactiveResId = R.drawable.ic_pallets_minus2),
        ModbusCommand.TAKE_PALLET to ButtonState(ModbusCommand.TAKE_PALLET, activeResId = R.drawable.ic_pallet_minus1, inactiveResId = R.drawable.ic_pallet_minus2),
        ModbusCommand.STACK_B to ButtonState(ModbusCommand.STACK_B, activeResId = R.drawable.ic_stack_pallets_b1, inactiveResId = R.drawable.ic_stack_pallets_b2),
        ModbusCommand.UP to ButtonState(ModbusCommand.UP, activeResId = R.drawable.ic_shuttle_up_on, inactiveResId = R.drawable.ic_shuttle_up_off),
        ModbusCommand.DOWN to ButtonState(ModbusCommand.DOWN, activeResId = R.drawable.ic_shuttle_down_on, inactiveResId = R.drawable.ic_shuttle_down_off)
    )

    private fun initButtonLockStates() {
        buttonStates.keys.forEach { command ->
            buttonLockStates[command] = false
        }
    }

    private val buttonLockConditions = listOf(
        ButtonLockCondition(ModbusCommand.LOCK) { buttonStates[ModbusCommand.POWER]?.isActive == true },
        ButtonLockCondition(ModbusCommand.STACK_A) { buttonStates[ModbusCommand.PICK_PALLETS]?.isActive == true && buttonStates[ModbusCommand.PICK_PALLET]?.isActive == true }
    )

    private fun lockAllButtons() {
        buttonLockStates.keys.forEach { command -> buttonLockStates[command] = true }
        updateButtonEnableStates()
    }

    private fun unlockAllButtons() {
        buttonLockStates.keys.forEach { command -> buttonLockStates[command] = false }
        updateButtonEnableStates()
    }

    private fun applyCrossLocking() {
        buttonLockConditions.forEach { condition ->
            buttonLockStates[condition.command] = condition.lockCondition()
        }
        updateButtonEnableStates()
    }

    private fun updateManualButtonState(command: ModbusCommand, active: Boolean) {
        buttonStates[command]?.let { state ->
            state.isActive = active
            updateButtonUI(command)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val plcIp = prefs.getString("modbus_ip", "") ?: ""
        val plcPort = prefs.getInt("modbus_port", 502)
        deviceName = prefs.getString("plc_name", "PLC") ?: "PLC"
        modbusManager = ModbusManager(this, plcIp, plcPort)
        palletHandler = PalletCommandHandler(this, modbusManager) { canExecuteCommand() }

        val icons = listOf(
            findViewById<ImageView>(R.id.pos1),
            findViewById(R.id.pos2),
            findViewById(R.id.pos3),
            findViewById(R.id.pos4),
            findViewById(R.id.pos5),
            findViewById(R.id.pos6),
            findViewById(R.id.pos7),
            findViewById(R.id.pos8),
            findViewById(R.id.pos9),
            findViewById(R.id.pos10),
            findViewById(R.id.pos11),
            findViewById(R.id.pos12),
            findViewById(R.id.pos13)
        )
        val onRes = listOf(
            R.drawable.ic_pos1_on, R.drawable.ic_pos2_on,
            R.drawable.ic_pos3_on, R.drawable.ic_pos4_on,
            R.drawable.ic_pos5_on, R.drawable.ic_pos6_on,
            R.drawable.ic_pos7_on, R.drawable.ic_pos8_on,
            R.drawable.ic_pos9_on, R.drawable.ic_pos10_on,
            R.drawable.ic_pos11_on, R.drawable.ic_pos12_on,
            R.drawable.ic_pos13_on
        )
        val offRes = listOf(
            R.drawable.ic_pos1_off, R.drawable.ic_pos2_off,
            R.drawable.ic_pos3_off, R.drawable.ic_pos4_off,
            R.drawable.ic_pos5_off, R.drawable.ic_pos6_off,
            R.drawable.ic_pos7_off, R.drawable.ic_pos8_off,
            R.drawable.ic_pos9_off, R.drawable.ic_pos10_off,
            R.drawable.ic_pos11_off, R.drawable.ic_pos12_off,
            R.drawable.ic_pos13_off
        )
        val shuttle = findViewById<ImageView>(R.id.ivShuttle)
        val belt    = findViewById<View>(R.id.vBelt)

        shuttleIndicator = ShuttlePositionIndicator(icons, onRes, offRes, shuttle, belt)
        initViews()
        setupUI()
        setupListeners()
    }

    private fun initViews() {
        tvNameDevice = findViewById(R.id.tvNameDevice)
        etIp = findViewById(R.id.etIp)
        btnConnectDisconnect = findViewById(R.id.btnConnectDisconnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvShuttlePosition = findViewById(R.id.tvShuttlePosition)

        btnBack = findViewById(R.id.btnBack)

        btnPower = findViewById(R.id.btnPower)
        btnLock = findViewById(R.id.btnLock)
        btnBuzzer = findViewById(R.id.btnBuzzer)
        btnPosition = findViewById(R.id.btnPosition)
        btnMode = findViewById(R.id.btnMode)
        btnHandlingMode = findViewById(R.id.btnHandlingMode)
        btnEmergencyStop = findViewById(R.id.btnEmergencyStop)
        btnCountPallet = findViewById(R.id.btnCountPallet)

        btnPickPallets = findViewById(R.id.btnPickPallets)
        btnPickPallet = findViewById(R.id.btnPickPallet)
        btnStackA = findViewById(R.id.btnStackA)
        btnTakePallets = findViewById(R.id.btnTakePallets)
        btnTakePallet = findViewById(R.id.btnTakePallet)
        btnStackB = findViewById(R.id.btnStackB)

        btnManualForward = findViewById(R.id.btnManualForward)
        btnManualReverse = findViewById(R.id.btnManualReverse)
        btnManualUp = findViewById(R.id.btnManualUp)
        btnManualDown = findViewById(R.id.btnManualDown)

        layoutAutoLeft = findViewById(R.id.layoutAutoLeft)
        layoutAutoRight = findViewById(R.id.layoutAutoRight)
        layoutManualLeft = findViewById(R.id.layoutManualLeft)
        layoutManualRight = findViewById(R.id.layoutManualRight)

        commandToButtonMap[ModbusCommand.POWER] = btnPower
        commandToButtonMap[ModbusCommand.LOCK] = btnLock
        commandToButtonMap[ModbusCommand.BUZZER] = btnBuzzer
        commandToButtonMap[ModbusCommand.POSITION] = btnPosition
        commandToButtonMap[ModbusCommand.MODE] = btnMode
        commandToButtonMap[ModbusCommand.HANDLING] = btnHandlingMode
        commandToButtonMap[ModbusCommand.EMERGENCY_STOP] = btnEmergencyStop
        commandToButtonMap[ModbusCommand.COUNT_PALLET] = btnCountPallet
        commandToButtonMap[ModbusCommand.PICK_PALLETS] = btnPickPallets
        commandToButtonMap[ModbusCommand.PICK_PALLET] = btnPickPallet
        commandToButtonMap[ModbusCommand.STACK_A] = btnStackA
        commandToButtonMap[ModbusCommand.FORWARD] = btnManualForward
        commandToButtonMap[ModbusCommand.REVERSE] = btnManualReverse
        commandToButtonMap[ModbusCommand.TAKE_PALLETS] = btnTakePallets
        commandToButtonMap[ModbusCommand.TAKE_PALLET] = btnTakePallet
        commandToButtonMap[ModbusCommand.STACK_B] = btnStackB
        commandToButtonMap[ModbusCommand.UP] = btnManualUp
        commandToButtonMap[ModbusCommand.DOWN] = btnManualDown
        initButtonLockStates()
    }

    private fun setupUI() {
        etIp.isEnabled = false
        tvNameDevice.text = deviceName
        updateConnectionUI(false)
    }

    private fun setupListeners() {
        btnConnectDisconnect.setOnClickListener {
            if (isConnected) disconnect() else connect()
        }

        val toggleButtonMappings = listOf(
            btnPower to ModbusCommand.POWER,
            btnLock to ModbusCommand.LOCK,
            btnBuzzer to ModbusCommand.BUZZER,
            btnPosition to ModbusCommand.POSITION,
            btnHandlingMode to ModbusCommand.HANDLING,
            btnEmergencyStop to ModbusCommand.EMERGENCY_STOP,
            btnCountPallet to ModbusCommand.COUNT_PALLET,
            btnPickPallet to ModbusCommand.PICK_PALLET,
            btnStackA to ModbusCommand.STACK_A,
            btnTakePallet to ModbusCommand.TAKE_PALLET,
            btnStackB to ModbusCommand.STACK_B
        )

        toggleButtonMappings.forEach { (button, command) ->
            ButtonStateHandler.setupToggleButton(
                button = button,
                command = command,
                modbusManager = modbusManager,
                getCurrentState = { buttonStates[command]?.isActive ?: false },
                updateButtonUI = { cmd, active ->
                    buttonStates[cmd]?.isActive = active
                    updateButtonUI(cmd)
                },
                showToast = ::showToast,
                canExecuteCommand = ::canExecuteCommand
            )
        }

        val momentaryButtonMappings = listOf(
            btnManualForward to ModbusCommand.FORWARD,
            btnManualReverse to ModbusCommand.REVERSE,
            btnManualUp to ModbusCommand.UP,
            btnManualDown to ModbusCommand.DOWN
        )

        momentaryButtonMappings.forEach { (button, command) ->
            ButtonStateHandler.setupMomentaryButton(
                button = button,
                command = command,
                modbusManager = modbusManager,
                updateButtonUI = { cmd, active ->
                    buttonStates[cmd]?.isActive = active
                    updateButtonUI(cmd)
                },
                showToast = ::showToast,
                canExecuteCommand = ::canExecuteCommand
            )
        }

        // Sử dụng ImageButton cho btnPickPallets và btnTakePallets
        palletHandler.setupPalletButton(btnPickPallets, ModbusCommand.PICK_PALLETS)
        palletHandler.setupPalletButton(btnTakePallets, ModbusCommand.TAKE_PALLETS)

        btnMode.setOnClickListener {
            handleCommand(ModbusCommand.MODE)
            toggleUIMode()
        }

        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun handleCommand(command: ModbusCommand) {
        if (!canExecuteCommand()) {
            showBusyMessage()
            return
        }

        uiScope.launch {
            try {
                val state = buttonStates[command]!!
                val newValue = if (state.isActive) 0 else 1
                modbusManager.writeCommand(command.address, newValue)
                state.isActive = !state.isActive
                updateButtonUI(command)
            } catch (e: Exception) {
                showToast("Lỗi: ${e.message}")
            }
        }
    }

    private fun toggleUIMode() {
        val isAutoMode = buttonStates[ModbusCommand.MODE]?.isActive ?: true
        layoutAutoLeft.visibility = if (isAutoMode) View.VISIBLE else View.GONE
        layoutManualLeft.visibility = if (!isAutoMode) View.VISIBLE else View.GONE
        layoutAutoRight.visibility = if (isAutoMode) View.VISIBLE else View.GONE
        layoutManualRight.visibility = if (!isAutoMode) View.VISIBLE else View.GONE
    }

    private fun updateButtonUI(command: ModbusCommand) {
        buttonStates[command]?.let { state ->
            val button = commandToButtonMap[command]
            button?.updateModbusState(
                isActive = state.isActive,
                activeResId = state.activeResId,
                inactiveResId = state.inactiveResId
            )
        }
    }

    private fun updateButtonEnableStates() {
        buttonLockStates.forEach { (command, isLocked) ->
            val button = commandToButtonMap[command]
            button?.isEnabled = !isLocked
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        disconnect()
        val intent = Intent(this, ConfigActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun connect() {
        if (isConnected) return
        uiScope.launch {
            val success = modbusManager.connect()
            if (success) {
                isConnected = true
                updateConnectionUI(true)
                startPolling()
            } else {
                showToast("Kết nối thất bại")
            }
        }
    }

    private fun startPolling() {
        modbusManager.startPolling { result ->
            when (result) {
                is ModbusManager.ModbusResult.Success -> {
                    runOnUiThread { updateUI(result.response) }
                }
                is ModbusManager.ModbusResult.Error -> {
                    runOnUiThread { showToast(result.message) }
                }
            }
        }
    }

    private fun updateUI(response: ReadMultipleRegistersResponse) {
        buttonStates.forEach { (command, state) ->
            state.isActive = response.getRegisterValue(command.address - 1) == 1
            updateButtonUI(command)
        }

        val errorCode = response.getRegisterValue(59)
        if (errorCode == 8085) {
            lockAllButtons()
        } else {
            unlockAllButtons()
            applyCrossLocking()
        }

        val posReg = response.getRegisterValue(ModbusCommand.LOCATION.address - 1)
        val iconIndex = posReg.coerceIn(1, 13)
        tvShuttlePosition.text = "Vị trí Shuttle: $iconIndex"
        shuttleIndicator.update(iconIndex)

    }

    private fun disconnect() {
        if (!isConnected) return
        try {
            modbusManager.disconnect()
            isConnected = false
            updateConnectionUI(false)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in disconnect: ${e.message}", e)
            showToast("Lỗi ngắt kết nối: ${e.message}")
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        btnConnectDisconnect.text = if (connected) "Ngắt kết nối" else "Kết nối"
        tvStatus.text = if (connected) "Đã kết nối" else "Chưa kết nối"
    }

    private fun canExecuteCommand() = isConnected && !modbusManager.isBusy

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showBusyMessage() = showToast("Đang xử lý lệnh khác, vui lòng chờ...")

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        deviceName = prefs.getString("plc_name", "PLC") ?: "PLC"
        setupUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (this::modbusManager.isInitialized) {
                modbusManager.disconnect()
            }
            uiScope.cancel("Activity destroyed")
            Log.d("MainActivity", "onDestroy completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}", e)
        }
    }
}