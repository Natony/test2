package com.example.test2.ui.config

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.MainActivity
import com.example.test2.ModbusConnectionManager
import com.example.test2.R
import com.example.test2.ui.control.ControlFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope

class ConfigFragment : Fragment(R.layout.activity_config) {
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

    // Reference to the connection manager
    private lateinit var connectionManager: ModbusConnectionManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Hide app title bar
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()

        // Get the connection manager instance
        connectionManager = ModbusConnectionManager.getInstance(requireContext())

        etDeviceName  = view.findViewById(R.id.etDeviceName)
        etPLCAddress = view.findViewById(R.id.etPLCAddress)
        etPLCPort    = view.findViewById(R.id.etPLCPort)
        btnSave      = view.findViewById(R.id.btnSave)
        rvAddresses  = view.findViewById(R.id.rvAddresses)

        rvAddresses.layoutManager = LinearLayoutManager(requireContext())
        adapter = ConfigAdapter(
            onItemClick = { selected ->
                // Store selected device in SharedPreferences
                val prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("modbus_ip", selected.ipAddress)
                    .putInt   ("modbus_port", selected.port)
                    .putString("plc_name", selected.name)
                    .apply()

                // Connect to the selected device
                connectionManager.connectToDevice(selected, lifecycleScope)

                // Observe connection status changes
                connectionManager.connectionStatus.observe(viewLifecycleOwner, Observer { status ->
                    when (status) {
                        ModbusConnectionManager.ConnectionStatus.Connected -> {
                            Toast.makeText(requireContext(), "Kết nối thành công với ${selected.name}", Toast.LENGTH_SHORT).show()
                            // Switch to ControlFragment within MainActivity
                            (requireActivity() as MainActivity).openFragment(ControlFragment())
                        }
                        ModbusConnectionManager.ConnectionStatus.Error -> {
                            Toast.makeText(requireContext(), "Không thể kết nối đến ${selected.name}", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // Do nothing for other states
                        }
                    }
                })
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
            val ip   = etPLCAddress.text.toString().trim()
            val port = etPLCPort.text.toString().trim().toIntOrNull()
            if (name.isEmpty() || ip.isEmpty() || port == null) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ và đúng định dạng", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addressList.add(ConfigItem(name, ip, port))
            saveAddresses()
            updateRecyclerView()
            // after adding, list shows under button
            etDeviceName.text.clear()
            etPLCAddress.text.clear()
            etPLCPort.text.clear()
        }
    }

    private fun loadAddresses() {
        val prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.getString(addressesKey, null)?.let { json ->
            val type = object : TypeToken<MutableList<ConfigItem>>() {}.type
            addressList = gson.fromJson(json, type)
        }
    }

    private fun saveAddresses() {
        val prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
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

        AlertDialog.Builder(requireContext())
            .setTitle("Chỉnh sửa thiết bị")
            .setView(dialogView)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = etName.text.toString().trim()
                val newIp   = etIp.text.toString().trim()
                val newPort = etPort.text.toString().toIntOrNull()
                if (newName.isNotEmpty() && newIp.isNotEmpty() && newPort != null) {
                    val idx = addressList.indexOf(item)
                    addressList[idx] = ConfigItem(newName, newIp, newPort)
                    saveAddresses()
                    updateRecyclerView()
                } else {
                    Toast.makeText(requireContext(), "Dữ liệu không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}