package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConfigActivity : ComponentActivity() {

    private lateinit var etPLCAddress: EditText
    private lateinit var etPLCPort: EditText
    private lateinit var btnSave: Button
    private lateinit var rvAddresses: RecyclerView
    private lateinit var adapter: ConfigAdapter

    private val prefsName = "AppPrefs"
    private val addressesKey = "plc_addresses"
    private val gson = Gson()
    private var addressList = mutableListOf<ConfigItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        etPLCAddress = findViewById(R.id.etPLCAddress)
        etPLCPort = findViewById(R.id.etPLCPort)
        btnSave = findViewById(R.id.btnSave)
        rvAddresses = findViewById(R.id.rvAddresses)

        rvAddresses.layoutManager = LinearLayoutManager(this)
        adapter = ConfigAdapter(
            onItemClick = { selected ->
                // Lưu địa chỉ được chọn để MainActivity dùng
                val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit().putString("modbus_ip", selected.ipAddress).apply()
                prefs.edit().putInt("modbus_port", selected.port).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            },
            onDeleteClick = { itemToDelete ->
                addressList.remove(itemToDelete)
                saveAddresses()
                updateRecyclerView()
            }
        )
        rvAddresses.adapter = adapter

        loadAddresses()
        updateRecyclerView()

        btnSave.setOnClickListener {
            val ip = etPLCAddress.text.toString().trim()
            val portStr = etPLCPort.text.toString().trim()
            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull()
            if (port == null) {
                Toast.makeText(this, "Cổng không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Thêm địa chỉ mới
            val newItem = ConfigItem(ip, port)
            addressList.add(newItem)
            saveAddresses()
            updateRecyclerView()
            etPLCAddress.text.clear()
            etPLCPort.text.clear()
        }
    }

    private fun loadAddresses() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(addressesKey, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<ConfigItem>>() {}.type
            addressList = gson.fromJson(json, type)
        }
    }

    private fun saveAddresses() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = gson.toJson(addressList)
        prefs.edit().putString(addressesKey, json).apply()
    }

    private fun updateRecyclerView() {
        adapter.updateData(addressList)
    }
}
