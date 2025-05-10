package com.example.test2.rules

import com.example.test2.ButtonStateHandler.LockRule
import com.example.test2.ModbusCommand

/**
 * Cấu hình các rule khóa động cho ButtonStateHandler.
 * Nhóm các rule theo chức năng để dễ quản lý khi có nhiều quy tắc.
 */
object LockRuleConfig {
    private val rules = mutableListOf<LockRule>()

    init {
        addHandlingRules()
        addManualRules()
        addAutoRules()
        addMainRules()
        // TODO: addOtherRules() khi có thêm yêu cầu khóa khác
    }

    private fun addHandlingRules() {
        // Khi HANDLING = 1: khóa STACK_B, mở STACK_A
        rules += LockRule(
            trigger = ModbusCommand.HANDLING,
            lockWhenValue = 1,
            toLock = listOf(ModbusCommand.STACK_B),
            toUnlock = listOf(ModbusCommand.STACK_A)
        )
        // Khi HANDLING = 0: khóa STACK_A, mở STACK_B
        rules += LockRule(
            trigger = ModbusCommand.HANDLING,
            lockWhenValue = 0,
            toLock = listOf(ModbusCommand.STACK_A),
            toUnlock = listOf(ModbusCommand.STACK_B)
        )
    }

    private fun addManualRules() {
        // Định nghĩa nhóm các nút manual để tạo rule mutual-exclusive
        val manualGroup = listOf(
            ModbusCommand.FORWARD,
            ModbusCommand.REVERSE,
            ModbusCommand.UP,
            ModbusCommand.DOWN
        )
        addExclusiveGroup(manualGroup)
    }

    private fun addAutoRules() {
        // Định nghĩa nhóm các nút manual để tạo rule mutual-exclusive
        val autoGroup = listOf(
            ModbusCommand.PICK_PALLETS,
            ModbusCommand.TAKE_PALLETS,
            ModbusCommand.PICK_PALLET,
            ModbusCommand.TAKE_PALLET,
            ModbusCommand.STACK_A,
            ModbusCommand.STACK_B
        )
        addExclusiveGroup(autoGroup)
    }

    private fun addMainRules(){
        val mainGroup = listOf(
            ModbusCommand.LOCK,
            ModbusCommand.BUZZER,
            ModbusCommand.COUNT_PALLET
        )
        addExclusiveGroup(mainGroup)
    }
    /**
     * Tạo rule khóa chéo cho một nhóm commands, chỉ cho phép 1 active mỗi lúc
     */
    private fun addExclusiveGroup(group: List<ModbusCommand>) {
        group.forEach { btn ->
            rules += LockRule(
                trigger = btn,
                lockWhenValue = 1,
                toLock = group.filter { it != btn },
                toUnlock = emptyList()
            )
        }
    }

    /**
     * Lấy toàn bộ rule dưới dạng bất biến
     */
    fun getRules(): List<LockRule> = rules.toList()
}