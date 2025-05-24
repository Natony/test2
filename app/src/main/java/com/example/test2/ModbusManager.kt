package com.example.test2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import net.wimpi.modbus.io.ModbusTCPTransaction
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse
import net.wimpi.modbus.msg.WriteSingleRegisterRequest
import net.wimpi.modbus.net.TCPMasterConnection
import net.wimpi.modbus.procimg.SimpleRegister
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class ModbusManager(private val context: Context, val ip: String, val port: Int = 502) {
    @Volatile
    var isBusy = false
        private set

    private var connection: TCPMasterConnection? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null

    // Cờ để kiểm tra xem có đang trong quá trình kết nối lại hay không
    private val isReconnecting = AtomicBoolean(false)

    private val pollingCallbacks = mutableMapOf<Int, (Int) -> Unit>()

    // Cấu hình kết nối lại
    private val maxReconnectAttempts = 5
    private val reconnectInterval = 3000L // milliseconds

    // Cờ để kiểm tra xem hệ thống đang hoạt động hay không
    private val shouldBeRunning = AtomicBoolean(false)

    sealed class ModbusResult {
        data class Success(val response: ReadMultipleRegistersResponse) : ModbusResult()
        data class Error(val message: String) : ModbusResult()
    }

    /**
     * Hàm kết nối đến PLC sử dụng Modbus TCP.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (connection?.isConnected == true) {
            Log.d("ModbusManager", "Already connected")
            return@withContext true
        }

        shouldBeRunning.set(true)

        try {
            Log.d("ModbusManager", "Attempting to connect to $ip on port $port")
            connection = TCPMasterConnection(InetAddress.getByName(ip)).apply {
                this.port = this@ModbusManager.port
                connect()
            }
            val isConnected = connection?.isConnected == true
            Log.d("ModbusManager", "Connection status: $isConnected")

            return@withContext isConnected
        } catch (e: Exception) {
            Log.e("ModbusManager", "Connect failed: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Hàm ngắt kết nối: đóng kết nối và dừng polling.
     */
    fun disconnect() {
        try {
            shouldBeRunning.set(false)

            // Hủy công việc kết nối lại
            reconnectJob?.cancel("Disconnect called")
            reconnectJob = null

            // Hủy polling job
            pollingJob?.cancel("Disconnect called")
            pollingJob = null

            // Đóng kết nối
            connection?.close()
            connection = null

            // Hủy tất cả coroutine trong scope
            scope.cancel("ModbusManager disconnected")

            Log.d("ModbusManager", "Disconnected successfully")
        } catch (e: Exception) {
            Log.e("ModbusManager", "Error disconnecting: ${e.message}", e)
        } finally {
            isBusy = false // Đảm bảo trạng thái busy được reset
        }
    }

    /**
     * Hàm bắt đầu polling dữ liệu từ PLC với khả năng tự kết nối lại
     */
    fun startPolling(
        interval: Long = 500,
        onUpdate: (ModbusResult) -> Unit
    ) {
        shouldBeRunning.set(true)

        pollingJob = scope.launch {
            while (isActive && shouldBeRunning.get()) {
                try {
                    // Kiểm tra kết nối trước khi polling
                    if (connection == null || !connection!!.isConnected) {
                        Log.d("ModbusManager", "Connection lost, attempting to reconnect...")
                        val reconnected = startReconnection()
                        if (!reconnected) {
                            onUpdate(ModbusResult.Error("Mất kết nối và không thể kết nối lại"))
                            delay(reconnectInterval)
                            continue
                        }
                    }

                    val response = readRegisters(0, 48)
                    val errorRegisterIndex = 47
                    if (response.wordCount > errorRegisterIndex) {
                        val errorCode = response.getRegisterValue(errorRegisterIndex)
                        if (errorCode == 8085) {
                            onUpdate(ModbusResult.Error("Device error: $errorCode"))
                            Log.e("ModbusManager", "Device error detected: $errorCode, stopping polling")
                            break
                        }
                    }

                    // QUAN TRỌNG: Xử lý các callbacks đăng ký trước khi gọi onUpdate
                    handlePollingResponse(response)

                    onUpdate(ModbusResult.Success(response))
                    delay(interval)
                } catch (e: CancellationException) {
                    // Hủy polling một cách có chủ đích, không cần thông báo lỗi
                    Log.d("ModbusManager", "Polling cancelled: ${e.message}")
                    throw e  // Ném lại để coroutine dừng hoàn toàn
                } catch (e: Exception) {
                    Log.e("ModbusManager", "Polling error: ${e.message}", e)

                    // Cố gắng kết nối lại nếu lỗi là do mất kết nối
                    if (e.message?.contains("connection", ignoreCase = true) == true ||
                        e.message?.contains("socket", ignoreCase = true) == true ||
                        connection == null || !connection!!.isConnected) {

                        onUpdate(ModbusResult.Error("Polling error: ${e.message}. Attempting to reconnect..."))
                        startReconnection()
                    } else {
                        onUpdate(ModbusResult.Error("Polling error: ${e.message}"))
                    }
                    delay(1000)
                }
            }
            Log.d("ModbusManager", "Polling stopped")
        }
    }

    /**
     * Hàm xử lý kết nối lại tự động
     */
    private suspend fun startReconnection(): Boolean {
        if (isReconnecting.getAndSet(true)) {
            // Đã có một quá trình kết nối lại đang diễn ra
            return false
        }

        try {
            // Đóng kết nối cũ nếu còn
            try {
                connection?.close()
                connection = null
            } catch (e: Exception) {
                Log.e("ModbusManager", "Error closing old connection: ${e.message}")
            }

            // Thử kết nối lại
            var attemptCount = 0
            var connected = false

            while (shouldBeRunning.get() && attemptCount < maxReconnectAttempts && !connected) {
                attemptCount++

                Log.d("ModbusManager", "Reconnect attempt $attemptCount of $maxReconnectAttempts")

                connected = connect()

                if (!connected && attemptCount < maxReconnectAttempts) {
                    delay(reconnectInterval) // Chờ trước khi thử lại
                }
            }

            if (connected) {
                Log.d("ModbusManager", "Reconnected successfully after $attemptCount attempts")
                return true
            } else {
                Log.e("ModbusManager", "Failed to reconnect after $maxReconnectAttempts attempts")
                return false
            }
        } finally {
            isReconnecting.set(false)
        }
    }

    /**
     * Hàm gửi lệnh write cho một thanh ghi nhất định.
     */
    suspend fun writeCommand(register: Int, value: Int): Boolean {
        isBusy = true
        try {
            // Kiểm tra kết nối trước khi gửi lệnh
            if (connection == null || !connection!!.isConnected) {
                Log.d("ModbusManager", "Connection lost, attempting to reconnect before writing...")
                val reconnected = startReconnection()
                if (!reconnected) {
                    Log.e("ModbusManager", "Cannot write register: no connection")
                    return false
                }
            }

            withContext(Dispatchers.IO) {
                val writeReq = WriteSingleRegisterRequest(register - 1, SimpleRegister(value))
                ModbusTCPTransaction(connection).apply {
                    request = writeReq
                    execute()
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("ModbusManager", "Write failed: ${e.message}", e)

            // Cố gắng kết nối lại nếu lỗi là do mất kết nối
            if (e.message?.contains("connection", ignoreCase = true) == true ||
                e.message?.contains("socket", ignoreCase = true) == true) {
                startReconnection()
            }

            return false
        } finally {
            isBusy = false
        }
    }

    /**
     * Hàm đọc một thanh ghi (1 từ) theo địa chỉ.
     */
    suspend fun readRegister(register: Int): Int? {
        try {
            // Kiểm tra kết nối trước khi đọc
            if (connection == null || !connection!!.isConnected) {
                Log.d("ModbusManager", "Connection lost, attempting to reconnect before reading...")
                val reconnected = startReconnection()
                if (!reconnected) {
                    Log.e("ModbusManager", "Cannot read register: no connection")
                    return null
                }
            }

            return withContext(Dispatchers.IO) {
                val trans = ModbusTCPTransaction(connection)
                trans.request = ReadMultipleRegistersRequest(register - 1, 1)
                trans.execute()
                val response = trans.response as ReadMultipleRegistersResponse
                response.getRegisterValue(0)
            }
        } catch (e: Exception) {
            Log.e("ModbusManager", "Read failed: ${e.message}", e)

            // Cố gắng kết nối lại nếu lỗi là do mất kết nối
            if (e.message?.contains("connection", ignoreCase = true) == true ||
                e.message?.contains("socket", ignoreCase = true) == true) {
                startReconnection()
            }

            return null
        }
    }

    /**
     * Hàm đọc nhiều thanh ghi.
     */
    private suspend fun readRegisters(startAddress: Int, count: Int): ReadMultipleRegistersResponse {
        // Kiểm tra kết nối trước khi đọc
        if (connection == null || !connection!!.isConnected) {
            Log.d("ModbusManager", "Connection lost, attempting to reconnect before reading registers...")
            val reconnected = startReconnection()
            if (!reconnected) {
                throw ModbusException("Cannot read registers: no connection")
            }
        }

        return withContext(Dispatchers.IO) {
            val trans = ModbusTCPTransaction(connection)
            trans.request = ReadMultipleRegistersRequest(startAddress, count)
            trans.execute()
            trans.response as ReadMultipleRegistersResponse
        }
    }

    fun registerPollingCallback(address: Int, callback: (Int) -> Unit) {
        Log.d("ModbusManager", "Registering callback for address: $address")
        pollingCallbacks[address] = callback
    }

    fun unregisterPollingCallback(address: Int) {
        Log.d("ModbusManager", "Unregistering callback for address: $address")
        pollingCallbacks.remove(address)
    }

    private fun handlePollingResponse(response: ReadMultipleRegistersResponse) {
        Log.d("ModbusManager", "Handling polling response, callbacks count: ${pollingCallbacks.size}")
        pollingCallbacks.forEach { (address, callback) ->
            try {
                // Kiểm tra nếu address nằm trong phạm vi của response
                if (address > 0 && address <= response.wordCount) {
                    val value = response.getRegisterValue(address - 1)
                    Log.d("ModbusManager", "Callback for address $address with value $value")
                    callback(value)
                } else {
                    Log.w("ModbusManager", "Address $address out of range (max: ${response.wordCount})")
                }
            } catch (e: Exception) {
                Log.e("ModbusManager", "Error processing address $address: ${e.message}")
            }
        }
    }

    class ModbusException(message: String) : Exception(message)
}