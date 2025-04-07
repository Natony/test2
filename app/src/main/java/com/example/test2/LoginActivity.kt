package com.example.test2

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameEditText = findViewById<EditText>(R.id.username)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.login_button)
        val errorMessage = findViewById<TextView>(R.id.error_message)
        val eyeIcon = findViewById<ImageView>(R.id.eye_icon)

        // 👁️ Toggle hiện/ẩn mật khẩu
        eyeIcon.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // icon đóng
            } else {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIcon.setImageResource(android.R.drawable.ic_menu_view) // icon con mắt
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        // 👉 Nút đăng nhập
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (username == "admin" && password == "password") {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                errorMessage.visibility = View.VISIBLE
                errorMessage.text = "Tên đăng nhập hoặc mật khẩu không đúng"
            }
        }
    }
}
