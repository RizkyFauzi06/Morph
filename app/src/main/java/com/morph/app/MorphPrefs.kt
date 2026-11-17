package com.morph.app

import android.content.Context

// --- PENGELOLA PREFERENSI & PENGATURAN (PBI 8.2) ---
class MorphPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("morph_settings", Context.MODE_PRIVATE)

    var themeOption: Int // 0: System, 1: Light, 2: Dark
        get() = prefs.getInt("theme", 0)
        set(value) = prefs.edit().putInt("theme", value).apply()

    var showNotifications: Boolean
        get() = prefs.getBoolean("notif", true)
        set(value) = prefs.edit().putBoolean("notif", value).apply()

    var keepOriginalName: Boolean
        get() = prefs.getBoolean("keep_name", true)
        set(value) = prefs.edit().putBoolean("keep_name", value).apply()

    var closeAfterShare: Boolean
        get() = prefs.getBoolean("close_share", true)
        set(value) = prefs.edit().putBoolean("close_share", value).apply()

    var qualityPreset: Int // 0: Tinggi, 1: Sedang, 2: Rendah, 3: Instan (Lossless)
        get() = prefs.getInt("quality", 0)
        set(value) = prefs.edit().putInt("quality", value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()
        
    var appLanguage: String // "id": Bahasa Indonesia, "en": English
        get() = prefs.getString("app_language", "id") ?: "id"
        set(value) = prefs.edit().putString("app_language", value).apply()
        
    var dismissBatteryWarning: Boolean
        get() = prefs.getBoolean("dismiss_battery_warning", false)
        set(value) = prefs.edit().putBoolean("dismiss_battery_warning", value).apply()
        
        var autoCheckUpdates: Boolean
        get() = prefs.getBoolean("auto_check_updates", true)
        set(value) = prefs.edit().putBoolean("auto_check_updates", value).apply()

    var dismissedUpdateTag: String
        get() = prefs.getString("dismissed_update_tag", "") ?: ""
        set(value) = prefs.edit().putString("dismissed_update_tag", value).apply()

    // PBI 5.1: SAF tree URI of the custom output folder; null = default Downloads/Morph
    var customOutputTreeUri: String?
        get() = prefs.getString("custom_output_tree_uri", null)
        set(value) = prefs.edit().putString("custom_output_tree_uri", value).apply()

    /**
     * Bitrate (bits/s) a hardware encoder really delivered on this firmware when it ignored -b:v,
     * stored as "<Build.FINGERPRINT>|<bps>". A firmware update changes the fingerprint, so the
     * encoder automatically gets a fresh chance.
     */
    fun hwEncoderCapBps(encoder: String, firmware: String): Long? {
        val raw = prefs.getString("hw_cap_$encoder", null) ?: return null
        val sep = raw.lastIndexOf('|')
        if (sep <= 0 || raw.substring(0, sep) != firmware) return null
        return raw.substring(sep + 1).toLongOrNull()
    }

    fun setHwEncoderCapBps(encoder: String, firmware: String, bps: Long) =
        prefs.edit().putString("hw_cap_$encoder", "$firmware|$bps").apply()

    var autoSubfolderPerFile: Boolean
        get() = prefs.getBoolean("auto_subfolder_per_file", false)
        set(value) = prefs.edit().putBoolean("auto_subfolder_per_file", value).apply()

    // Document URIs Morph wrote into SAF folders, so history never lists unrelated user files there
    var safOutputUris: Set<String>
        get() = prefs.getStringSet("saf_output_uris", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("saf_output_uris", value).apply()
}
