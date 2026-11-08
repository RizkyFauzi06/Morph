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
        createNotificationChannel()

        val inputUri = intent?.getParcelableExtra<Uri>("INPUT_URI")
        val targetFormat = intent?.getStringExtra("TARGET_FORMAT") ?: "mp4"

        if (inputUri == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Tampilkan Notifikasi "Sedang Berjalan" (Mode Indeterminate / Berputar)
        val notification = buildNotification("Memproses ke $targetFormat...", "Mohon tunggu, Morph sedang bekerja", true)
        startForeground(NOTIFICATION_ID, notification)

        // 2. Eksekusi Engine di dalam Service ini
        MorphEngine.convert(
            context = this,
            inputUri = inputUri,
            outputExtension = targetFormat,
            onProgress = { progressText ->
                // Mengupdate notifikasi secara real-time pada ID yang SAMA
                val updatedNotification = buildNotification("Memproses ke $targetFormat...", progressText, true)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, updatedNotification)
            },
            onSuccess = { finalPath ->
                // Panggil 3 parameter saja. Tidak perlu melempar File lagi karena klik notif sekarang mengarah ke UI History
                val successNotif = buildNotification("Konversi Selesai", "Buka Morph untuk melihat hasil", false)
                
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, successNotif)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                stopSelf()
            },
            onError = { errorMessage ->
                val errorNotif = buildNotification("Konversi Gagal", errorMessage, false)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, errorNotif)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                stopSelf()
            }
        )

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