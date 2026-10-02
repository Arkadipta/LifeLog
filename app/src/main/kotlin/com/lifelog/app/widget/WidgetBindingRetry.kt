package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.lifelog.app.util.logD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles an APPWIDGET_UPDATE in which some ids are not bound to their provider yet: renders the
 * bound ones now and the unbound ones after [RETRY_DELAY_MS], all under the broadcast's
 * `goAsync` token.
 *
 * The token is the point. The retry outlives `onUpdate` by seconds, and a process whose receiver
 * has returned with nothing else running is cached — killable at any moment — so without the
 * token the retry was a promise the process was not obliged to live long enough to keep. And
 * since `updatePeriodMillis=0`, nothing would ever ask again.
 *
 * A broadcast has exactly ONE token: `goAsync()` returns it and nulls the receiver's copy, and
 * `GlanceAppWidgetReceiver.onUpdate` takes it unconditionally (then dereferences it, so calling
 * super after this crashes). That is why the bound ids are rendered here rather than handed to
 * super alongside — whichever side finished first would release the process under the other.
 * `GlanceAppWidget.update(context, GlanceId)` is the public face of the same call super makes;
 * the one thing skipped is super's receiver→provider bookkeeping, which every unmixed update and
 * every onAppWidgetOptionsChanged still performs.
 */
internal fun BroadcastReceiver.updateWithBindingRetry(
    context: Context,
    widget: GlanceAppWidget,
    tag: String,
    boundIds: List<Int>,
    unboundIds: List<Int>,
) {
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Holding the token past the broadcast timeout is an ANR, which is worse than the
            // lost update this exists to prevent — so the hold is bounded, not open-ended.
            withTimeoutOrNull(TOKEN_HOLD_LIMIT_MS) {
                boundIds.forEach { id -> widget.updateLogged(context, id, tag) }

                delay(RETRY_DELAY_MS)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                unboundIds.forEach { id ->
                    if (appWidgetManager.getAppWidgetInfo(id) != null) {
                        widget.updateLogged(context, id, tag)
                    } else {
                        Log.e(tag, "onUpdate retry: appWidgetId=$id still not bound after ${RETRY_DELAY_MS}ms — giving up")
                    }
                }
            } ?: Log.w(tag, "onUpdate retry: gave up after ${TOKEN_HOLD_LIMIT_MS}ms to release the broadcast")
        } finally {
            try {
                pending?.finish()
            } catch (e: IllegalStateException) {
                // Some OEM builds report "Broadcast already finished" here; Glance guards the
                // same call for the same reason.
                Log.w(tag, "onUpdate retry: broadcast was already finished", e)
            }
        }
    }
}

private suspend fun GlanceAppWidget.updateLogged(context: Context, appWidgetId: Int, tag: String) {
    try {
        update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
        logD(tag) { "onUpdate: update complete for appWidgetId=$appWidgetId" }
    } catch (e: CancellationException) {
        throw e // the hold limit; swallowing it would keep the token past the deadline
    } catch (e: Exception) {
        Log.e(tag, "onUpdate: update failed for appWidgetId=$appWidgetId", e)
    }
}

private const val RETRY_DELAY_MS = 3_000L

// Comfortably inside the 10s a foreground broadcast is allowed, with room for the renders
// either side of the delay.
private const val TOKEN_HOLD_LIMIT_MS = 8_000L
