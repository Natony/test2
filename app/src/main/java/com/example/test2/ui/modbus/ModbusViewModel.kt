package com.example.test2.ui.modbus

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.test2.ModbusConnectionManager
import com.example.test2.ModbusManager
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import android.content.Context
import com.example.test2.AppConfigStatus
import com.example.test2.ModbusCommand
import com.example.test2.OperationStatus
import com.example.test2.OperationStatusManager

class ModbusViewModel(private val appContext: Context) : ViewModel() {
    private val connectionManager = ModbusConnectionManager.getInstance(appContext)
    private val _modbusData = MutableLiveData<ModbusManager.ModbusResult>()
    val modbusData: LiveData<ModbusManager.ModbusResult> = _modbusData

    private val _batteryLevel = MutableLiveData<Int>()
    val batteryLevel: LiveData<Int> = _batteryLevel

    // Thêm LiveData cho trạng thái hoạt động
    private val _operationStatus = MutableLiveData<OperationStatus>()
    val operationStatus: LiveData<OperationStatus> = _operationStatus

    private var operationStatusManager: OperationStatusManager? = null

    init {
        observeConnectionStatus()
    }


    private fun observeConnectionStatus() {
        connectionManager.connectionStatus.observeForever { status ->
            when (status) {
                ModbusConnectionManager.ConnectionStatus.Connected -> {
                    startPolling()
                    initOperationStatusManager()
                }

                ModbusConnectionManager.ConnectionStatus.Disconnected,
                ModbusConnectionManager.ConnectionStatus.Error -> {
                    stopPolling()
                }
                else -> { /* Do nothing */ }
            }
        }
    }

    private fun initOperationStatusManager() {
        val modbusManager = connectionManager.getModbusManager() ?: return

        operationStatusManager = OperationStatusManager(
            modbusManager,
            AppConfigStatus.operationStatusConfig
        ).apply {
            // Cài đặt callback để cập nhật LiveData khi có thay đổi trạng thái
            setStatusUpdateCallback { status ->
                _operationStatus.postValue(status)
            }
            startMonitoring()
        }
    }

    private fun startPolling() {
        val modbusManager = connectionManager.getModbusManager() ?: return
        modbusManager.registerPollingCallback(ModbusCommand.BATTERY.address) { level ->
            _batteryLevel.postValue(level)
        }

        modbusManager.startPolling { result ->
            _modbusData.postValue(result)
        }

    }

    private fun stopPolling() {
        connectionManager.getModbusManager()?.disconnect()
        operationStatusManager?.stopMonitoring()

    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}