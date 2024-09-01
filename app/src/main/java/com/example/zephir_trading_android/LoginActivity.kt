package com.example.zephir_trading_android

import com.google.firebase.messaging.FirebaseMessaging
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.Connection
import org.mindrot.jbcrypt.BCrypt

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.util.Calendar
import java.util.Date
import java.util.Properties


class LoginActivity : AppCompatActivity() {

    private var dbConnection: Connection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val properties = Properties()
        assets.open("config.properties").use { properties.load(it) }

        val dbEnpoint = properties.getProperty("DB_ENPOINT")
        val dbPort = properties.getProperty("DB_PORT")
        val dbName = properties.getProperty("DB_NAME")
        val dbUser = properties.getProperty("DB_USER")
        val dbPassword = properties.getProperty("DB_PASSWORD")

        dbConnection = MySQLConnectionBuilder.createConnectionPool {
            username = dbUser
            password = dbPassword
            host = dbEnpoint
            port = dbPort.toInt()
            database = dbName
            maxActiveConnections = 10
        }

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
    val query = "SELECT password FROM users WHERE email = ?"
    dbConnection?.sendPreparedStatement(query, listOf(email))?.thenApply { queryResult ->
        queryResult.rows.firstOrNull()?.let { row ->
            val storedHash = row["password"] as String
            if (BCrypt.checkpw(password, storedHash)) {
                handleToken(email)
            } else {
                Log.e("Login", "Invalid password")
            }
        } ?: Log.e("Login", "User not found")
    }?.exceptionally { throwable ->
        Log.e("Database", "Error executing database operation", throwable)
        null
    }
    }

    private fun handleToken(email: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val deviceToken = task.result
                val calendar = Calendar.getInstance()
                val currentDate = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                val expiryDate = calendar.time
                updateOrInsertDeviceToken(deviceToken, email, currentDate, expiryDate)

                FirebaseMessaging.getInstance().subscribeToTopic("zephir_trading_active")
                    .addOnCompleteListener { topicTask ->
                        if (topicTask.isSuccessful) {
                            Log.d("FCM", "Subscribed to topic successfully")
                        } else {
                            Log.e("FCM", "Failed to subscribe to topic")
                        }
                    }
            } else {
                Log.e("FCM", "Failed to get device token")
            }
        }
    }

    private fun updateOrInsertDeviceToken(deviceToken: String, email: String, currentDate: Date, expiryDate: Date) {
        val query = """
            INSERT INTO devices (device_token, user_email, token_start_date, token_expiry_date) 
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                user_email = VALUES(user_email), 
                token_start_date = VALUES(token_start_date), 
                token_expiry_date = VALUES(token_expiry_date);
        """
        dbConnection?.sendPreparedStatement(query, listOf(deviceToken, email, currentDate, expiryDate))?.thenApply { result ->
            Log.d("SQL Debug", "Query executed, rows affected: ${result.rowsAffected}")
            runOnUiThread {
            val loginButton = findViewById<Button>(R.id.loginButton)
            loginButton.text = "Token Refreshed"
            loginButton.isEnabled = false  // Disable the login button
        }
        }?.exceptionally { throwable ->
            Log.e("SQL Error", "Error executing SQL operation", throwable)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dbConnection?.disconnect()
    }
}