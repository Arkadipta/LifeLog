package com.lifelog.app.ui.undo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin conduit that exposes the singleton [UndoDeleteManager] to the app-level
 * snackbar host composable. The undo state itself lives in the manager, so this
 * ViewModel holds nothing that must survive configuration changes.
 */
@HiltViewModel
class UndoHostViewModel @Inject constructor(
    private val manager: UndoDeleteManager
) : ViewModel() {

    val pending: StateFlow<UndoDeleteManager.Pending?> = manager.pending

    fun undo(token: Long) = manager.undo(token)

    fun commit(token: Long) = manager.commit(token)
}
