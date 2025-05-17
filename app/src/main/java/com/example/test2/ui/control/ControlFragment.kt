package com.example.test2.ui.control

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.test2.AppConfigStatus
import com.example.test2.ButtonStateHandler
import com.example.test2.MainActivity
import com.example.test2.ModbusCommand
import com.example.test2.ModbusConnectionManager
import com.example.test2.ModbusManager
import com.example.test2.OperationStatusManager
import com.example.test2.ui.modbus.ModbusViewModel
import com.example.test2.PalletCommandHandler
import com.example.test2.PlcErrorHandler
import com.example.test2.R
import com.example.test2.ShuttleFunctionHandler
import com.example.test2.ShuttlePositionIndicator
import com.example.test2.rules.LockRuleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse

class ControlFragment : Fragment() {

    // region: Views
    private lateinit var tvNameDevice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvOperationStatus: TextView

    private lateinit var btnPower: ImageButton
    private lateinit var btnBuzzer: ImageButton
    private lateinit var btnPosition: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnEmergencyStop: ImageButton
    private lateinit var btnCountPallet: ImageButton

    private lateinit var layoutAutoLeft: LinearLayout
    private lateinit var layoutManualLeft: LinearLayout
    private lateinit var layoutAutoRight: LinearLayout
    private lateinit var layoutManualRight: LinearLayout
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

    private lateinit var icBattery: ImageView
    private lateinit var tvBattery: TextView

    private lateinit var spinnerFunctions: Spinner
    private lateinit var etStartX: EditText
    private lateinit var etStartY: EditText
    private lateinit var etStartZ: EditText
    private lateinit var etEndX: EditText
    private lateinit var etEndY: EditText
    private lateinit var etEndZ: EditText
    private lateinit var tvActualX: TextView
    private lateinit var tvActualY: TextView
    private lateinit var tvActualZ: TextView
    private lateinit var btnRunFunction: Button

    // Reference to the shared connection manager
    private lateinit var connectionManager: ModbusConnectionManager
    private var modbusManager: ModbusManager? = null

    private lateinit var palletHandler: PalletCommandHandler
    private lateinit var shuttleIndicator: ShuttlePositionIndicator
    private val errorHandler = PlcErrorHandler()
    private lateinit var statusManager: OperationStatusManager
    private lateinit var modbusViewModel: ModbusViewModel

    private val buttonLockStates = mutableMapOf<ModbusCommand, Boolean>()
    private val commandToButtonMap = mutableMapOf<ModbusCommand, ImageButton>()
    private var pollingJob: Job? = null
    private var isConnected = false

    private lateinit var shuttleFunctionHandler: ShuttleFunctionHandler

    private var isIndicatorInitialized = false


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
        ModbusCommand.BUZZER to ButtonState(ModbusCommand.BUZZER, activeResId = R.drawable.ic_buzzer_on, inactiveResId = R.drawable.ic_buzzer_off),
        ModbusCommand.DIRECTION to ButtonState(ModbusCommand.DIRECTION, activeResId = R.drawable.ic_direction_a, inactiveResId = R.drawable.ic_direction_b),
        ModbusCommand.MODE to ButtonState(ModbusCommand.MODE, activeResId = R.drawable.ic_mode_auto, inactiveResId = R.drawable.ic_mode_manual),
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

        // Lấy modbusViewModel từ MainActivity
        modbusViewModel = (requireActivity() as MainActivity).modbusViewModel

        // Khởi handler
        handler = ButtonStateHandler(viewLifecycleOwner.lifecycleScope)


        bindViews(view)
        setupUI()

        // Initialize ShuttleFunctionHandler
        shuttleFunctionHandler = ShuttleFunctionHandler(
            fragment = this,
            modbusManager = null,
            canExecuteCommandCheck = ::canExecuteCommand
        )

        shuttleFunctionHandler.initialize(
            spinnerFunctions,
            etStartX, etStartY, etStartZ,
            etEndX, etEndY, etEndZ,
            tvActualX, tvActualY, tvActualZ,
            btnRunFunction
        )

        // Observe dữ liệu từ ViewModel
        modbusViewModel.modbusData.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is ModbusManager.ModbusResult.Success -> {
                    updateUI(result.response)
                }
                is ModbusManager.ModbusResult.Error -> {
                    showToast(result.message)
                }
            }
        })

        connectionManager.connectionStatus.observe(viewLifecycleOwner, Observer { status ->
            when (status) {
                ModbusConnectionManager.ConnectionStatus.Connected -> {
                    modbusManager = connectionManager.getModbusManager()
                    updateConnectionUI(true)
                    initModbusAndIndicator(view)
                    isIndicatorInitialized = true
                    isConnected = true
                    setupListeners()
                    setupBatteryMonitor()
                    shuttleIndicator.startMonitoring()
                    shuttleFunctionHandler.setModbusManager(modbusManager)
                }
                else -> {
                    modbusManager = null
                    isConnected = false
                    isIndicatorInitialized = true
                    updateConnectionUI(false)
                }
            }
        })

        connectionManager.currentDevice.observe(viewLifecycleOwner, Observer { device ->
            device?.let { tvNameDevice.text = it.name }
        })

