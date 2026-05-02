package com.cliffracertech.soundaura.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class CardVisualStyle { Solid, FrostedGlass }

data class CardAppearance(
    val style: CardVisualStyle,
    val containerColor: Color,
    val borderColor: Color,
    val imageOverlayColor: Color,
) {
    val border get() =
        if (borderColor.alpha > 0f) BorderStroke(1.dp, borderColor)
        else null
    val isFrostedGlass get() = style == CardVisualStyle.FrostedGlass
}

val LocalCardAppearance = staticCompositionLocalOf {
    CardAppearance(
        style = CardVisualStyle.Solid,
        containerColor = LightSurface,
        borderColor = Color.Transparent,
        imageOverlayColor = LightSurface.copy(alpha = 0.74f),
    )
}

@Composable
fun rememberCardAppearance(
    darkTheme: Boolean,
    frostedGlass: Boolean,
    opacityPercent: Int,
): CardAppearance {
    val opacity = (opacityPercent.coerceIn(0, 100) / 100f)
    return remember(darkTheme, frostedGlass, opacity) {
        if (!frostedGlass) {
            val surface = if (darkTheme) DarkSurface else LightSurface
            CardAppearance(
                style = CardVisualStyle.Solid,
                containerColor = surface,
                borderColor = Color.Transparent,
                imageOverlayColor = surface.copy(alpha = 0.74f),
            )
        } else {
            val containerColor = if (darkTheme)
                Color(0xFF1A1D21).copy(alpha = opacity)
            else
                Color.White.copy(alpha = opacity)
            val borderColor = if (darkTheme)
                Color.White.copy(alpha = 0.16f + opacity * 0.18f)
            else
                Color.White.copy(alpha = 0.28f + opacity * 0.18f)
            val imageOverlayColor = if (darkTheme)
                Color(0xFF1A1D21).copy(alpha = (0.12f + opacity * 0.78f).coerceAtMost(0.9f))
            else
                Color.White.copy(alpha = (0.08f + opacity * 0.78f).coerceAtMost(0.9f))
            CardAppearance(
                style = CardVisualStyle.FrostedGlass,
                containerColor = containerColor,
                borderColor = borderColor,
                imageOverlayColor = imageOverlayColor,
            )
        }
    }
}

@Composable
fun AppCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val appearance = LocalCardAppearance.current
    Surface(
        modifier = modifier,
        shape = shape,
        color = appearance.containerColor,
        border = appearance.border,
        content = content,
    )
}
