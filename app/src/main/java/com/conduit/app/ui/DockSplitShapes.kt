package com.conduit.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Creates the convex semicircular split shape with flat 4dp inner corners on the left and a smooth
 * semicircular arc on the right. Used by the expand button on the right, or the dock packages pill
 * when the expand button is positioned on the left.
 */
@Composable
fun rememberRightSplitButtonShape(dockCapRadius: Dp): Shape {
    return remember(dockCapRadius) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val rLeft = with(density) { 4.dp.toPx() }
                val rRight = size.height / 2f
                val path = Path().apply {
                    moveTo(rLeft, 0f)
                    val rightArcStartX = (size.width - rRight).coerceAtLeast(rLeft)
                    lineTo(rightArcStartX, 0f)
                    arcTo(
                        rect = Rect(
                            left = size.width - 2f * rRight,
                            top = 0f,
                            right = size.width,
                            bottom = size.height
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(rLeft, size.height)
                    arcTo(
                        rect = Rect(
                            left = 0f,
                            top = size.height - 2f * rLeft,
                            right = 2f * rLeft,
                            bottom = size.height
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(0f, rLeft)
                    arcTo(
                        rect = Rect(
                            left = 0f,
                            top = 0f,
                            right = 2f * rLeft,
                            bottom = 2f * rLeft
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }
                return Outline.Generic(path)
            }
        }
    }
}

/**
 * Creates the convex semicircular split shape with a smooth semicircular arc on the left and flat
 * 4dp inner corners on the right. Used by the expand button on the left, or the dock packages pill
 * when the expand button is positioned on the right.
 */
@Composable
fun rememberLeftSplitButtonShape(dockCapRadius: Dp): Shape {
    return remember(dockCapRadius) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val rRight = with(density) { 4.dp.toPx() }
                val rLeft = size.height / 2f
                val path = Path().apply {
                    moveTo(size.width - rRight, 0f)
                    arcTo(
                        rect = Rect(
                            left = size.width - 2f * rRight,
                            top = 0f,
                            right = size.width,
                            bottom = 2f * rRight
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(size.width, size.height - rRight)
                    arcTo(
                        rect = Rect(
                            left = size.width - 2f * rRight,
                            top = size.height - 2f * rRight,
                            right = size.width,
                            bottom = size.height
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    val leftArcBottomX = rLeft.coerceAtMost(size.width - rRight)
                    lineTo(leftArcBottomX, size.height)
                    arcTo(
                        rect = Rect(
                            left = 0f,
                            top = 0f,
                            right = 2f * rLeft,
                            bottom = size.height
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(size.width - rRight, 0f)
                    close()
                }
                return Outline.Generic(path)
            }
        }
    }
}