//        setupRunButton()
    }

    private fun bindViews(root: View) {
        tvNameDevice      = root.findViewById(R.id.tvNameDevice)
        tvStatus          = root.findViewById(R.id.tvStatus)

        btnPower          = root.findViewById(R.id.btnPower)
        btnBuzzer         = root.findViewById(R.id.btnBuzzer)
        btnPosition       = root.findViewById(R.id.btnPosition)
        btnMode           = root.findViewById(R.id.btnMode)
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

        tvOperationStatus = requireActivity().findViewById(R.id.tvOperationStatus)

        icBattery = requireActivity().findViewById(R.id.icBattery)
        tvBattery = requireActivity().findViewById(R.id.tvBattery)

        spinnerFunctions = root.findViewById(R.id.spinnerFunctions)
        etStartX = root.findViewById(R.id.etStartX)
        etStartY = root.findViewById(R.id.etStartY)
        etStartZ = root.findViewById(R.id.etStartZ)
        etEndX = root.findViewById(R.id.etEndX)
        etEndY = root.findViewById(R.id.etEndY)
        etEndZ = root.findViewById(R.id.etEndZ)
        tvActualX = root.findViewById(R.id.tvActualX)
        tvActualY = root.findViewById(R.id.tvActualY)
        tvActualZ = root.findViewById(R.id.tvActualZ)
        btnRunFunction = root.findViewById(R.id.btnRunFunction)

        // Map commands → buttons
        listOf(
            btnPower to ModbusCommand.POWER,
            btnBuzzer to ModbusCommand.BUZZER,
            btnPosition to ModbusCommand.DIRECTION,
            btnMode to ModbusCommand.MODE,
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

        statusManager =
            OperationStatusManager(modbusManager!!, AppConfigStatus.operationStatusConfig)
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
            modbusManager = connectionManager.getModbusManager()!!,
            lifecycleOwner = viewLifecycleOwner,
            icons = icons,
            onRes = onRes,
            offRes = offRes
        )

        isIndicatorInitialized = true
    }

    private fun updateBatteryUI(batteryLevel: Int) {
        // Update the battery icon based on level
        val batteryIcon = when {
            batteryLevel > 80 -> R.drawable.ic_battery_full
            batteryLevel > 20 -> R.drawable.ic_battery_medium
            else -> R.drawable.ic_battery_low
        }

        // Update the UI
        icBattery.setImageResource(batteryIcon)
        tvBattery.text = "$batteryLevel%"
    }

    private fun setupBatteryMonitor() {
        val modbus = modbusManager ?: return

        // Register a callback for the battery address
        modbus.registerPollingCallback(ModbusCommand.BATTERY.address) { batteryLevel ->
            // Update on the main thread
            lifecycleScope.launch(Dispatchers.Main) {
                updateBatteryUI(batteryLevel)
            }
        }
    }

    // Call this method in onViewCreated or when connection is established
    private fun initBatteryMonitor() {
        if (isConnected && modbusManager != null) {
            setupBatteryMonitor()
        }
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

    private fun updateUI(response: ReadMultipleRegistersResponse) {
        // Cập nhật toggle states
        buttonStates.forEach { (cmd, st) ->
            st.isActive = response.getRegisterValue(cmd.address - 1) == 1
            updateButtonUI(cmd)
        }
        // Cross-lock
        handler.applyCrossLocking(response, buttonLockStates)
        updateButtonEnableStates()
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
        layoutAutoLeft.visibility   = if (auto) View.VISIBLE else View.INVISIBLE
        layoutManualLeft.visibility = if (!auto) View.VISIBLE else View.INVISIBLE
        layoutAutoRight.visibility  = if (auto) View.VISIBLE else View.INVISIBLE
        layoutManualRight.visibility= if (!auto) View.VISIBLE else View.INVISIBLE

        btnCountPallet.visibility = if(!auto) View.VISIBLE else View.INVISIBLE
    }

    private fun validateCoordinates(): Boolean {
        val startX = etStartX.text.toString().trim()
        val startY = etStartY.text.toString().trim()
        val startZ = etStartZ.text.toString().trim()
        val endX = etEndX.text.toString().trim()
        val endY = etEndY.text.toString().trim()
        val endZ = etEndZ.text.toString().trim()

        if (startX.isEmpty() || startY.isEmpty() || startZ.isEmpty() ||
            endX.isEmpty() || endY.isEmpty() || endZ.isEmpty()) {
            return false
        }

        try {
            startX.toInt()
            startY.toInt()
            startZ.toInt()
            endX.toInt()
            endY.toInt()
            endZ.toInt()
        } catch (e: NumberFormatException) {
            return false
        }

        return true
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
        isIndicatorInitialized = false
        shuttleIndicator.stopMonitoring()
        // Do not disconnect here as we're using the shared connection manager
        // It will manage the connection lifecycle for the entire app
    }
}