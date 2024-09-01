package com.example.zephir_trading_android

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import android.content.BroadcastReceiver
import android.content.Intent
import android.app.PendingIntent
import android.os.Environment
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.content.ContentValues
import android.graphics.drawable.Drawable
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squareup.picasso.Picasso
import java.io.File
import android.net.Uri
import android.graphics.Bitmap
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "Boot completed: Starting FirebaseMessagingService.")
        }
    }
}

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received: ${remoteMessage.data}")

        try {
            remoteMessage.data.let { data ->
                val title = data["title"] ?: return
                val asset = data["asset"] ?: return
                val timestamp = data["timestamp"] ?: return
                val side = data["side"] ?: return
                val url = data["url"] ?: return

                Log.d("FCM", "Processing message: title=$title, asset=$asset, timestamp=$timestamp, side=$side, url=$url")

                downloadAndStoreImage(url, asset, side, timestamp)
                storeMessage(asset, timestamp, side, url)

                sendNotification(title, asset, side, timestamp)
            }
        } catch (e: Exception) {
            Log.e("FCM", "Error processing message", e)
        }
    }

    private fun downloadAndStoreImage(url: String, asset: String, side: String, timestamp: String) {
        Log.d("FCM", "Downloading image from URL: $url")
        val gsUrl = if (url.startsWith("https://storage.googleapis.com/")) {
            "gs://" + url.substringAfter("https://storage.googleapis.com/")
        } else {
            Log.e("FCM", "Invalid URL format: $url")
            return
        }
        val sanitizedTimestamp = timestamp.replace(":", "").replace(" ", "_")
        val fileName = "image_${asset}_$sanitizedTimestamp.jpg"
        val assetSubfolder = File(getExternalFilesDir(null), asset)
        if (!assetSubfolder.exists()) {
            assetSubfolder.mkdirs()
        }
        val file = File(assetSubfolder, fileName)

        val storageReference: StorageReference = FirebaseStorage.getInstance().getReferenceFromUrl(gsUrl)
        storageReference.getDownloadUrl().addOnSuccessListener { downloadUrl ->
            Log.d("FCM", "Download URL: $downloadUrl")

                Handler(Looper.getMainLooper()).post {
                    try {
                        Picasso.get().load(downloadUrl.toString()).into(object : com.squareup.picasso.Target {
                            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                                try {
                                    val outputStream = FileOutputStream(file)
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                                    outputStream.flush()
                                    outputStream.close()
                                    Log.d("FCM", "Image successfully saved to $file")
                                } catch (e: IOException) {
                                    Log.e("FCM", "Error saving image", e)
                                }
                            }

                            override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                                Log.e("FCM", "Bitmap load failed", e)
                            }

                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                                // Handle preparations for loading bitmap
                            }
                        })
                    } catch (e: Exception) {
                        // Handle general Picasso loading errors
                    }
                }
        }.addOnFailureListener { exception ->
            Log.e("FCM", "Error getting download URL", exception)
        }
    }

    private fun storeMessage(asset: String, timestamp: String, side: String, url: String) {
        Log.d("FCM", "Storing message in database: asset=$asset, timestamp=$timestamp, side=$side, url=$url")
        val dbHelper = DBHelper(this.applicationContext)
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("asset", asset)
            put("timestamp", timestamp)
            put("side", side)
            put("url", url)
        }
        db.insert("Messages", null, values)
        db.close()
    }

    private fun sendNotification(title: String, asset: String, side: String, timestamp: String) {
        try {
            Log.d("FCM", "Starting to send notification: title=$title, asset=$asset, side=$side, timestamp=$timestamp")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationID = timestamp.hashCode()
            Log.d("FCM", "Notification ID generated: $notificationID")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelName = "Zephir_Trading_Notifications"
                val channelDescription = "Notifications for trading updates"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val notificationChannel = NotificationChannel("Zephir_Trading_Notifications", channelName, importance)
                notificationChannel.description = channelDescription
                notificationManager.createNotificationChannel(notificationChannel)
                Log.d("FCM", "Notification channel created or updated")
            }

            val sanitizedTimestamp = timestamp.replace(":", "").replace(" ", "_")
            val fileName = "image_${asset}_$sanitizedTimestamp.jpg"
            Log.d("FCM", "Sanitized timestamp and generated filename: $fileName")
            val assetSubfolder = File(getExternalFilesDir(null), asset) 
            if (!assetSubfolder.exists()) {
                assetSubfolder.mkdirs()
                Log.d("FCM", "Asset subfolder created: ${assetSubfolder.path}")
            }
            val file = File(assetSubfolder, fileName)

            val fileUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "image/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            Log.d("FCM", "Intent created for ACTION_VIEW")

            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            Log.d("FCM", "PendingIntent created for notification")

            val notificationBuilder = NotificationCompat.Builder(this, "Zephir_Trading_Notifications")
                .setSmallIcon(R.drawable.zephir_icon)
                .setContentTitle(title)
                .setContentText(side)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            Log.d("FCM", "Notification builder set up")

            NotificationManagerCompat.from(this).notify(notificationID, notificationBuilder.build())
            Log.d("FCM", "Notification dispatched with ID: $notificationID")
        } catch (e: Exception) {
            Log.e("FCM", "Error sending notification: ${e.message}")
        }
    }
}
