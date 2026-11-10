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
    private var isCancelled = false // Pelacak pembatalan

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // Pastikan channel dibuat sebelum startForeground
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // DETEKSI TOMBOL BATAL
        if (intent?.action == "ACTION_CANCEL_CONVERSION") {
            isCancelled = true // Kunci semua notifikasi susulan!
            MorphEngine.cancelCurrentConversion()
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NOTIFICATION_ID) // Bunuh notifikasi progress
            manager.cancel(NOTIFICATION_ID + 1) // Bunuh notifikasi error/sukses jika sempat muncul
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            
            stopSelf()
            return START_NOT_STICKY
        }

        isCancelled = false // Reset saat konversi baru dimulai
        
        // FIX: getParcelableExtra deprecated di API 33+
        val inputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("INPUT_URI", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Uri>("INPUT_URI")
        }
        val targetFormat = intent?.getStringExtra("TARGET_FORMAT") ?: ""
        
        // TANGKAP PILIHAN KUALITAS DARI BOTTOM SHEET
        val qualityPreset = intent?.getIntExtra("QUALITY_PRESET", 0) ?: 0
        
        val prefs = MorphPrefs(this)
        val showNotif = prefs.showNotifications

        if (inputUri != null && targetFormat.isNotEmpty()) {
            val initialNotif = buildNotification("Morph berjalan", "Mengonversi file...", true, 0)
            startForeground(NOTIFICATION_ID, initialNotif)

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
                originalFileName = originalName,
                qualityPreset = qualityPreset,
                onProgress = { progressText, percentage ->
                    // Smart Notification: hanya update jika aplikasi di background dan belum dibatalkan
                    if (!MainActivity.isAppInForeground && !isCancelled) {
                        val updateNotif = buildNotification("Mengonversi ke ${targetFormat.uppercase()}", progressText, true, percentage)
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(NOTIFICATION_ID, updateNotif)
                    }
                },
                onSuccess = { finalPath ->
                    if (!isCancelled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
                        else @Suppress("DEPRECATION") stopForeground(true)
                        
                        // Smart Notification: tampilkan notif selesai HANYA jika di background
                        if (!MainActivity.isAppInForeground) {
                            val successNotif = buildNotification("✅ Konversi Selesai", "Buka Morph untuk melihat hasil", false)
                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(NOTIFICATION_ID + 1, successNotif)
                        }
                        // Jika di foreground, in-app banner sudah ditampilkan otomatis via MorphEngine.currentStatus
                    }
                    stopSelf()
                },
                onError = { errorMessage ->
                    if (!isCancelled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
                        else @Suppress("DEPRECATION") stopForeground(true)
                        
                        // Smart Notification: tampilkan notif error HANYA jika di background
                        if (!MainActivity.isAppInForeground) {
                            val errorNotif = buildNotification("❌ Konversi Gagal", errorMessage, false)
                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(NOTIFICATION_ID + 1, errorNotif)
                        }
                    }
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
            
            val cancelIntent = Intent(this, MorphService::class.java).apply {
                action = "ACTION_CANCEL_CONVERSION"
            }
            val cancelPendingIntent = PendingIntent.getService(
                this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // Menggunakan ikon "X" bawaan Android
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPendingIntent)
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