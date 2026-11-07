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
                // Opsi: Jika ingin mengupdate teks notifikasi secara real-time
                // val updatedNotification = buildNotification("Memproses...", progressText, true)
                // getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, updatedNotification)
            },
            onSuccess = { finalPath ->
                // 3. Konversi Sukses! Ubah notifikasi menjadi centang/sukses.
                val successNotif = buildNotification("Konversi Selesai", "Tersimpan di: $finalPath", false)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID + 1, successNotif) // Pindah ID agar tidak tertimpa saat Service mati
                
                stopForeground(STOP_FOREGROUND_REMOVE) // Matikan mode tahan banting MIUI
                stopSelf() // Matikan Service
            },
            onError = { errorMessage ->
                // 4. Konversi Gagal.
                val errorNotif = buildNotification("Gagal", errorMessage, false)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID + 1, errorNotif)
                
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )

        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, content: String, isProcessing: Boolean): android.app.Notification {
        // Intent agar jika notifikasi diklik, aplikasi utama terbuka
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Icon bawaan Android dulu
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isProcessing) {
            builder.setProgress(0, 0, true) // Indeterminate Progress Bar (Maju mundur tanpa henti)
            builder.setOngoing(true) // Tidak bisa di-swipe (dibuang)
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