package com.example.zephir_trading_android

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ListView
import android.widget.Button
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter
import android.view.View
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class SignalsActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var messagesList = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signals)

        listView = findViewById(R.id.assetsListView)
        loadMessages()
        adapter = ArrayAdapter(this, R.layout.list_item, R.id.text1, messagesList.map { it.first })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val messagePair = messagesList[position]
            openImage(messagePair.second)
        }
    }

    private fun loadMessages() {
        val dbHelper = DBHelper(this)
        val db = dbHelper.readableDatabase
        val cursor = db.query("Messages", arrayOf("timestamp", "asset", "side", "filePath"), null, null, null, null, "timestamp DESC")
        if (cursor.moveToFirst()) {
            do {
                val timestamp = cursor.getString(cursor.getColumnIndex("timestamp"))
                val asset = cursor.getString(cursor.getColumnIndex("asset"))
                val side = cursor.getString(cursor.getColumnIndex("side"))
                val filePath = cursor.getString(cursor.getColumnIndex("filePath"))
                val message = "$timestamp $asset $side"
                messagesList.add(Pair(message, filePath))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
    }

    private fun openImage(filePath: String) {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "image/*")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }
}