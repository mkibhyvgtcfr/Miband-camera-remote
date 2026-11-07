package com.miband.camremote.data

import android.content.Context
import androidx.core.content.edit

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("miband_camera", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit { putBoolean("dark_theme", value) }

    var gridOverlay: Boolean
        get() = prefs.getBoolean("grid", true)
        set(value) = prefs.edit { putBoolean("grid", value) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(value) = prefs.edit { putBoolean("keep_screen_on", value) }

    var autoDownloadCapture: Boolean
        get() = prefs.getBoolean("auto_download", false)
        set(value) = prefs.edit { putBoolean("auto_download", value) }

    var bandEnabled: Boolean
        get() = prefs.getBoolean("band_enabled", true)
        set(value) = prefs.edit { putBoolean("band_enabled", value) }

    var lastHost: String
        get() = prefs.getString("last_host", "192.168.1.1").orEmpty()
        set(value) = prefs.edit { putString("last_host", value) }

    var lastPort: Int
        get() = prefs.getInt("last_port", 15740)
        set(value) = prefs.edit { putInt("last_port", value) }

    var lastMode: String
        get() = prefs.getString("last_mode", "PTPIP").orEmpty()
        set(value) = prefs.edit { putString("last_mode", value) }
}
