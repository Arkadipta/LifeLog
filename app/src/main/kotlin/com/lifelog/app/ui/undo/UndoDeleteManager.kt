package com.lifelog.app.ui.undo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide coordinator for the shared "delete → snackbar → undo / commit" flow used
 * by every deletable object (events, entries, charts, reminders).
 *
 * Deletion is two-phase. [delete] runs immediately — the row leaves the database, so
 * the item disappears from every reactive list — and an undo offer is published as
 * [pending]. The app-level snackbar host renders that offer: tapping Undo calls [undo]
 * (which re-inserts the captured snapshot), while letting the snackbar expire or
 * dismissing it calls [commit] (a no-op finalize, since the row is already gone).
 *
 * State lives in this @Singleton rather than in composition, so it survives
 * configuration changes and screen navigation — e.g. deleting an event navigates back
 * to the list, yet the snackbar persists. Delete and restore run on an app-scoped
 * [scope] so they cannot be cancelled when the calling ViewModel is cleared.
 *
 * A monotonic [token] makes [undo] and [commit] idempotent: superseded or duplicate
 * callbacks no-op, so a deletion can never be restored twice nor committed twice.
 */
@Singleton
class UndoDeleteManager @Inject constructor() {

    data class Pending(
        val token: Long,
        val message: String,
        val actionLabel: String,
        val restore: suspend () -> Unit
    )

    // Main.immediate keeps token bookkeeping single-threaded and lets DB work (Room
    // suspend DAOs are main-safe) outlive the ViewModel that requested the delete.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var tokenCounter = 0L

    /**
     * Soft-delete [delete] now and offer an undo snackbar. [delete] returns a snapshot
     * of whatever it removed; if the user undoes, that snapshot is handed back to
     * [restore]. Replacing an existing offer commits the previous one (its row is
     * already gone), so only the most recent deletion is undoable at a time.
     */
    fun <T> delete(
        message: String,
        actionLabel: String = "Undo",
        delete: suspend () -> T,
        restore: suspend (T) -> Unit
    ) {
        scope.launch {
            val snapshot = delete()
            val token = ++tokenCounter
            _pending.value = Pending(token, message, actionLabel) { restore(snapshot) }
        }
    }

    /** User tapped Undo: re-insert the snapshot exactly once. */
    fun undo(token: Long) {
        val current = _pending.value
        if (current == null || current.token != token) return
        _pending.value = null
        scope.launch { current.restore() }
    }

    /** Snackbar expired or was dismissed: finalize. The row is already gone, so clear. */
    fun commit(token: Long) {
        _pending.update { if (it?.token == token) null else it }
    }
}
