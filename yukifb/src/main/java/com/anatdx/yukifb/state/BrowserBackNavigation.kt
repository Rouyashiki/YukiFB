package com.anatdx.yukifb.state

/** Resolves only browser-owned navigation; a null result belongs to the host. */
internal fun browserBackAction(state: FileBrowserUiState): FileBrowserAction? = when {
    state.isBusy || state.isLoading -> null
    state.isSelectionMode -> FileBrowserAction.ClearSelection
    state.search != null -> FileBrowserAction.StopSearch
    state.breadcrumbs.size > 1 -> FileBrowserAction.NavigateUp
    else -> null
}

/** A gesture may commit only to the target it started with, and only once. */
internal class BackGestureSession {
    private enum class Phase { Idle, Tracking, Invalid, Finished }
    private var phase = Phase.Idle
    private var target: Any? = null

    fun begin(key: Any) {
        target = key
        phase = Phase.Tracking
    }

    fun update(key: Any): Boolean {
        if (phase == Phase.Tracking && key != target) phase = Phase.Invalid
        return phase == Phase.Tracking
    }

    fun complete(key: Any): Boolean {
        val accepted = phase == Phase.Idle || (phase == Phase.Tracking && key == target)
        phase = Phase.Finished
        return accepted
    }

    fun cancel() { phase = Phase.Finished }

    fun reset() {
        phase = Phase.Idle
        target = null
    }
}
