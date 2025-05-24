package com.example.test2

import android.graphics.Color
import androidx.annotation.ColorInt

object AppConfigStatus {
    val operationStatusConfig: Map<Int, OperationStatus> = mapOf(
        // 1- POWER
        1 to OperationStatus(
            1,
            "READY",
            null,
            Color.parseColor("#FF5722") // e.g., deep orange
        ),
        // 2- BUZZER
        2 to OperationStatus(
            2,
            "🔔 BUZZER",
            null,
            Color.parseColor("#FFC107")
        ),
        // 3- DIRECTION
        3 to OperationStatus(
            3,
            "↔️ DIRECTION",
            null,
            Color.parseColor("#2196F3")
        ),
        // 4- MODE
        4 to OperationStatus(
            4,
            "CHANGE MODE",
            null,
            Color.parseColor("#9C27B0")
        ),
        // 5- EMERGENCY STOP
        5 to OperationStatus(
            5,
            "⛔ EMERGENCY STOP",
            null,
            Color.parseColor("#F44336")
        ),
        // 6- COUNT PALLET
        6 to OperationStatus(
            6,
            "📦 COUNT PALLET",
            null,
            Color.parseColor("#009688")
        ),
        // 7- PICK PALLET
        7 to OperationStatus(
            7,
            "🤏 PICK PALLET",
            null,
            Color.parseColor("#3F51B5")
        ),
        // 8- STACK A
        8 to OperationStatus(
            8,
            "🅰️ STACK A",
            null,
            Color.parseColor("#607D8B")
        ),
        // 9- TAKE PALLET
        9 to OperationStatus(
            9,
            "↩️ TAKE PALLET",
            null,
            Color.parseColor("#00BCD4")
        ),
        // 10- STACK B
        10 to OperationStatus(
            10,
            "🅱️ STACK B",
            null,
            Color.parseColor("#607D8B")
        ),
        // 11- FORWARD
        11 to OperationStatus(
            11,
            "⬆️ FORWARD",
            null,
            Color.parseColor("#8BC34A")
        ),
        // 12- REVERSE
        12 to OperationStatus(
            12,
            "⬇️ REVERSE",
            null,
            Color.parseColor("#CDDC39")
        ),
        // 13- UP
        13 to OperationStatus(
            13,
            "🔼 UP",
            null,
            Color.parseColor("#009688")
        ),
        // 14- DOWN
        14 to OperationStatus(
            14,
            "🔽 DOWN",
            null,
            Color.parseColor("#009688")
        ),
        // 15- PICK PALLETS
        15 to OperationStatus(
            15,
            "🤏 PICK PALLETS",
            null,
            Color.parseColor("#3F51B5")
        ),
        // 16- TAKE PALLETS
        16 to OperationStatus(
            18,
            "↩️ TAKE PALLETS",
            null,
            Color.parseColor("#00BCD4")
        ),
        // 18- OPERATION STATUS
        18 to OperationStatus(
            18,
            "ℹ️ OPERATION STATUS",
            null,
            Color.parseColor("#795548")
        )
    )
}
