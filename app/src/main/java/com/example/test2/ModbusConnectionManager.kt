package com.example.test2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.test2.ui.config.ConfigItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton class to manage the Modbus connection across the application.
 * This provides a centralized way to handle PLC connections and can be used by any fragment.
 */
class ModbusConnectionManager private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "ModbusConnectionManager"
        private var instance: ModbusConnectionManager? = null

        fun getInstance(context: Context): ModbusConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: ModbusConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // Shared preferences for storing connection info
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    }

    // The actual ModbusManager that handles communication
    private var modbusManager: ModbusManager? = null

    // LiveData to observe connection status changes
    private val _connectionStatus = MutableLiveData<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

    // LiveData for the currently selected device
    private val _currentDevice = MutableLiveData<ConfigItem?>()
    val currentDevice: LiveData<ConfigItem?> = _currentDevice

    // Interface for notifying state changes
    interface ConnectionStatusListener {
        fun onStatusChange(status: ConnectionStatus)
    }

    enum class ConnectionStatus {
        Connected,
        Connecting,
        Disconnected,
        Error
    }

    /**
     * Connect to a device using the stored connection parameters
     */
    fun connectToSelectedDevice(scope: CoroutineScope) {
        val deviceName = prefs.getString("plc_name", null) ?: return
        val ip = prefs.getString("modbus_ip", null) ?: return
        val port = prefs.getInt("modbus_port", 502)

        connectToDevice(ConfigItem(deviceName, ip, port), scope)
    }

    /**
     * Connect to a specific device
     */
    fun connectToDevice(device: ConfigItem, scope: CoroutineScope) {
        if (_connectionStatus.value == ConnectionStatus.Connecting) {
            Log.d(TAG, "Already attempting to connect")
            return
        }

        // Store the selected device details in SharedPreferences
        prefs.edit()
            .putString("modbus_ip", device.ipAddress)
            .putInt("modbus_port", device.port)
            .putString("plc_name", device.name)
            .apply()

        // Update current device
        _currentDevice.value = device
        _connectionStatus.value = ConnectionStatus.Connecting

        // Close any existing connection
        disconnectCurrent()

        // Create and connect the new ModbusManager
        modbusManager = ModbusManager(appContext, device.ipAddress, device.port)

        scope.launch {
            try {
                val connected = modbusManager?.connect() ?: false
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = if (connected) {
                        Log.d(TAG, "Successfully connected to ${device.name}")
                        ConnectionStatus.Connected
                    } else {
                        Log.e(TAG, "Failed to connect to ${device.name}")
                        ConnectionStatus.Error
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = ConnectionStatus.Error
                }
            }
        }
    }

    /**
     * Get the current ModbusManager instance if connected
     */
    fun getModbusManager(): ModbusManager? {
        return if (_connectionStatus.value == ConnectionStatus.Connected) modbusManager else null
    }

    /**
     * Disconnect the current connection
     */
    fun disconnectCurrent() {
        modbusManager?.disconnect()
        modbusManager = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    /**
     * Check if we are currently connected
     */
    fun isConnected(): Boolean {
        return _connectionStatus.value == ConnectionStatus.Connected
    }
}