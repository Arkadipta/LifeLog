package com.lifelog.app.util

import android.util.Log
import com.lifelog.app.BuildConfig

/**
 * Debug-only diagnostic logging.
 *
 * The widget/Glance paths are chatty by necessity — a homescreen widget can only be
 * observed from logcat — but those traces name the user's events (`eventName='…'`) and
 * fire on every render, so they have no business in a shipped build.
 *
 * [BuildConfig.DEBUG] is the gate rather than a ProGuard `-assumenosideeffects` rule
 * alone for two reasons: it still holds in an un-minified build, and taking the message
 * as a lambda means the string is never *built* in release rather than built and thrown
 * away. AGP emits `DEBUG` as a non-constant field so both branches compile; R8 folds it
 * to `false` and drops the block entirely from the release DEX.
 *
 * Warnings and errors deliberately keep calling [Log] directly — they report real
 * failures and are worth having in a release logcat when a user reports a bug.
 */
inline fun logD(tag: String, message: () -> String) {
    if (BuildConfig.DEBUG) Log.d(tag, message())
}
