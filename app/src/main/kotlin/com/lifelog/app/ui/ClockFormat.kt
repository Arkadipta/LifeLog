package com.lifelog.app.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The device's 12/24-hour preference, kept live for the whole composition.
 *
 * Provided once by `LifeLogTheme`; null means nobody provided it, which [is24HourFormat]
 * answers with a plain one-shot read rather than a wrong default. Static because the value
 * changes at most when the user visits system settings, so paying a full subtree
 * recomposition then is a better trade than tracking readers on every recomposition.
 */
val LocalIs24HourFormat = staticCompositionLocalOf<Boolean?> { null }

/**
 * Whether to render clocks in 24-hour form. Prefer this over calling
 * `DateFormat.is24HourFormat` directly: the result is only correct for as long as the
 * setting is untouched, and reading it inside a composable caches that answer for the
 * lifetime of the composition.
 */
@Composable
fun is24HourFormat(): Boolean =
    LocalIs24HourFormat.current ?: DateFormat.is24HourFormat(LocalContext.current)

/**
 * Reads the 12/24-hour setting and keeps watching it, so a flip in system settings reaches
 * the UI on return instead of waiting for the process to die.
 *
 * There is no configuration field for the hour format, so nothing recreates the activity
 * or invalidates a composition when it changes — which is why the value has to be observed
 * rather than read once. `TIME_12_24` is unset until the user picks explicitly (the format
 * then follows the locale, whose change *is* a configuration change), and both the first
 * pick and every later flip write the setting, so the URI covers every transition.
 *
 * Call this once near the root and publish the result through [LocalIs24HourFormat]:
 * clocks are per-list-row, and an observer registered per row would churn a binder
 * registration on every scroll.
 */
@Composable
fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    var is24Hour by remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                is24Hour = DateFormat.is24HourFormat(context)
            }
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            observer
        )
        // Re-read now that a later change would be heard: the setting could have moved
        // between the initial read above and the observer going live.
        is24Hour = DateFormat.is24HourFormat(context)

        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return is24Hour
}
