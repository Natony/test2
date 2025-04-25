package com.example.test2

enum class ModbusCommand(val address: Int) {
    POWER(1),
    LOCK(2),
    BUZZER(3),
    POSITION(4),
    MODE(5),
    HANDLING(6),
    EMERGENCY_STOP(7),
    COUNT_PALLET(8),
    PICK_PALLETS(9),
    PICK_PALLET(10),
    STACK_A(11),
    TAKE_PALLETS(12),
    TAKE_PALLET(13),
    STACK_B(14),
    FORWARD(15),
    REVERSE(16),
    UP(17),
    DOWN(18),
    LOCATION(19)
}