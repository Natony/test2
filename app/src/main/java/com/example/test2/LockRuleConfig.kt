package com.example.test2.rules

import com.example.test2.ButtonStateHandler.LockRule
import com.example.test2.ModbusCommand

/**
 * Cấu hình các rule khóa động cho ButtonStateHandler.
 * Dễ dàng mở rộng và quản lý tập trung.
 */
object LockRuleConfig {
    private val rules = mutableListOf<LockRule>()

    init {
        // Manual: mutual-exclusive khi giá trị == 1
        addExclusiveGroup(listOf(
            ModbusCommand.FORWARD,
            ModbusCommand.REVERSE,
            ModbusCommand.UP,
            ModbusCommand.DOWN
        ))

        // Auto basics: mutual-exclusive khi giá trị == 1
        addExclusiveGroup(listOf(
            ModbusCommand.PICK_PALLET,
            ModbusCommand.TAKE_PALLET,
            ModbusCommand.STACK_A,
            ModbusCommand.STACK_B
        ))

        // Main: mutual-exclusive khi giá trị == 1
        addExclusiveGroup(listOf(
            ModbusCommand.BUZZER,
            ModbusCommand.COUNT_PALLET
        ))

        // Pallet quantity: lock TAKE_PALLETS khi PICK_PALLETS > 0 (lockWhenValue < 0 => != 0)
        addQuantityRule(
            trigger = ModbusCommand.PICK_PALLETS,
            toLock = listOf(
                ModbusCommand.TAKE_PALLETS,
                ModbusCommand.TAKE_PALLET,
                ModbusCommand.PICK_PALLET,
                ModbusCommand.STACK_A,
                ModbusCommand.STACK_B,
                ModbusCommand.FORWARD,
                ModbusCommand.REVERSE,
                ModbusCommand.UP,
                ModbusCommand.DOWN
            )
        )
        // Ngược lại: lock PICK_PALLETS khi TAKE_PALLETS > 0
        addQuantityRule(
            trigger = ModbusCommand.TAKE_PALLETS,
            toLock = listOf(
                ModbusCommand.PICK_PALLETS,
                ModbusCommand.TAKE_PALLET,
                ModbusCommand.PICK_PALLET,
                ModbusCommand.STACK_A,
                ModbusCommand.STACK_B,
                ModbusCommand.FORWARD,
                ModbusCommand.REVERSE,
                ModbusCommand.UP,
                ModbusCommand.DOWN
            )
        )
    }

    /**
     * Tạo rule khóa chéo cho một nhóm commands khi giá trị == 1
     */
    private fun addExclusiveGroup(group: List<ModbusCommand>) {
        group.forEach { trigger ->
            rules += LockRule(
                trigger = trigger,
                lockWhenValue = 1,
                toLock = group.filter { it != trigger }
            )
        }
    }

    /**
     * Tạo rule dựa trên quantity: lockWhenValue = -1 để handler hiểu là value != 0
     */
    private fun addQuantityRule(
        trigger: ModbusCommand,
        toLock: List<ModbusCommand>,
        toUnlock: List<ModbusCommand> = emptyList()
    ) {
        rules += LockRule(
            trigger = trigger,
            lockWhenValue = -1,
            toLock = toLock,
            toUnlock = toUnlock
        )
    }

    /**
     * Trả về danh sách rule bất biến
     */
    fun getRules(): List<LockRule> = rules.toList()
}
