package com.example.test2

enum class ModbusCommand(val address: Int) {
    POWER(1),
    LOCK(2),
    BUZZER(3),
    DIRECTION(4),
    MODE(5),
    HANDLING(6),
    EMERGENCY_STOP(7),
    COUNT_PALLET(8),
    PICK_PALLET(9),
    STACK_A(10),
    TAKE_PALLET(11),
    STACK_B(12),
    FORWARD(13),
    REVERSE(14),
    UP(15),
    DOWN(16),
    PICK_PALLETS(17),
    TAKE_PALLETS(18),
    LOCATION(19),
    OPERATION_STATUS(20)
}