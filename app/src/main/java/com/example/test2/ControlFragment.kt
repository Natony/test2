package com.example.test2.ui.control

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import com.example.test2.R
import com.example.test2.ModbusManager
import com.example.test2.ModbusConnectionManager
import com.example.test2.PalletCommandHandler
import com.example.test2.PlcErrorHandler
import com.example.test2.ShuttlePositionIndicator
import com.example.test2.ButtonStateHandler
import com.example.test2.ModbusCommand
import com.example.test2.OperationStatusManager
import com.example.test2.AppConfigStatus
import com.example.test2.rules.LockRuleConfig
import kotlinx.coroutines.*

class ControlFragment : Fragment() {

    // region: Views
    private lateinit var tvNameDevice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvOperationStatus: TextView

    private lateinit var btnPower: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnBuzzer: ImageButton
    private lateinit var btnPosition: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnHandlingMode: ImageButton
    private lateinit var btnEmergencyStop: ImageButton
    private lateinit var btnCountPallet: ImageButton

    private lateinit var layoutAutoLeft: LinearLayout
    private lateinit var layoutManualLeft: LinearLayout
    private lateinit var layoutAutoRight: LinearLayout
    private lateinit var layoutManualRight: LinearLayout
    private lateinit var textCountPallet: TextView
    private lateinit var btnPickPallets: ImageButton
    private lateinit var btnPickPallet: ImageButton
    private lateinit var btnStackA: ImageButton
    private lateinit var btnTakePallets: ImageButton
    private lateinit var btnTakePallet: ImageButton
    private lateinit var btnStackB: ImageButton
    private lateinit var btnManualForward: ImageButton
    private lateinit var btnManualReverse: ImageButton
    private lateinit var btnManualUp: ImageButton
    private lateinit var btnManualDown: ImageButton
    // endregion

    // Reference to the shared connection manager
    private lateinit var connectionManager: ModbusConnectionManager
    private var modbusManager: ModbusManager? = null

    private lateinit var palletHandler: PalletCommandHandler
    private lateinit var shuttleIndicator: ShuttlePositionIndicator
    private val errorHandler = PlcErrorHandler()
    private lateinit var statusManager: OperationStatusManager

    private val buttonLockStates = mutableMapOf<ModbusCommand, Boolean>()
    private val commandToButtonMap = mutableMapOf<ModbusCommand, ImageButton>()
    private var pollingJob: Job? = null
    private var isConnected = false

    // Tạo instance của handler với lifecycleScope
    private lateinit var handler: ButtonStateHandler

    private data class ButtonState(
        val command: ModbusCommand,
        var isActive: Boolean = false,
        val activeResId: Int,
        val inactiveResId: Int
    )

