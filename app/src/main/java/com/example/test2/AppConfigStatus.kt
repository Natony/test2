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
        // 2- LOCK
        2 to OperationStatus(
            2,
            "🔒 LOCK",
            null,
            Color.parseColor("#4CAF50")
        ),
        // 3- BUZZER
        3 to OperationStatus(
            3,
            "🔔 BUZZER",
            null,
            Color.parseColor("#FFC107")
        ),
        // 4- DIRECTION
        4 to OperationStatus(
            4,
            "↔️ DIRECTION",
            null,
            Color.parseColor("#2196F3")
        ),
        // 5- MODE
        5 to OperationStatus(
            5,
            "CHANGE MODE",
            null,
            Color.parseColor("#9C27B0")
        ),
        // 6- HANDLING
        6 to OperationStatus(
            6,
            "🤲 HANDLING",
            null,
            Color.parseColor("#795548")
        ),
        // 7- EMERGENCY STOP
        7 to OperationStatus(
            7,
            "⛔ EMERGENCY STOP",
            null,
            Color.parseColor("#F44336")
        ),
        // 8- COUNT PALLET
        8 to OperationStatus(
            8,
            "📦 COUNT PALLET",
            null,
            Color.parseColor("#009688")
        ),
        // 9- PICK PALLET
        9 to OperationStatus(
            9,
            "🤏 PICK PALLET",
            null,
            Color.parseColor("#3F51B5")
        ),
        // 10- STACK A
        10 to OperationStatus(
            10,
            "🅰️ STACK A",
            null,
            Color.parseColor("#607D8B")
        ),
        // 11- TAKE PALLET
        11 to OperationStatus(
            11,
            "↩️ TAKE PALLET",
            null,
            Color.parseColor("#00BCD4")
        ),
        // 12- STACK B
        12 to OperationStatus(
            12,
            "🅱️ STACK B",
            null,
            Color.parseColor("#607D8B")
        ),
        // 13- FORWARD
        13 to OperationStatus(
            13,
            "⬆️ FORWARD",
            null,
            Color.parseColor("#8BC34A")
        ),
        // 14- REVERSE
        14 to OperationStatus(
            14,
            "⬇️ REVERSE",
            null,
            Color.parseColor("#CDDC39")
        ),
        // 15- UP
        15 to OperationStatus(
            15,
            "🔼 UP",
            null,
            Color.parseColor("#009688")
        ),
        // 16- DOWN
        16 to OperationStatus(
            16,
            "🔽 DOWN",
            null,
            Color.parseColor("#009688")
        ),
        // 17- PICK PALLETS
        17 to OperationStatus(
            17,
            "🤏 PICK PALLETS",
            null,
            Color.parseColor("#3F51B5")
        ),
        // 18- TAKE PALLETS
        18 to OperationStatus(
            18,
            "↩️ TAKE PALLETS",
            null,
            Color.parseColor("#00BCD4")
        ),
        // 19- LOCATION
        19 to OperationStatus(
            19,
            "📍 LOCATION",
            null,
            Color.parseColor("#3F51B5")
        ),
        // 20- OPERATION STATUS
        20 to OperationStatus(
            20,
            "ℹ️ OPERATION STATUS",
            null,
            Color.parseColor("#795548")
        )
    )
}
