package com.example.test2.ui.alarm

import java.util.Date

/**
 * Enum class for alarm severity levels
 */
enum class AlarmSeverity(val color: Int) {
    LOW(0xFF4CAF50.toInt()),      // Green
    MEDIUM(0xFFFF9800.toInt()),   // Orange
    HIGH(0xFFE91E63.toInt()),     // Pink
    CRITICAL(0xFFFF0000.toInt())  // Red
}

/**
 * Data class representing an alarm
 */
data class Alarm(
    val id: Int,                     // Unique identifier for the alarm
    val code: String,                // Alarm code (e.g., A001)
    val message: String,             // Alarm description
    val registerAddress: Int,        // Modbus register address where this alarm originated
    val severity: AlarmSeverity,     // Alarm severity level
    val timestamp: Date = Date(),    // Time when the alarm occurred
    var isActive: Boolean = true,    // Whether the alarm is still active
    var isAcknowledged: Boolean = false, // Whether the alarm has been acknowledged
    var selected: Boolean = false    // Selection state for UI interaction
) {
    companion object {
        /**
         * Map of alarm codes and their corresponding messages
         * These would typically come from a configuration file or database
         */
        private val ALARM_DEFINITIONS = mapOf(
            8001 to AlarmDefinition("A001", "Temperature sensor fault", AlarmSeverity.HIGH),
            8002 to AlarmDefinition("A002", "Pressure sensor fault", AlarmSeverity.MEDIUM),
            8003 to AlarmDefinition("A003", "Emergency stop activated", AlarmSeverity.CRITICAL),
            8004 to AlarmDefinition("A004", "Motor overload", AlarmSeverity.HIGH),
            8005 to AlarmDefinition("A005", "Battery low", AlarmSeverity.LOW),
            8006 to AlarmDefinition("A006", "Communication error", AlarmSeverity.MEDIUM),
            8007 to AlarmDefinition("A007", "Flow rate below minimum", AlarmSeverity.MEDIUM),
            8008 to AlarmDefinition("A008", "Power supply failure", AlarmSeverity.CRITICAL),
            8009 to AlarmDefinition("A009", "System warning", AlarmSeverity.LOW),
            8010 to AlarmDefinition("A010", "Controller error", AlarmSeverity.HIGH),
            8080 to AlarmDefinition("E001", "PLC error", AlarmSeverity.CRITICAL),
            8085 to AlarmDefinition("E002", "Critical system fault", AlarmSeverity.CRITICAL)
            // Add more alarm definitions as needed
        )

        /**
         * Creates an Alarm object from a register value
         */
        fun fromRegisterValue(registerAddress: Int, value: Int): Alarm? {
            val definition = ALARM_DEFINITIONS[value] ?: return null

            return Alarm(
                id = value,
                code = definition.code,
                message = definition.message,
                registerAddress = registerAddress,
                severity = definition.severity
            )
        }
    }
}

/**
 * Data class for alarm definition
 */
data class AlarmDefinition(
    val code: String,
    val message: String,
    val severity: AlarmSeverity
)