package com.morph.app.core

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

enum class JobStatus {
    WAITING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ConversionJob(
    val id: String = UUID.randomUUID().toString(),
    val inputUri: Uri,
    val originalFileName: String,
    val mimeType: String,
    val targetFormat: String,
    val qualityPreset: Int,
    val status: JobStatus = JobStatus.WAITING,
    val progress: Int = 0,
    val progressMessage: String = AppStrings.get().queueWaiting,
    val outputFilePath: String? = null,
    val errorMessage: String? = null,
    val extraInputUris: List<Uri> = emptyList()
)

data class ConversionJobRequest(
    val inputUri: Uri,
    val originalFileName: String,
    val mimeType: String,
    val targetFormat: String,
    val qualityPreset: Int,
    val extraInputUris: List<Uri> = emptyList()
)

data class SelectedFileItem(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L
)

data class QueueSummary(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val cancelledCount: Int,
    val waitingCount: Int,
    val activeJob: ConversionJob?,
    val currentJobIndex: Int // 1-based index
)

object ConversionQueueManager {

    // Single Source of Truth untuk seluruh antrean pekerjaan
    private val _queueState = MutableStateFlow<List<ConversionJob>>(emptyList())
    val queueState: StateFlow<List<ConversionJob>> = _queueState.asStateFlow()

    // Listener ketika pekerjaan yang sedang berjalan dibatalkan
    var onCancelActiveConversion: (() -> Unit)? = null

    fun addJob(
        inputUri: Uri,
        originalFileName: String,
        mimeType: String,
        targetFormat: String,
        qualityPreset: Int,
        extraInputUris: List<Uri> = emptyList()
    ): String {
        // Bersihkan riwayat antrean sesi sebelumnya jika antrean sedang menganggur
        if (!hasActiveOrWaitingJobs()) {
            _queueState.value = emptyList()
        }

        val newJob = ConversionJob(
            inputUri = inputUri,
            originalFileName = originalFileName,
            mimeType = mimeType,
            targetFormat = targetFormat,
            qualityPreset = qualityPreset,
            extraInputUris = extraInputUris
        )
        _queueState.update { currentList -> currentList + newJob }
        return newJob.id
    }

    fun addJobs(requests: List<ConversionJobRequest>): List<String> {
        // Bersihkan riwayat antrean sesi sebelumnya jika antrean sedang menganggur
        if (!hasActiveOrWaitingJobs()) {
            _queueState.value = emptyList()
        }

        val newJobs = requests.map { req ->
            ConversionJob(
                inputUri = req.inputUri,
                originalFileName = req.originalFileName,
                mimeType = req.mimeType,
                targetFormat = req.targetFormat,
                qualityPreset = req.qualityPreset,
                extraInputUris = req.extraInputUris
            )
        }
        _queueState.update { currentList -> currentList + newJobs }
        return newJobs.map { it.id }
    }

    fun updateJobStatus(jobId: String, newStatus: JobStatus, filePath: String? = null, error: String? = null) {
        _queueState.update { currentList ->
            currentList.map { job ->
                if (job.id == jobId) {
                    val finalProgress = when (newStatus) {
                        JobStatus.COMPLETED -> 100
                        JobStatus.FAILED, JobStatus.CANCELLED -> job.progress // Bekukan progress saat error/batal
                        JobStatus.WAITING -> 0
                        JobStatus.PROCESSING -> job.progress
                    }
                    val strings = AppStrings.get()
                    val finalMsg = when (newStatus) {
                        JobStatus.COMPLETED -> strings.queueCompleted
                        JobStatus.FAILED -> error ?: strings.queueFailed
                        JobStatus.CANCELLED -> strings.queueCancelled
                        JobStatus.WAITING -> strings.queueWaiting
                        JobStatus.PROCESSING -> job.progressMessage
                    }

                    job.copy(
                        status = newStatus,
                        progress = finalProgress,
                        progressMessage = finalMsg,
                        outputFilePath = filePath ?: job.outputFilePath,
                        errorMessage = error ?: job.errorMessage
                    )
                } else {
                    job
                }
            }
        }
    }

