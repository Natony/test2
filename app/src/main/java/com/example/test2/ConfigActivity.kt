package com.example.test2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View // Thêm import này
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
    private lateinit var btnAdd: Button
    private lateinit var btnSave: Button
    private lateinit var btnGoToMain: Button
    private lateinit var adapter: ConfigAdapter // Sửa thành ConfigAdapter
    private lateinit var prefs: SharedPreferences
    private var selectedItem: ConfigItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        initViews()
        setupRecyclerView()
        loadSavedConfigs()
    }

    private fun initViews() {
        etPLCAddress = findViewById(R.id.etPLCAddress)
        btnAdd = findViewById(R.id.btnAdd)
        btnSave = findViewById(R.id.btnSave)
        btnGoToMain = findViewById(R.id.btnGoToMain)

        btnAdd.setOnClickListener { resetForm() }
        btnSave.setOnClickListener { saveConfig() }
        btnGoToMain.setOnClickListener { goToMain() }
    }

    private fun setupRecyclerView() {
        adapter = ConfigAdapter(
            onItemClick = { config ->
                selectedItem = config
                etPLCAddress.setText(config.ipAddress)
                btnSave.visibility = View.VISIBLE // Đã có import View
            },
            onDeleteClick = { config ->
                deleteConfig(config)
            }
        )

        findViewById<RecyclerView>(R.id.rvConfigs).apply {
            layoutManager = LinearLayoutManager(this@ConfigActivity)
            adapter = this@ConfigActivity.adapter // Gán đúng adapter
        }
    }

    private fun saveConfig() {
        val ip = etPLCAddress.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập IP", Toast.LENGTH_SHORT).show()
            return
        }

        val configs = getSavedConfigs().toMutableList()
        selectedItem?.let {
            configs.removeAll { item -> item.id == it.id }
            configs.add(it.copy(ipAddress = ip))
        } ?: run {
            configs.add(ConfigItem(ipAddress = ip))
        }

        saveConfigs(configs)
        loadSavedConfigs()
        resetForm()
    }

    private fun deleteConfig(config: ConfigItem) {
        val configs = getSavedConfigs().toMutableList().apply {
            removeAll { it.id == config.id }
        }
        saveConfigs(configs)
        loadSavedConfigs()
    }

    private fun getSavedConfigs(): List<ConfigItem> {
        val json = prefs.getString("saved_configs", "[]")
        return Gson().fromJson(json, object : TypeToken<List<ConfigItem>>() {}.type)
    }

    private fun saveConfigs(configs: List<ConfigItem>) {
        prefs.edit().putString("saved_configs", Gson().toJson(configs)).apply()
    }

    private fun loadSavedConfigs() {
        adapter.updateData(getSavedConfigs()) // Đảm bảo adapter có phương thức này
    }

    private fun resetForm() {
        selectedItem = null
        etPLCAddress.text.clear()
        btnSave.visibility = View.GONE // Đã có import View
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}