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
}
