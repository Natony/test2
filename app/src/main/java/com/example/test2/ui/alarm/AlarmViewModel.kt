package com.example.test2.ui.alarm

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.test2.ModbusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Alarm screen
 */
class AlarmViewModel(private val context: Context) : ViewModel() {

    // Lấy thông tin từ SharedPreferences thay vì sử dụng địa chỉ cố định
    private val modbusManager: ModbusManager by lazy {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val ip = prefs.getString("modbus_ip", "192.168.1.100") ?: "192.168.1.100"
        val port = prefs.getInt("modbus_port", 502) // 502 là cổng Modbus TCP mặc định

        // Log để debug
        Log.d("AlarmViewModel", "Connecting to Modbus device at IP: $ip, port: $port")

        ModbusManager(context, ip, port) // Giả sử ModbusManager có constructor nhận port
    }

    // Initialize AlarmManager
    private val alarmManager = AlarmManager(context, modbusManager)

    // UI state for selected alarms
    private val _selectedAlarms = MutableStateFlow<Set<Int>>(emptySet())

    // Expose connection status
    val connectionStatus: StateFlow<Boolean> = alarmManager.connectionStatus
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

    // Combined flow of alarms with selection state
    val alarms = combine(
        alarmManager.alarms,
        _selectedAlarms
    ) { alarmList, selectedIds ->
        alarmList.map { alarm ->
            alarm.copy(selected = selectedIds.contains(alarm.id))
        }.sortedWith(
            compareBy<Alarm> { !it.isActive }
                .thenBy { it.isAcknowledged }
                .thenByDescending { it.severity.ordinal }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    /**
     * Initialize alarms monitoring
     */
    fun initializeAlarms() {
        alarmManager.initialize()
    }

    /**
     * Update alarm selection state
     */
    fun updateAlarmSelection(alarmId: Int, isSelected: Boolean) {
        _selectedAlarms.update { selectedAlarms ->
            if (isSelected) {
                selectedAlarms + alarmId
            } else {
                selectedAlarms - alarmId
            }
        }
    }

    /**
     * Acknowledge selected alarms
     */
    fun acknowledgeAlarms(alarmIds: List<Int>) {
        viewModelScope.launch {
            val success = alarmManager.acknowledgeAlarms(alarmIds)
            if (success) {
                // Clear selections after successful acknowledgment
                _selectedAlarms.update { it - alarmIds.toSet() }
            }
        }
    }

    /**
     * Acknowledge all active alarms
     */
    fun acknowledgeAllAlarms() {
        viewModelScope.launch {
            alarmManager.acknowledgeAllAlarms()
        }
    }

    /**
     * Clear an acknowledged alarm
     */
    fun clearAcknowledgedAlarm(alarmId: Int) {
        alarmManager.clearAcknowledgedAlarm(alarmId)
    }

    /**
     * Shutdown the alarm manager
     */
    fun shutdown() {
        alarmManager.shutdown()
    }

    /**
     * Clear ViewModel resources
     */
    override fun onCleared() {
        super.onCleared()
        shutdown()
    }
}

/**
 * Factory for creating AlarmViewModel
 */
class AlarmViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AlarmViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}