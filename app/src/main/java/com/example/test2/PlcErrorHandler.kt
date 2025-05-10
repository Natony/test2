package com.example.test2

class PlcErrorHandler {

    /** Mã lỗi PLC (0 = không lỗi) */
    var plcErrorCode: Int = 0
        private set

    /** Trạng thái emergency (PLC lỗi hoặc user bấm nút E-Stop) */
    var emergencyActive: Boolean = false
        private set

    /**
     * Cập nhật mã lỗi mới đọc từ PLC.
     * @param code giá trị đọc được (ví dụ 0x80A3, 0x0000)
     */
    fun updateErrorCode(code: Int) {
        plcErrorCode = code
        emergencyActive = (plcErrorCode != 0) || emergencyActive
    }

    /**
     * Cập nhật khi user bấm hoặc reset E-Stop.
     * @param eStopActive true nếu E-Stop đang active
     */
    fun updateEmergencyStop(eStopActive: Boolean) {
        emergencyActive = emergencyActive || eStopActive
    }

    /**
     * Reset toàn bộ trạng thái lỗi (khi PLC trả về 0 và user đã reset E-Stop).
     */
    fun clear() {
        plcErrorCode = 0
        emergencyActive = false
    }
}