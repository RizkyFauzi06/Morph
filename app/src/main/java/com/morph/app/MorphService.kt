package com.morph.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionJob
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.JobStatus
import kotlinx.coroutines.*

class MorphService : Service() {

    private val CHANNEL_ID = "Morph_Foreground"
    private val NOTIFICATION_ID = 143
    private val SUMMARY_NOTIFICATION_ID = 144

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueExecutionJob: Job? = null
    private var isCancelled = false

    companion object {
        const val ACTION_START_QUEUE = "com.morph.app.ACTION_START_QUEUE"
        const val ACTION_CANCEL_CONVERSION = "ACTION_CANCEL_CONVERSION"
        const val ACTION_CANCEL_SINGLE_JOB = "com.morph.app.ACTION_CANCEL_SINGLE_JOB"
        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Hubungkan pembatalan dari manager ke MorphEngine
        ConversionQueueManager.onCancelActiveConversion = {
            MorphEngine.cancelCurrentConversion()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppStrings.setLanguage(com.morph.app.MorphPrefs(this).appLanguage)

        when (intent?.action) {
            ACTION_CANCEL_CONVERSION -> {
                isCancelled = true
                ConversionQueueManager.cancelAllJobs()
                stopForegroundAndService()
                return START_NOT_STICKY
            }

            ACTION_CANCEL_SINGLE_JOB -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                if (jobId != null) {
                    ConversionQueueManager.cancelJob(jobId)
                }
                return START_NOT_STICKY
            }
        }

        isCancelled = false

        // Dukungan kompatibilitas backward jika intent membawa file tunggal
        val inputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("INPUT_URI", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Uri>("INPUT_URI")
        }
        val targetFormat = intent?.getStringExtra("TARGET_FORMAT") ?: ""
        val qualityPreset = intent?.getIntExtra("QUALITY_PRESET", 0) ?: 0

        if (inputUri != null && targetFormat.isNotEmpty()) {
            val originalName = getFileNameFromUri(this, inputUri)
            val mimeType = contentResolver.getType(inputUri) ?: "*/*"
            ConversionQueueManager.addJob(
                inputUri = inputUri,
                originalFileName = originalName,
                mimeType = mimeType,
                targetFormat = targetFormat,
                qualityPreset = qualityPreset
            )
        }

        // Mulai eksekusi antrean
        if (ConversionQueueManager.hasActiveOrWaitingJobs()) {
            val strings = AppStrings.get()
            val initialNotif = buildNotification(
                title = strings.serviceRunningTitle,
                content = strings.servicePreparingQueue,
                isProcessing = true,
                progress = 0
            )
            startForeground(NOTIFICATION_ID, initialNotif)
            processQueue()
        } else {
            stopForegroundAndService()
        }

        return START_NOT_STICKY
    }

    private fun processQueue() {
        if (queueExecutionJob?.isActive == true) {
            return // Antrean sudah berjalan, sequential processing tetap terjaga
        }

        queueExecutionJob = serviceScope.launch {
            while (isActive && !isCancelled) {
                val job = ConversionQueueManager.getNextWaitingJob() ?: break

                ConversionQueueManager.updateJobStatus(job.id, JobStatus.PROCESSING)
                updateNotificationForJob(job, 0, AppStrings.get().serviceStartingConversion)

                try {
                    val allUris = listOf(job.inputUri) + job.extraInputUris
                    val finalPath = if (allUris.size > 1 && job.targetFormat.equals("pdf", ignoreCase = true)) {
                        MorphEngine.mergeImagesToPdf(
                            context = this@MorphService,
                            inputUris = allUris,
                            originalFileName = job.originalFileName,
                            qualityPreset = job.qualityPreset,
                            onProgress = { stepMsg, percent ->
                                ConversionQueueManager.updateJobProgress(job.id, percent, stepMsg)
                                if (!MainActivity.isAppInForeground && !isCancelled) {
                                    updateNotificationForJob(job, percent, stepMsg)
                                }
                            }
                        )
                    } else {
                        MorphEngine.executeConversion(
                            context = this@MorphService,
                            inputUri = job.inputUri,
                            outputExtension = job.targetFormat,
                            originalFileName = job.originalFileName,
                            qualityPreset = job.qualityPreset,
                            onProgress = { stepMsg, percent ->
                                ConversionQueueManager.updateJobProgress(job.id, percent, stepMsg)
                                // Smart Notification: hanya update notifikasi di background saat aplikasi aktif
                                if (!MainActivity.isAppInForeground && !isCancelled) {
                                    updateNotificationForJob(job, percent, stepMsg)
                                }
                            }
                        )
                    }

                    ConversionQueueManager.updateJobStatus(job.id, JobStatus.COMPLETED, filePath = finalPath)
                } catch (e: CancellationException) {
                    ConversionQueueManager.updateJobStatus(job.id, JobStatus.CANCELLED)
                    if (isCancelled || !ConversionQueueManager.hasActiveOrWaitingJobs()) break
                } catch (e: Exception) {
                    Log.e("MorphService", "Gagal memproses job ${job.id}: ${job.originalFileName}", e)
                    ConversionQueueManager.updateJobStatus(
                        job.id,
                        JobStatus.FAILED,
                        error = e.message ?: AppStrings.get().queueFailed
                    )
                    // Sequential processing: Kegagalan 1 item TIDAK menghentikan item antrean berikutnya
                }
            }

            // Seluruh antrean selesai diproses
            handleQueueCompleted()
        }
    }

    private fun updateNotificationForJob(job: ConversionJob, progress: Int, message: String) {
        val summary = ConversionQueueManager.getQueueSummary()
        val total = summary.totalCount
        val currentIdx = summary.currentJobIndex

        val strings = AppStrings.get()
        val title = if (total > 1) {
            strings.notifConvertingBatchTitle(currentIdx, total, job.originalFileName)
        } else {
            strings.notifConvertingSingleTitle(job.originalFileName, job.targetFormat)
        }

        val content = "$message ($progress%)"
        val notification = buildNotification(title, content, true, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun handleQueueCompleted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        val summary = ConversionQueueManager.getQueueSummary()
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)

        // Tampilkan notifikasi ringkasan akhir HANYA jika berada di background dan tidak dibatalkan total
        if (!MainActivity.isAppInForeground && !isCancelled && summary.totalCount > 0) {
            val strings = AppStrings.get()
            val title = if (summary.failedCount == 0) {
                strings.notifSuccessTitle
            } else {
                strings.notifPartialTitle
            }

            val body = if (summary.failedCount == 0) {
                if (summary.completedCount == 1) strings.notifSuccessBodySingle
                else strings.notifSuccessBodyBatch(summary.completedCount)
            } else {
                strings.notifSummaryBody(summary.completedCount, summary.failedCount)
            }

            val finishNotif = buildNotification(title, body, false)
            manager.notify(SUMMARY_NOTIFICATION_ID, finishNotif)
        }

        stopSelf()
    }

    private fun stopForegroundAndService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        manager.cancel(SUMMARY_NOTIFICATION_ID)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        content: String,
        isProcessing: Boolean,
        progress: Int = 0
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
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
            .setSmallIcon(R.drawable.morph_logo_monochrome)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isProcessing) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
            builder.setOngoing(true)

            val cancelIntent = Intent(this, MorphService::class.java).apply {
                action = ACTION_CANCEL_CONVERSION
            }
            val cancelPendingIntent = PendingIntent.getService(
                this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, AppStrings.get().notifActionCancelAll, cancelPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Morph Conversions",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