    fun updateJobProgress(jobId: String, percentage: Int, message: String) {
        _queueState.update { currentList ->
            currentList.map { job ->
                if (job.id == jobId && job.status == JobStatus.PROCESSING) {
                    job.copy(progress = percentage.coerceIn(0, 100), progressMessage = message)
                } else {
                    job
                }
            }
        }
    }

    // Mendapatkan pekerjaan berikutnya yang harus dieksekusi
    fun getNextWaitingJob(): ConversionJob? {
        return _queueState.value.firstOrNull { it.status == JobStatus.WAITING }
    }

    // Mendapatkan pekerjaan yang sedang berjalan
    fun getActiveJob(): ConversionJob? {
        return _queueState.value.firstOrNull { it.status == JobStatus.PROCESSING }
    }

    // Memeriksa apakah masih ada pekerjaan yang sedang berjalan atau menunggu
    fun hasActiveOrWaitingJobs(): Boolean {
        return _queueState.value.any { it.status == JobStatus.PROCESSING || it.status == JobStatus.WAITING }
    }

    // Membatalkan satu pekerjaan spesifik
    fun cancelJob(jobId: String) {
        val jobToCancel = _queueState.value.find { it.id == jobId }
        if (jobToCancel != null && (jobToCancel.status == JobStatus.WAITING || jobToCancel.status == JobStatus.PROCESSING)) {
            val wasProcessing = jobToCancel.status == JobStatus.PROCESSING
            updateJobStatus(jobId, JobStatus.CANCELLED)
            if (wasProcessing) {
                onCancelActiveConversion?.invoke()
            }
        }
    }

    // Membatalkan seluruh pekerjaan antrean
    fun cancelAllJobs() {
        val hadProcessing = _queueState.value.any { it.status == JobStatus.PROCESSING }
        _queueState.update { currentList ->
            currentList.map { job ->
                if (job.status == JobStatus.WAITING || job.status == JobStatus.PROCESSING) {
                    job.copy(status = JobStatus.CANCELLED, progressMessage = AppStrings.get().queueCancelled)
                } else {
                    job
                }
            }
        }
        if (hadProcessing) {
            onCancelActiveConversion?.invoke()
        }
    }

    // Bersihkan daftar yang sudah selesai/gagal/batal
    fun clearCompletedJobs() {
        _queueState.update { currentList ->
            currentList.filter { it.status == JobStatus.WAITING || it.status == JobStatus.PROCESSING }
        }
    }

    // Hapus satu job tertentu (misalnya dismiss error card)
    fun dismissJob(jobId: String) {
        _queueState.update { currentList ->
            currentList.filterNot { it.id == jobId }
        }
    }

    fun clearAll() {
        cancelAllJobs()
        _queueState.value = emptyList()
    }

    fun getQueueSummary(): QueueSummary {
        val list = _queueState.value
        val active = list.firstOrNull { it.status == JobStatus.PROCESSING }
        val activeIndex = if (active != null) {
            list.indexOfFirst { it.id == active.id } + 1
        } else {
            val nextWaiting = list.firstOrNull { it.status == JobStatus.WAITING }
            if (nextWaiting != null) list.indexOfFirst { it.id == nextWaiting.id } + 1 else 0
        }

        return QueueSummary(
            totalCount = list.size,
            completedCount = list.count { it.status == JobStatus.COMPLETED },
            failedCount = list.count { it.status == JobStatus.FAILED },
            cancelledCount = list.count { it.status == JobStatus.CANCELLED },
            waitingCount = list.count { it.status == JobStatus.WAITING },
            activeJob = active,
            currentJobIndex = activeIndex
        )
    }
}
