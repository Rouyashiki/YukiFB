package com.anatdx.yukifb.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

private class SceneIdentity(var key: Any)

/**
 * Animates one live scene and an optional non-interactive [backPreview].
 * [sceneKey] is an opaque stable identity; [direction] is negative for a return.
 * Previews must not start backend work. Outgoing scenes remain live until their
 * back handler finishes settling; recorded child layers are never reused as snapshots.
 */
@Composable
fun YukiFBScene(
    sceneKey: Any,
    modifier: Modifier = Modifier,
    direction: Int = 1,
    backState: YukiFBBackState? = null,
    backPreview: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val identity = remember { SceneIdentity(sceneKey) }
    val handoffGeneration = remember(sceneKey) { backState?.takeIf { it.committing }?.generation }
    val entrance = remember(sceneKey) {
        Animatable(if (identity.key == sceneKey || handoffGeneration != null) 1f else 0f)
    }
    SideEffect { identity.key = sceneKey }
    val motion = LocalYukiFBMotion.current
    val expressive = expressiveYukiFB
    val layer = rememberGraphicsLayer()
    val shape = MaterialTheme.shapes.extraLarge
    val background = yukiFBColors.background
    val sceneBackground = LocalYukiFBSceneBackground.current
    val backing = background.compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 1f))
    val gesture = backState?.takeIf { it.isActive && it.generation != handoffGeneration }
    LaunchedEffect(entrance, motion, gesture?.generation) {
        if (motion == YukiFBMotion.None || gesture != null) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(if (expressive) 200 else 160, easing = FastOutSlowInEasing))
    }

    Box(modifier.background(background)) {
        sceneBackground?.invoke()
        if (gesture != null && backPreview != null) {
            Box(Modifier.fillMaxSize().blockPreviewInput().graphicsLayer {
                if (motion == YukiFBMotion.Full) {
                    translationX = -size.width * gesture.direction * 0.025f * (1f - gesture.progress)
                }
            }) { backPreview() }
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                layer.record {
                    if (sceneBackground != null || gesture != null || entrance.value < 1f) drawRect(backing)
                    this@drawWithContent.drawContent()
                }
                layer.apply {
                    scaleX = 1f; scaleY = 1f; translationX = 0f
                    alpha = 1f; clip = false; shadowElevation = 0f
                }
                if (gesture != null) {
                    drawRect(Color.Black, alpha = 0.24f * (1f - gesture.progress))
                    if (motion == YukiFBMotion.Full) {
                        layer.apply {
                            val p = gesture.progress
                            scaleX = 1f - 0.2f * p; scaleY = scaleX
                            val offset = 0.075f * p
                            // Keep the outgoing page opaque while revealing the parent spatially.
                            translationX = size.width * gesture.direction *
                                (offset + (1f - offset) * gesture.completion)
                            setOutline(shape.createOutline(this@drawWithContent.size, layoutDirection, this@drawWithContent))
                            clip = true
                            shadowElevation = 16f * density * p
                        }
                    }
                } else if (motion == YukiFBMotion.Full) {
                    layer.translationX = size.width * direction * (if (expressive) 0.08f else 0.04f) *
                        (1f - entrance.value.coerceIn(0f, 1f))
                } else if (motion == YukiFBMotion.Reduced) {
                    layer.alpha = entrance.value.coerceIn(0f, 1f)
                }
                drawLayer(layer)
            },
        ) {
            sceneBackground?.invoke()
            content()
        }
    }
}

internal fun Modifier.blockPreviewInput(): Modifier = clearAndSetSemantics {}
    .focusProperties {
        canFocus = false
        onEnter = { cancelFocusChange() }
    }
    .focusGroup()
    .onPreviewKeyEvent { true }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
