package com.morph.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.morph.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateChecker {

    // Sesuaikan dengan username/repo GitHub kamu
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/RizkyFauzi06/Morph/releases/latest"

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun checkForUpdates(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        // Langsung berhenti dalam < 1ms jika Mode Pesawat aktif / tidak ada jaringan
        if (!isNetworkAvailable(context)) {
            return@withContext UpdateCheckResult.Error("Offline")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(LATEST_RELEASE_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Morph-Android/${BuildConfig.VERSION_NAME}")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("HTTP ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return@withContext UpdateCheckResult.UpToDate
            }

            val tagName = json.optString("tag_name", "").trim()
            val htmlUrl = json.optString("html_url", "").trim()
            val title = json.optString("name", tagName).trim()
            val notes = json.optString("body", "").trim()

            if (tagName.isEmpty() || htmlUrl.isEmpty()) {
                return@withContext UpdateCheckResult.UpToDate
            }

            if (isNewerVersion(remoteTag = tagName, currentVersion = BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.UpdateAvailable(
                    ReleaseInfo(
                        tagName = tagName,
                        title = title,
                        releaseNotes = notes,
                        htmlUrl = htmlUrl
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    internal fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = remoteTag.removePrefix("v").removePrefix("V")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }

        val currentParts = currentVersion.removePrefix("v").removePrefix("V")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}