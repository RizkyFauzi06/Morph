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
                // Masukkan file finalPath ke dalam notifikasi
                val finalFile = File(finalPath)
                val successNotif = buildNotification("Konversi Selesai", "Sentuh untuk membuka file", false, finalFile)
                
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
                // Konversi Gagal. Timpa notifikasi menjadi pesan error.
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
        fileToOpen: File? = null
    ): android.app.Notification {
        
        // Logika Pintar: Jika ada file yang diselipkan, buat Intent BUKA FILE. Jika tidak, buka aplikasi Morph.
        val intent = if (fileToOpen != null) {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", fileToOpen)
            val extension = fileToOpen.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Notifikasi otomatis hilang saat diklik!
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isProcessing) {
            builder.setProgress(0, 0, true)
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