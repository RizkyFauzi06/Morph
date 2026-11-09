package com.morph.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import java.io.File

class MorphService : Service() {

    private val CHANNEL_ID = "Morph_Foreground"
    private val NOTIFICATION_ID = 143

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputUri = intent?.getParcelableExtra<Uri>("INPUT_URI")
        val targetFormat = intent?.getStringExtra("TARGET_FORMAT") ?: ""
        
        // Baca pengaturan dari Preferences
        val prefs = MorphPrefs(this)
        val showNotif = prefs.showNotifications

        if (inputUri != null && targetFormat.isNotEmpty()) {
            // Notifikasi wajib Android (Foreground) - Jika showNotif false, kita buat sangat senyap
            val initialNotif = buildNotification("Morph berjalan", "Mengonversi file...", true, 0)
            startForeground(NOTIFICATION_ID, initialNotif)

            // Ekstrak nama asli file untuk fitur Keep Original Filename (8.2.5)
            val cursor = contentResolver.query(inputUri, null, null, null, null)
            val originalName = if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else "file"
            } else "file"
            cursor?.close()

            MorphEngine.convert(
                context = this,
                inputUri = inputUri,
                outputExtension = targetFormat,
                originalFileName = originalName, // Lempar nama asli ke mesin
                onProgress = { progressText, percentage ->
                    // PBI 8.2.4 (Smart Notification): Update notif HANYA JIKA aplikasi di latar belakang
                    if (!MainActivity.isAppInForeground) {
                        val updateNotif = buildNotification("Mengonversi ke ${targetFormat.uppercase()}", progressText, true, percentage)
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(NOTIFICATION_ID, updateNotif)
                    }
                },
                onSuccess = { finalPath ->
                    val successNotif = buildNotification("Konversi Selesai", "Buka Morph untuk melihat hasil", false)
                    val manager = getSystemService(NotificationManager::class.java)
                    
                    // PERBAIKAN BUG: Hapus total notifikasi proses yang lama dari layar!
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE) 
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true) 
                    }
                    
                    // Baru munculkan notifikasi sukses
                    manager.notify(NOTIFICATION_ID + 1, successNotif)
                    stopSelf()
                },
                onError = { errorMessage ->
                    val errorNotif = buildNotification("Konversi Gagal", errorMessage, false)
                    val manager = getSystemService(NotificationManager::class.java)
                    
                    // PERBAIKAN BUG: Hapus total notifikasi proses yang lama
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    
                    manager.notify(NOTIFICATION_ID + 1, errorNotif)
                    stopSelf()
                }
            )
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String, 
        content: String, 
        isProcessing: Boolean,
        progress: Int = 0
    ): android.app.Notification {
        
        // SELALU ARAHKAN KE MARKAS UTAMA MORPH (HISTORY TAB)
        val intent = Intent(this, MainActivity::class.java).apply {
            // Pastikan aplikasi terbuka di layar depan, bukan menumpuk
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP 
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isProcessing) {
            builder.setProgress(100, progress, false)
            builder.setOngoing(true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Morph Conversions",
                NotificationManager.IMPORTANCE_LOW // Low = Muncul tanpa suara berisik
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}