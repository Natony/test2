package com.example.test2

import android.graphics.Color

data class OperationStatus(
    val statusCode: Int,
    val displayText: String,
    val iconResId: Int? = null,
    @androidx.annotation.ColorInt val textColor: Int = Color.BLACK
)
