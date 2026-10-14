package com.anatdx.yukifb.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.anatdx.yukifb.state.BackGestureSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** UI-only progress shared by a back handler and a [YukiFBScene]. */
@Stable
class YukiFBBackState internal constructor() {
    /** Current gesture fraction; observing it in a draw layer avoids list recomposition. */
    var progress: Float by mutableFloatStateOf(0f)
        internal set
    /** True while previewing or settling a gesture. */
    var isActive: Boolean by mutableStateOf(false)
        internal set
    internal var committing by mutableStateOf(false)
    internal var completion by mutableFloatStateOf(0f)
    internal var generation by mutableIntStateOf(0)
    internal var direction = 1
    internal val session = BackGestureSession()
    private var settle: Job? = null
    private var settleToken: Any? = null

    internal fun start(key: Any, edge: Int, preview: Boolean) {
        settleToken = null
        settle?.cancel()
        generation++
        session.begin(key)
        direction = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
        progress = 0f
        completion = 0f
        committing = false
        isActive = preview
    }

    internal fun finish(
        scope: CoroutineScope,
        commit: Boolean,
        animateMotion: Boolean,
        onFinished: () -> Unit = {},
    ) {
        settleToken = null
        settle?.cancel()
        committing = commit && isActive
        if (!isActive || !animateMotion) {
            try { onFinished() } finally { reset() }
            return
        }
        val from = progress
        val token = Any().also { settleToken = it }
        settle = scope.launch {
            try {
                animate(0f, 1f, animationSpec = tween(120, easing = LinearEasing)) { fraction, _ ->
                    completion = if (commit) fraction else 0f
                    progress = if (commit) from + (1f - from) * fraction else from * (1f - fraction)
                }
                onFinished()
                // Let the new scene consume the handoff before clearing gesture state.
                if (commit) { withFrameNanos {}; withFrameNanos {} }
            } finally {
                if (settleToken === token) {
                    settleToken = null
                    reset()
                }
            }
        }
    }

    internal fun reset() {
        progress = 0f
        completion = 0f
        isActive = false
        committing = false
    }

    internal fun dispose() {
        settleToken = null
        settle?.cancel()
        settle = null
        // Retained presentation can register a new handler without a gesture-start event.
        session.reset()
        reset()
    }
}

/** Creates gesture state whose lifetime is the enclosing browser or host scene. */
@Composable
fun rememberYukiFBBackState(): YukiFBBackState = remember { YukiFBBackState() }

/**
 * Explicitly handles back within the nearest host NavigationEvent dispatcher.
 * [targetKey] must change when the intended action or its document changes.
 * [previewEnabled] should be false for actions that require confirmation.
 * Existing FileBrowser overloads never install this handler implicitly.
 */
@Composable
fun YukiFBBackHandler(
    state: YukiFBBackState,
    targetKey: Any,
    enabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    previewEnabled: Boolean = true,
    onBack: () -> Unit,
) {
    val owner = LocalNavigationEventDispatcherOwner.current ?: return
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onBack)
    val key = rememberUpdatedState(targetKey)
    val enabledState = rememberUpdatedState(enabled)
    val motion = LocalYukiFBMotion.current
    val systemAnimations = rememberSystemAnimationsEnabled()
    val preview = rememberUpdatedState(predictiveBackEnabled && previewEnabled && systemAnimations && motion != YukiFBMotion.None)
    val handler = remember(owner, state, scope) {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = enabled) {
            override fun onBackStarted(event: NavigationEvent) {
                if (!state.committing) state.start(key.value, event.swipeEdge, preview.value)
            }
            override fun onBackProgressed(event: NavigationEvent) {
                if (state.committing) return
                if (enabledState.value && state.session.update(key.value)) {
                    if (state.isActive) state.progress = event.progress.coerceIn(0f, 1f)
                } else {
                    state.finish(scope, commit = false, animateMotion = preview.value)
                }
            }
            override fun onBackCancelled() {
                if (state.committing) return
                state.session.cancel()
                state.finish(scope, commit = false, animateMotion = preview.value)
                state.session.reset()
            }
            override fun onBackCompleted() {
                if (state.committing) return
                val target = key.value
                val action = callback.value
                val accepted = enabledState.value && state.session.complete(target)
                state.finish(scope, commit = accepted, animateMotion = preview.value) {
                    try {
                        if (accepted && enabledState.value && key.value == target) action()
                    } finally {
                        state.session.reset()
                    }
                }
            }
        }
    }
    SideEffect {
        handler.isBackEnabled = enabled
        state.session.update(targetKey)
    }
    DisposableEffect(owner, handler) {
        owner.navigationEventDispatcher.addHandler(handler)
        onDispose {
            handler.remove()
            state.dispose()
        }
    }
}