    // Khai báo trạng thái các toggle button
    private val buttonStates = mapOf(
        ModbusCommand.POWER to ButtonState(ModbusCommand.POWER, activeResId = R.drawable.ic_power_on, inactiveResId = R.drawable.ic_power_off),
        ModbusCommand.LOCK to ButtonState(ModbusCommand.LOCK, activeResId = R.drawable.ic_lock_closed, inactiveResId = R.drawable.ic_lock_open),
        ModbusCommand.BUZZER to ButtonState(ModbusCommand.BUZZER, activeResId = R.drawable.ic_buzzer_on, inactiveResId = R.drawable.ic_buzzer_off),
        ModbusCommand.DIRECTION to ButtonState(ModbusCommand.DIRECTION, activeResId = R.drawable.ic_direction_a, inactiveResId = R.drawable.ic_direction_b),
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
            = inflater.inflate(R.layout.activity_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the shared connection manager
        connectionManager = ModbusConnectionManager.getInstance(requireContext())

        // Khởi handler
        handler = ButtonStateHandler(viewLifecycleOwner.lifecycleScope)

        // Khởi dynamic rules từ class riêng
        handler.dynamicRules.addAll(LockRuleConfig.getRules())

        bindViews(view)
        setupUI()

        // Observe connection changes
        connectionManager.connectionStatus.observe(viewLifecycleOwner, Observer { status ->
            when (status) {
                ModbusConnectionManager.ConnectionStatus.Connected -> {
                    modbusManager = connectionManager.getModbusManager()
                    isConnected = true
                    updateConnectionUI(true)
                    initModbusAndIndicator(view)
                    setupListeners()
                    startPolling()
                    toggleUIMode()
                }
                ModbusConnectionManager.ConnectionStatus.Disconnected,
                ModbusConnectionManager.ConnectionStatus.Error -> {
                    modbusManager = null
                    isConnected = false
                    updateConnectionUI(false)
                    pollingJob?.cancel()
                }
                else -> { /* Do nothing for other states */ }
            }
        })

        // Current device info
        connectionManager.currentDevice.observe(viewLifecycleOwner, Observer { device ->
            device?.let {
                tvNameDevice.text = it.name
            }
        })

        // Try to use existing connection or establish a new one
        if (connectionManager.isConnected()) {
            modbusManager = connectionManager.getModbusManager()
            isConnected = true
            updateConnectionUI(true)
            initModbusAndIndicator(view)
            setupListeners()
            startPolling()
            toggleUIMode()
        } else {
            // Try to connect to the last selected device
            connectionManager.connectToSelectedDevice(lifecycleScope)
        }
    }

    private fun bindViews(root: View) {
        tvNameDevice      = root.findViewById(R.id.tvNameDevice)
        tvStatus          = root.findViewById(R.id.tvStatus)

        btnPower          = root.findViewById(R.id.btnPower)
        btnLock           = root.findViewById(R.id.btnLock)
        btnBuzzer         = root.findViewById(R.id.btnBuzzer)
        btnPosition       = root.findViewById(R.id.btnPosition)
        btnMode           = root.findViewById(R.id.btnMode)
        btnHandlingMode   = root.findViewById(R.id.btnHandlingMode)
        btnEmergencyStop  = root.findViewById(R.id.btnEmergencyStop)
        btnCountPallet    = root.findViewById(R.id.btnCountPallet)

        btnPickPallets    = root.findViewById(R.id.btnPickPallets)
        btnPickPallet     = root.findViewById(R.id.btnPickPallet)
        btnStackA         = root.findViewById(R.id.btnStackA)
        btnTakePallets    = root.findViewById(R.id.btnTakePallets)
        btnTakePallet     = root.findViewById(R.id.btnTakePallet)
        btnStackB         = root.findViewById(R.id.btnStackB)

        btnManualForward  = root.findViewById(R.id.btnManualForward)
        btnManualReverse  = root.findViewById(R.id.btnManualReverse)
        btnManualUp       = root.findViewById(R.id.btnManualUp)
        btnManualDown     = root.findViewById(R.id.btnManualDown)

        layoutAutoLeft    = root.findViewById(R.id.layoutAutoLeft)
        layoutManualLeft  = root.findViewById(R.id.layoutManualLeft)
        layoutAutoRight   = root.findViewById(R.id.layoutAutoRight)
        layoutManualRight = root.findViewById(R.id.layoutManualRight)
        textCountPallet = root.findViewById(R.id.textCountPallet)

        tvOperationStatus = root.findViewById(R.id.tvOperationStatus)

        // Map commands → buttons
        listOf(
            btnPower to ModbusCommand.POWER,
            btnLock  to ModbusCommand.LOCK,
            btnBuzzer to ModbusCommand.BUZZER,
            btnPosition to ModbusCommand.DIRECTION,
            btnMode to ModbusCommand.MODE,
            btnHandlingMode to ModbusCommand.HANDLING,
            btnEmergencyStop to ModbusCommand.EMERGENCY_STOP,
            btnCountPallet to ModbusCommand.COUNT_PALLET,
            btnPickPallets to ModbusCommand.PICK_PALLETS,
            btnPickPallet to ModbusCommand.PICK_PALLET,
            btnStackA to ModbusCommand.STACK_A,
            btnTakePallets to ModbusCommand.TAKE_PALLETS,
            btnTakePallet to ModbusCommand.TAKE_PALLET,
            btnStackB to ModbusCommand.STACK_B,
            btnManualForward to ModbusCommand.FORWARD,
            btnManualReverse to ModbusCommand.REVERSE,
            btnManualUp to ModbusCommand.UP,
            btnManualDown to ModbusCommand.DOWN
        ).forEach { (btn, cmd) ->
            commandToButtonMap[cmd] = btn
            buttonLockStates[cmd] = false
        }

        initButtonLockStates()
    }

    private fun initModbusAndIndicator(root: View) {
        // Use the ModbusManager from the connection manager
        modbusManager = connectionManager.getModbusManager() ?: return

        palletHandler = PalletCommandHandler(
            requireContext(),
            modbusManager!!,
            ::canExecuteCommand
        )

        statusManager = OperationStatusManager(modbusManager!!, AppConfigStatus.operationStatusConfig)
        statusManager.bindTextView(tvOperationStatus)
        statusManager.startMonitoring()

        // Danh sách 13 vị trí
        val icons = listOf(
            root.findViewById<ImageView>(R.id.pos1),
            root.findViewById(R.id.pos2),
            root.findViewById(R.id.pos3),
            root.findViewById(R.id.pos4),
            root.findViewById(R.id.pos5),
            root.findViewById(R.id.pos6),
            root.findViewById(R.id.pos7),
            root.findViewById(R.id.pos8),
            root.findViewById(R.id.pos9),
            root.findViewById(R.id.pos10),
            root.findViewById(R.id.pos11),
            root.findViewById(R.id.pos12),
            root.findViewById(R.id.pos13)
        )
        val onRes = listOf(
            R.drawable.ic_pos1_on,
            R.drawable.ic_pos2_on,
            R.drawable.ic_pos3_on,
            R.drawable.ic_pos4_on,
            R.drawable.ic_pos5_on,
            R.drawable.ic_pos6_on,
            R.drawable.ic_pos7_on,
            R.drawable.ic_pos8_on,
            R.drawable.ic_pos9_on,
            R.drawable.ic_pos10_on,
            R.drawable.ic_pos11_on,
            R.drawable.ic_pos12_on,
            R.drawable.ic_pos13_on
        )
        val offRes = listOf(
            R.drawable.ic_pos1_off,
            R.drawable.ic_pos2_off,
            R.drawable.ic_pos3_off,
            R.drawable.ic_pos4_off,
            R.drawable.ic_pos5_off,
            R.drawable.ic_pos6_off,
            R.drawable.ic_pos7_off,
            R.drawable.ic_pos8_off,
            R.drawable.ic_pos9_off,
            R.drawable.ic_pos10_off,
            R.drawable.ic_pos11_off,
            R.drawable.ic_pos12_off,
            R.drawable.ic_pos13_off
        )

        shuttleIndicator = ShuttlePositionIndicator(
            icons,
            onRes,
            offRes,
            root.findViewById(R.id.ivShuttle),
            root.findViewById(R.id.vBelt)
        )
    }

    private fun setupUI() {
        // Get device name from connection manager if available
        connectionManager.currentDevice.value?.let {
            tvNameDevice.text = it.name
        } ?: run {
            // Fallback to SharedPreferences
            val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            tvNameDevice.text = prefs.getString("plc_name", "PLC")
        }

        updateConnectionUI(connectionManager.isConnected())
    }

    private fun setupListeners() {
        // Skip if ModbusManager is not available
        val modbus = modbusManager ?: return

        // Toggle buttons (all except momentary)
        commandToButtonMap.filterKeys {
            it !in listOf(ModbusCommand.FORWARD, ModbusCommand.REVERSE, ModbusCommand.UP, ModbusCommand.DOWN)
        }.forEach { (cmd, btn) ->
            handler.setupToggleButton(
                button            = btn,
                command           = cmd,
                modbusManager     = modbus,
                getCurrentState   = { buttonStates[cmd]?.isActive ?: false },
                updateButtonUI    = { c, active ->
                    buttonStates[c]?.isActive = active
                    updateButtonUI(c)
                },
                showToast         = ::showToast,
                canExecuteCommand = ::canExecuteCommand,
                context           = requireContext(),
                confirmMessage    = "Bạn có chắc muốn thực hiện ${cmd.name.toLowerCase().replace('_', ' ')} không?"
            )
        }

        // Momentary buttons
        listOf(ModbusCommand.FORWARD, ModbusCommand.REVERSE, ModbusCommand.UP, ModbusCommand.DOWN).forEach { cmd ->
            handler.setupMomentaryButton(
                button         = commandToButtonMap[cmd]!!,
                command        = cmd,
                modbusManager  = modbus,
                updateButtonUI = { c, active ->
                    buttonStates[c]?.isActive = active
                    updateButtonUI(c)
                },
                showToast      = ::showToast,
                canExecuteCommand = ::canExecuteCommand
            )
        }

        palletHandler.setupPalletButton(btnPickPallets, ModbusCommand.PICK_PALLETS)
        palletHandler.setupPalletButton(btnTakePallets, ModbusCommand.TAKE_PALLETS)

        btnMode.setOnClickListener {
            handleCommand(ModbusCommand.MODE)
            toggleUIMode()
        }
    }

    private fun startPolling() {
        val modbus = modbusManager ?: return

        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch(Dispatchers.Main) {
            Log.d("ControlFragment", "Starting polling with operation status address: ${ModbusCommand.OPERATION_STATUS.address}")

            modbus.startPolling { result ->
                when (result) {
                    is ModbusManager.ModbusResult.Success -> {
                        requireActivity().runOnUiThread {
                            updateUI(result.response)

                            // Thêm log để debug
                            try {
                                val statusValue = result.response.getRegisterValue(ModbusCommand.OPERATION_STATUS.address - 1)
                                Log.d("ControlFragment", "Operation Status Register Value: $statusValue")
                            } catch (e: Exception) {
                                Log.e("ControlFragment", "Error reading operation status: ${e.message}")
                            }
                        }
                    }
                    is ModbusManager.ModbusResult.Error ->
                        requireActivity().runOnUiThread { showToast(result.message) }
                }
            }
        }
    }

    private fun updateUI(response: ReadMultipleRegistersResponse) {
        // Cập nhật toggle states
        buttonStates.forEach { (cmd, st) ->
            st.isActive = response.getRegisterValue(cmd.address - 1) == 1
            updateButtonUI(cmd)
        }
        // Cross-lock
        handler.applyCrossLocking(response, buttonLockStates)
        updateButtonEnableStates()
        // Shuttle indicator
        val pos = response.getRegisterValue(ModbusCommand.LOCATION.address -1).coerceIn(1,13)
        shuttleIndicator.update(pos)
        toggleUIMode()
    }

    private fun initButtonLockStates() {
        ModbusCommand.values().forEach { buttonLockStates[it] = false }
    }

    private fun updateButtonEnableStates() {
        buttonLockStates.forEach { (cmd, locked) ->
            commandToButtonMap[cmd]?.isEnabled = !locked
        }
    }

    private fun updateButtonUI(command: ModbusCommand) {
        val st = buttonStates[command] ?: return
        val btn = commandToButtonMap[command] ?: return
        btn.setImageResource(if (st.isActive) st.activeResId else st.inactiveResId)
    }

    private fun updateConnectionUI(connected: Boolean) {
        tvStatus.text = if (connected) "Đã kết nối" else "Chưa kết nối"
    }

    private fun toggleUIMode() {
        val auto = buttonStates[ModbusCommand.MODE]?.isActive ?: true
        layoutAutoLeft.visibility   = if (auto) View.VISIBLE else View.GONE
        layoutManualLeft.visibility = if (!auto) View.VISIBLE else View.GONE
        layoutAutoRight.visibility  = if (auto) View.VISIBLE else View.GONE
        layoutManualRight.visibility= if (!auto) View.VISIBLE else View.GONE

        btnCountPallet.visibility = if(auto) View.VISIBLE else View.GONE
        textCountPallet.visibility = if (auto) View.VISIBLE else View.GONE
    }

    private fun handleCommand(cmd: ModbusCommand) {
        val modbus = modbusManager ?: return

        if (!canExecuteCommand()) {
            showToast("Đang xử lý lệnh khác…")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = buttonStates[cmd]!!
                val newVal = if (state.isActive) 0 else 1
                modbus.writeCommand(cmd.address, newVal)
                withContext(Dispatchers.Main) {
                    state.isActive = !state.isActive
                    updateButtonUI(cmd)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Lỗi: ${e.message}")
                }
            }
        }
    }

    private fun canExecuteCommand(): Boolean {
        val modbus = modbusManager ?: return false
        return isConnected && !modbus.isBusy
    }

    private fun showToast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()
        // Do not disconnect here as we're using the shared connection manager
        // It will manage the connection lifecycle for the entire app
    }
}