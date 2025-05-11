package com.example.test2.ui.alarm

import android.content.Context
import android.util.Log
import com.example.test2.ModbusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager class responsible for handling alarms from Modbus
 */
class AlarmManager(
    private val context: Context,
    private val modbusManager: ModbusManager
) {
    // Coroutine scope for alarm operations
    private val alarmScope = CoroutineScope(Dispatchers.Default)

    // State flow for alarm list
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    // Connection status from ModbusManager
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()

    // Alarm register map (register address -> alarm value)
    private val alarmRegisters = mapOf(
        23 to "System Error Register",
        24 to "Temperature Alarm Register",
        25 to "Pressure Alarm Register",
        26 to "Flow Alarm Register",
        27 to "General Alarm Register",
        28 to "Safety Alarm Register"
        // Add more registers as needed
    )

    /**
     * Initialize the alarm manager
     */
    fun initialize() {
        alarmScope.launch {
            try {
                // Connect to Modbus device
                val connected = modbusManager.connect()
                _connectionStatus.value = connected

                if (connected) {
                    Log.d("AlarmManager", "Connected to Modbus device, setting up alarm monitoring...")
                    setupAlarmMonitoring()
                } else {
                    Log.e("AlarmManager", "Failed to connect to Modbus device")
                }
            } catch (e: Exception) {
                Log.e("AlarmManager", "Initialization error: ${e.message}")
            }
        }
    }

    /**
     * Setup monitoring for alarm registers
     */
    private fun setupAlarmMonitoring() {
        // Register callbacks for all alarm registers
        alarmRegisters.keys.forEach { registerAddress ->
            modbusManager.registerPollingCallback(registerAddress) { value ->
                if (value != 0) { // Non-zero value indicates an active alarm
                    processAlarmValue(registerAddress, value)
                } else {
                    clearAlarm(registerAddress)
                }
            }
        }

        // Start polling data from PLC
        modbusManager.startPolling { result ->
            when (result) {
                is ModbusManager.ModbusResult.Success -> {
                    _connectionStatus.value = true
                }
                is ModbusManager.ModbusResult.Error -> {
                    _connectionStatus.value = false
                    Log.e("AlarmManager", "Modbus error: ${result.message}")
                }
            }
        }
    }

    fun pauseConnection() {
        try {
            // Hủy đăng ký callbacks tạm thời
            alarmRegisters.keys.forEach { registerAddress ->
                modbusManager.unregisterPollingCallback(registerAddress)
            }

            // Đóng kết nối Modbus
            modbusManager.disconnect()

            // Cập nhật trạng thái kết nối
            _connectionStatus.value = false

            Log.d("AlarmManager", "Connection paused")
        } catch (e: Exception) {
            Log.e("AlarmManager", "Error during connection pause: ${e.message}")
        }
    }
    /**
     * Process an alarm value from a register
     */
    private fun processAlarmValue(registerAddress: Int, value: Int) {
        // Create alarm from register value if defined
        val alarm = Alarm.fromRegisterValue(registerAddress, value)

        if (alarm != null) {
            // Update alarms list
            _alarms.update { currentList ->
                val existingAlarmIndex = currentList.indexOfFirst { it.id == alarm.id }

                if (existingAlarmIndex >= 0) {
                    // Update existing alarm
                    currentList.toMutableList().apply {
                        this[existingAlarmIndex] = this[existingAlarmIndex].copy(isActive = true)
                    }
                } else {
                    // Add new alarm
                    currentList + alarm
                }
            }
        }
    }

    /**
     * Clear an alarm for a specific register
     */
    private fun clearAlarm(registerAddress: Int) {
        _alarms.update { currentList ->
            currentList.map { alarm ->
                if (alarm.registerAddress == registerAddress) {
                    alarm.copy(isActive = false)
                } else {
                    alarm
                }
            }
        }
    }

    /**
     * Acknowledge specific alarms
     */
    suspend fun acknowledgeAlarms(alarmIds: List<Int>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Write acknowledgment command to PLC if needed
                // Here we're just updating local state for demo

                _alarms.update { currentList ->
                    currentList.map { alarm ->
                        if (alarmIds.contains(alarm.id)) {
                            alarm.copy(isAcknowledged = true, selected = false)
                        } else {
                            alarm
                        }
                    }
                }

                true
            } catch (e: Exception) {
                Log.e("AlarmManager", "Failed to acknowledge alarms: ${e.message}")
                false
            }
        }
    }

    /**
     * Acknowledge all active alarms
     */
    suspend fun acknowledgeAllAlarms(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get all active alarm IDs
                val activeAlarmIds = _alarms.value
                    .filter { it.isActive && !it.isAcknowledged }
                    .map { it.id }

                // Acknowledge each alarm
                acknowledgeAlarms(activeAlarmIds)
            } catch (e: Exception) {
                Log.e("AlarmManager", "Failed to acknowledge all alarms: ${e.message}")
                false
            }
        }
    }

    /**
     * Clear an acknowledged alarm from the list
     */
    fun clearAcknowledgedAlarm(alarmId: Int) {
        _alarms.update { currentList ->
            currentList.filterNot { it.id == alarmId && it.isAcknowledged && !it.isActive }
        }
    }

    /**
     * Cleanup resources when no longer needed
     */
    fun shutdown() {
        try {
            // Unregister all callbacks
            alarmRegisters.keys.forEach { registerAddress ->
                modbusManager.unregisterPollingCallback(registerAddress)
            }

            // Disconnect from Modbus
            modbusManager.disconnect()
        } catch (e: Exception) {
            Log.e("AlarmManager", "Error during shutdown: ${e.message}")
        }
    }
}