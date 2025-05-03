// LoginActivity.kt
package com.example.test2

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.test2.R

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

        // Toggle show/hide password
        eyeIcon.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordEditText.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            eyeIcon.setImageResource(
                if (isPasswordVisible) android.R.drawable.ic_menu_close_clear_cancel
                else android.R.drawable.ic_menu_view
            )
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        // Handle login button
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (username == "admin" && password == "password") {
                // Launch MainActivity and open Config tab
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("startPage", "CONFIG")
                }
                startActivity(intent)
                finish()
            } else {
                errorMessage.visibility = View.VISIBLE
                errorMessage.text = "Tên đăng nhập hoặc mật khẩu không đúng"
            }
        }
    }
}