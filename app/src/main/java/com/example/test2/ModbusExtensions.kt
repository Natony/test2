package com.example.test2.ui.modbus

import android.widget.ImageButton
import androidx.annotation.DrawableRes

fun ImageButton.updateModbusState(
    isActive: Boolean,
    @DrawableRes activeResId: Int,
    @DrawableRes inactiveResId: Int
) {
    setImageResource(if (isActive) activeResId else inactiveResId)
}


