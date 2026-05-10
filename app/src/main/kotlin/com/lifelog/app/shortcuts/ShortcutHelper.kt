package com.lifelog.app.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.lifelog.app.MainActivity
import com.lifelog.app.R

object ShortcutHelper {

    // IDs declared in shortcuts.xml are static/manifest shortcuts — the OS registers them
    // automatically and forbids touching them via ShortcutManager APIs.
    // Only use this function for additional *dynamic* shortcuts (different IDs).
    fun setupShortcuts(context: Context) {
        // Static shortcuts in res/xml/shortcuts.xml are auto-registered by the manifest.
        // Nothing to do here — calling setDynamicShortcuts with those IDs would crash.
    }
}
