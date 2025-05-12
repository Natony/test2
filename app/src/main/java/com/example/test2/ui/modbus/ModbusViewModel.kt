package com.example.test2.ui.modbus

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.test2.ModbusConnectionManager
import com.example.test2.ModbusManager
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import android.content.Context

class ModbusViewModel(private val appContext: Context) : ViewModel() {
    private val connectionManager = ModbusConnectionManager.getInstance(appContext)
    private val _modbusData = MutableLiveData<ModbusManager.ModbusResult>()
    val modbusData: LiveData<ModbusManager.ModbusResult> = _modbusData

    init {
        observeConnectionStatus()
    }

    private fun observeConnectionStatus() {
        connectionManager.connectionStatus.observeForever { status ->
            when (status) {
                ModbusConnectionManager.ConnectionStatus.Connected -> {
                    startPolling()
                }
                ModbusConnectionManager.ConnectionStatus.Disconnected,
                ModbusConnectionManager.ConnectionStatus.Error -> {
                    stopPolling()
                }
                else -> { /* Do nothing */ }
            }
        }
    }

    private fun startPolling() {
        val modbusManager = connectionManager.getModbusManager() ?: return
        modbusManager.startPolling { result ->
            _modbusData.postValue(result)
        }
    }

    private fun stopPolling() {
        connectionManager.getModbusManager()?.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}