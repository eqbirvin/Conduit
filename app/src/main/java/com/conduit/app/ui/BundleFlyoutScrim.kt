package com.conduit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.conduit.app.data.ConduitSettings

/** Blur applied behind the open bundle flyout (Android 12+ only, when enabled in settings). */
fun bundleFlyoutBlurModifier(settings: ConduitSettings, isBundleFlyoutExpanded: Boolean): Modifier =
    if (isBundleFlyoutExpanded && settings.dockExpandButtonVisible && settings.bundleMenuBlur &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    ) {
        Modifier.blur(20.dp)
    } else {
        Modifier
    }

/**
 * Shade drawn behind the open bundle flyout. The top edge fades in across the status bar
 * so the shading has no hard line at the top of the screen.
 */
@Composable
fun BundleFlyoutScrim(
    visible: Boolean,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fadeHeightPx = with(density) {
        maxOf(WindowInsets.statusBars.getTop(density).toFloat(), 16.dp.toPx())
    }
    val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.40f)
        else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.65f)
    val scrimBrush = remember(scrimColor, fadeHeightPx) {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, scrimColor),
            startY = 0f,
            endY = fadeHeightPx
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush)
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )
    }
}
