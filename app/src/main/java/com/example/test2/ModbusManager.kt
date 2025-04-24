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
import android.widget.*
import java.net.Socket

class ModbusManager(private val context: Context, val ip: String, val port: Int = 502) {
    @Volatile
    var isBusy = false
        private set

    private var connection: TCPMasterConnection? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    sealed class ModbusResult {
        data class Success(val response: ReadMultipleRegistersResponse) : ModbusResult()
        data class Error(val message: String) : ModbusResult()
    }

    /**
     * Hàm kết nối đến PLC sử dụng Modbus TCP.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("ModbusManager", "Attempting to connect to $ip on port $port")
            connection = TCPMasterConnection(InetAddress.getByName(ip)).apply {
                this.port = this@ModbusManager.port
                connect()
            }
            Log.d("ModbusManager", "Connection status: ${connection?.isConnected}")
            true
        } catch (e: Exception) {
            Log.e("ModbusManager", "Connect failed: ${e.message}", e)
            false
        }
    }

    /**
     * Hàm ngắt kết nối: đóng kết nối và dừng polling.
     */
    fun disconnect() {
        try {
            pollingJob?.cancel("Disconnect called") // Hủy polling job
            pollingJob = null
            connection?.close() // Đóng kết nối
            connection = null
            scope.cancel("ModbusManager disconnected") // Hủy tất cả coroutine trong scope
            Log.d("ModbusManager", "Disconnected successfully")
        } catch (e: Exception) {
            Log.e("ModbusManager", "Error disconnecting: ${e.message}", e)
        } finally {
            isBusy = false // Đảm bảo trạng thái busy được reset
        }
    }


    /**
     * Hàm polling dữ liệu từ PLC. Sau mỗi lần đọc, nếu phát hiện mã lỗi (ví dụ: 8085 ở register 60)
     * thì sẽ gọi onUpdate với kết quả lỗi và dừng polling.
     */
    fun startPolling(
        interval: Long = 500,
        onUpdate: (ModbusResult) -> Unit
    ) {
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val response = readRegisters(0, 64)
                    val errorRegisterIndex = 59
                    if (response.wordCount > errorRegisterIndex) {
                        val errorCode = response.getRegisterValue(errorRegisterIndex)
                        if (errorCode == 8085) {
                            onUpdate(ModbusResult.Error("Device error: $errorCode"))
                            Log.e("ModbusManager", "Device error detected: $errorCode, stopping polling")
                            break
                        }
                    }
                    onUpdate(ModbusResult.Success(response))
                    delay(interval)
                } catch (e: CancellationException) {
                    // Hủy polling một cách có chủ đích, không cần thông báo lỗi
                    Log.d("ModbusManager", "Polling cancelled: ${e.message}")
                    throw e  // Ném lại để coroutine dừng hoàn toàn
                } catch (e: Exception) {
                    Log.e("ModbusManager", "Polling error: ${e.message}", e)
                    onUpdate(ModbusResult.Error("Polling error: ${e.message}"))
                    delay(1000)
                }
            }
            Log.d("ModbusManager", "Polling stopped")
        }
    }


    /**
     * Hàm gửi lệnh write cho một thanh ghi nhất định.
     */
    suspend fun writeCommand(register: Int, value: Int) {
        isBusy = true
        try {
            withContext(Dispatchers.IO) {
                val writeReq = WriteSingleRegisterRequest(register - 1, SimpleRegister(value))
                ModbusTCPTransaction(connection).apply {
                    request = writeReq
                    execute()
                }
            }
        } catch (e: Exception) {
            throw ModbusException("Write failed: ${e.message}")
        } finally {
            isBusy = false
        }
    }

    /**
     * Hàm đọc một thanh ghi (1 từ) theo địa chỉ.
     */
        suspend fun readRegister(register: Int): Int {
            return withContext(Dispatchers.IO) {
                val trans = ModbusTCPTransaction(connection)
                trans.request = ReadMultipleRegistersRequest(register - 1, 1)
                trans.execute()
                val response = trans.response as ReadMultipleRegistersResponse
                response.getRegisterValue(0)
            }
        }

    /**
     * Hàm đọc nhiều thanh ghi.
     */
    private suspend fun readRegisters(startAddress: Int, count: Int): ReadMultipleRegistersResponse {
        return withContext(Dispatchers.IO) {
            val trans = ModbusTCPTransaction(connection)
            trans.request = ReadMultipleRegistersRequest(startAddress, count)
            trans.execute()
            trans.response as ReadMultipleRegistersResponse
        }
    }

    class ModbusException(message: String) : Exception(message)
}
