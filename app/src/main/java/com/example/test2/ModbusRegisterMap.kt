package com.example.test2

class ModbusRegisterMap {
    var pallet: Int = 0       // Ví dụ: từ register[19]
    var load: Int = 0         // Từ register[20]
    var unload: Int = 0       // Từ register[21]

    var power: Boolean = false    // Từ register[1]
    var lock: Boolean = false     // Từ register[2]
    var buzzer: Boolean = false   // Từ register[3]
    var position: Boolean = false // Từ register[4]
    var mode: Boolean = false     // Từ register[5]
    var handling: Boolean = false // Từ register[6]

    /**
     * Cập nhật dữ liệu từ mảng thanh ghi (giả sử regs có chiều dài >= 22)
     */
    fun updateFromRegisters(regs: IntArray) {
        if (regs.size >= 22) {
            pallet = regs[19]
            load = regs[20]
            unload = regs[21]
            power = regs[1] == 1
            lock = regs[2] == 1
            buzzer = regs[3] == 1
            position = regs[4] == 1
            mode = regs[5] == 1
            handling = regs[6] == 1
        }
    }
}
