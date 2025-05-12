package com.example.test2.ui.modbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context

class ModbusViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModbusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModbusViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}