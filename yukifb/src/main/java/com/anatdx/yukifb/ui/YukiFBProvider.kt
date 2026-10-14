package com.anatdx.yukifb.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/** The host's presentation style; neither choice installs a Material theme. */
enum class YukiFBStyle {
    /** Compact, conventional Material 3 surfaces. */
    Classic,
    /** Segmented surfaces, stateful shapes and expressive motion. */
    Expressive,
}

/** Motion policy, additionally constrained by the system animation setting. */
enum class YukiFBMotion {
    /** Use the host Material motion scheme and spatial transitions. */
    Full,
    /** Use brief fades without translation, scaling or spring overshoot. */
    Reduced,
    /** Change presentation immediately. */
    None,
}

/**
 * Container colors. Transparency never changes the opacity of text or icons.
 * @property background Browser and scene background.
 * @property listContainer Unselected row surface.
 * @property selectedContainer Selected row surface.
 * @property topBar Navigation, search and selection header.
 * @property bottomBar Selection and clipboard action surface.
 * @property editor Plain-text editing surface.
 */
@Immutable
data class YukiFBColors(
    val background: Color,
    val listContainer: Color,
    val selectedContainer: Color,
    val topBar: Color,
    val bottomBar: Color,
    val editor: Color,
)

/** Renders an icon while preserving its description, size and semantic color. */
typealias YukiFBIconRenderer = @Composable (ImageVector, String?, Modifier, Color) -> Unit

/** Defaults derived from the enclosing host theme. */
object YukiFBThemeDefaults {
    /** Resolves the surface palette without creating a nested Material theme. */
    @Composable
    fun colors(): YukiFBColors = with(MaterialTheme.colorScheme) {
        YukiFBColors(
            background = background,
            listContainer = surfaceContainer,
            selectedContainer = secondaryContainer,
            topBar = surface,
            bottomBar = surfaceContainerHigh,
            editor = surfaceContainerLowest,
        )
    }
}

internal val LocalYukiFBStyle = staticCompositionLocalOf { YukiFBStyle.Expressive }
internal val LocalYukiFBColors = staticCompositionLocalOf<YukiFBColors?> { null }
internal val LocalYukiFBMotion = staticCompositionLocalOf { YukiFBMotion.Full }
internal val LocalYukiFBSceneBackground = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
private val LocalYukiFBIcon = staticCompositionLocalOf<YukiFBIconRenderer?> { null }

/**
 * Adapts YukiFB to a host. Typography, shapes and motion specs come from the
 * enclosing MaterialTheme. The default style retains YukiFB's expressive UI.
 */
@Composable
fun YukiFBProvider(
    style: YukiFBStyle = YukiFBStyle.Expressive,
    colors: YukiFBColors = YukiFBThemeDefaults.colors(),
    motion: YukiFBMotion = YukiFBMotion.Full,
    icon: YukiFBIconRenderer? = null,
    content: @Composable () -> Unit,
) {
    val animationsEnabled = rememberSystemAnimationsEnabled()
    CompositionLocalProvider(
        LocalYukiFBStyle provides style,
        LocalYukiFBColors provides colors,
        LocalYukiFBMotion provides if (animationsEnabled) motion else YukiFBMotion.None,
        LocalYukiFBIcon provides icon,
        content = content,
    )
}

/**
 * Supplies the host background inside each moving scene, including custom imagery.
 * [sceneBackground] must fill its bounds with an opaque backing before drawing any
 * translucent decoration. Container alpha remains independent of this backing.
 * The original overload remains available for hosts without a background renderer.
 */
@Composable
fun YukiFBProvider(
    sceneBackground: @Composable () -> Unit,
    style: YukiFBStyle = YukiFBStyle.Expressive,
    colors: YukiFBColors = YukiFBThemeDefaults.colors(),
    motion: YukiFBMotion = YukiFBMotion.Full,
    icon: YukiFBIconRenderer? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalYukiFBSceneBackground provides sceneBackground) {
        YukiFBProvider(style, colors, motion, icon, content)
    }
}

internal val yukiFBColors: YukiFBColors
    @Composable get() = LocalYukiFBColors.current ?: YukiFBThemeDefaults.colors()

internal val expressiveYukiFB: Boolean
    @Composable get() = LocalYukiFBStyle.current == YukiFBStyle.Expressive

@Composable
internal fun rememberSystemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = read() }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> yukiFBSpatialSpec(): FiniteAnimationSpec<T> = when (LocalYukiFBMotion.current) {
    YukiFBMotion.None -> snap()
    YukiFBMotion.Reduced -> snap()
    YukiFBMotion.Full -> MaterialTheme.motionScheme.defaultSpatialSpec()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> yukiFBEffectsSpec(): FiniteAnimationSpec<T> = when (LocalYukiFBMotion.current) {
    YukiFBMotion.None -> snap()
    YukiFBMotion.Reduced -> tween(120)
    YukiFBMotion.Full -> MaterialTheme.motionScheme.fastEffectsSpec()
}

@Composable
internal fun BrowserIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val renderer = LocalYukiFBIcon.current
    if (renderer == null) Icon(imageVector, contentDescription, modifier, tint)
    else renderer(imageVector, contentDescription, modifier, tint)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BrowserLoadingIndicator(modifier: Modifier = Modifier) {
    if (expressiveYukiFB) LoadingIndicator(modifier = modifier)
    else CircularProgressIndicator(modifier = modifier)
}

@Composable
internal fun BrowserAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val expressive = expressiveYukiFB
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = {
            if (expressive) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), title)
            } else title()
        },
        text = text,
        shape = if (expressive) MaterialTheme.shapes.extraLarge else AlertDialogDefaults.shape,
    )
}
