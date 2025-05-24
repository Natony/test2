package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {
    private var isPasswordVisible = false
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Khởi tạo SharedPreferences
        prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        // Find views
        val usernameEdt = findViewById<EditText>(R.id.username)
        val passwordEdt = findViewById<EditText>(R.id.password)
        val eyeIcon    = findViewById<ImageView>(R.id.eye_icon)
        val rememberCb = findViewById<CheckBox>(R.id.remember_checkbox)
        val loginBtn   = findViewById<Button>(R.id.login_button)
        val errorMsg   = findViewById<TextView>(R.id.error_message)

        // Load saved credentials (nếu có)
        prefs.getString("username", null)?.let { savedUser ->
            prefs.getString("password", null)?.let { savedPass ->
                usernameEdt.setText(savedUser)
                passwordEdt.setText(savedPass)
                rememberCb.isChecked = true
            }
        }

        // Toggle ẩn/hiện mật khẩu
        eyeIcon.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordEdt.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            eyeIcon.setImageResource(
                if (isPasswordVisible)
                    android.R.drawable.ic_menu_close_clear_cancel
                else
                    android.R.drawable.ic_menu_view
            )
            // Giữ con trỏ ở cuối
            passwordEdt.setSelection(passwordEdt.text.length)
        }

        // Xử lý nút Login
        loginBtn.setOnClickListener {
            val user = usernameEdt.text.toString().trim()
            val pass = passwordEdt.text.toString().trim()

            if (user == "admin" && pass == "password") {
                // Lưu hoặc xóa credentials tùy chọn checkbox
                prefs.edit().apply {
                    if (rememberCb.isChecked) {
                        putString("username", user)
                        putString("password", pass)
                    } else {
                        remove("username")
                        remove("password")
                    }
                    apply()
                }

                // Chuyển sang MainActivity (tab CONFIG)
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("startPage", "CONFIG")
                })
                finish()

            } else {
                // Hiện thông báo lỗi (không làm layout bị dịch chuyển)
                errorMsg.visibility = View.VISIBLE
            }
        }
    }
}
