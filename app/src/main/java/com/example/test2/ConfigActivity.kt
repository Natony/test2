package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConfigActivity : ComponentActivity() {
    private lateinit var etDeviceName: EditText
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

        etDeviceName = findViewById(R.id.etDeviceName)
        etPLCAddress = findViewById(R.id.etPLCAddress)
        etPLCPort = findViewById(R.id.etPLCPort)
        btnSave = findViewById(R.id.btnSave)
        rvAddresses = findViewById(R.id.rvAddresses)

        rvAddresses.layoutManager = LinearLayoutManager(this)
        adapter = ConfigAdapter(
            onItemClick = { selected ->
                val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("modbus_ip", selected.ipAddress)
                    .putInt   ("modbus_port", selected.port)
                    .putString("plc_name", selected.name)
                    .apply()

                // Launch MainActivity and clear back stack
                Intent(this, MainActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(it)
                }
                finish()
            },
            onEditClick = { item -> showEditDialog(item) },
            onDeleteClick = { item ->
                addressList.remove(item)
                saveAddresses()
                updateRecyclerView()
            }
        )
        rvAddresses.adapter = adapter

        loadAddresses()
        updateRecyclerView()

        btnSave.setOnClickListener {
            val name = etDeviceName.text.toString().trim()
            val ip = etPLCAddress.text.toString().trim()
            val portStr = etPLCPort.text.toString().trim()
            if (name.isEmpty() || ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull() ?: run {
                Toast.makeText(this, "Cổng không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addressList.add(ConfigItem(name, ip, port))
            saveAddresses()
            updateRecyclerView()
            etDeviceName.text.clear()
            etPLCAddress.text.clear()
            etPLCPort.text.clear()
        }
    }

    private fun loadAddresses() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.getString(addressesKey, null)?.let { json ->
            val type = object : TypeToken<MutableList<ConfigItem>>() {}.type
            addressList = gson.fromJson(json, type)
        }
    }

    private fun saveAddresses() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString(addressesKey, gson.toJson(addressList)).apply()
    }

    private fun updateRecyclerView() {
        adapter.updateData(addressList)
    }

    private fun showEditDialog(item: ConfigItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_config, null)
        val etName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etIp   = dialogView.findViewById<EditText>(R.id.etEditIp)
        val etPort = dialogView.findViewById<EditText>(R.id.etEditPort)
        etName.setText(item.name)
        etIp.setText(item.ipAddress)
        etPort.setText(item.port.toString())

        AlertDialog.Builder(this)
            .setTitle("Chỉnh sửa thiết bị")
            .setView(dialogView)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = etName.text.toString().trim()
                val newIp   = etIp.text.toString().trim()
                val newPort = etPort.text.toString().trim().toIntOrNull()
                if (newName.isNotEmpty() && newIp.isNotEmpty() && newPort != null) {
                    val idx = addressList.indexOf(item)
                    addressList[idx] = ConfigItem(newName, newIp, newPort)
                    saveAddresses()
                    updateRecyclerView()
                } else {
                    Toast.makeText(this, "Dữ liệu không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}