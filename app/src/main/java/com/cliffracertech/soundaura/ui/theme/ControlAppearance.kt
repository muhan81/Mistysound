package com.cliffracertech.soundaura.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

enum class OverlayActionStyle { Neutral, Primary, Secondary }

@Stable
data class OverlayActionColors(
    val containerColor: Color,
    val contentColor: Color,
)

@Stable
data class OverlaySliderPalette(
    val accentStart: Color,
    val accentEnd: Color,
    val inactiveTrackColor: Color,
)

@Stable
data class OverlayPanelStyle(
    val backgroundBrush: Brush,
    val backgroundColor: Color,
    val contentColor: Color,
)

@Composable
fun rememberOverlayActionColors(
    style: OverlayActionStyle,
): OverlayActionColors {
    val appearance = LocalCardAppearance.current
    val colors = MaterialTheme.colors
    return remember(
        appearance,
        style,
        colors.primaryVariant,
        colors.secondaryVariant,
        colors.surface,
        colors.onSurface,
        colors.onPrimary,
        colors.isLight,
    ) {
        if (!appearance.isFrostedGlass) {
            when (style) {
                OverlayActionStyle.Neutral -> OverlayActionColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                )
                OverlayActionStyle.Primary -> OverlayActionColors(
                    containerColor = colors.primaryVariant,
                    contentColor = colors.onPrimary,
                )
                OverlayActionStyle.Secondary -> OverlayActionColors(
                    containerColor = colors.secondaryVariant,
                    contentColor = colors.onPrimary,
                )
            }
        } else {
            val base = frostedBaseContainerColor(appearance.containerColor)
            val accentContent = if (colors.isLight) colors.onPrimary else colors.onSurface
            when (style) {
                OverlayActionStyle.Neutral -> OverlayActionColors(
                    containerColor = base,
                    contentColor = colors.onSurface,
                )
                OverlayActionStyle.Primary -> OverlayActionColors(
                    containerColor = frostedAccentColor(
                        accent = colors.primaryVariant,
                        base = base,
                        alpha = if (colors.isLight) 0.22f else 0.30f,
                    ),
                    contentColor = accentContent,
                )
                OverlayActionStyle.Secondary -> OverlayActionColors(
                    containerColor = frostedAccentColor(
                        accent = colors.secondaryVariant,
                        base = base,
                        alpha = if (colors.isLight) 0.18f else 0.24f,
                    ),
                    contentColor = accentContent,
                )
            }
        }
    }
}

@Composable
fun rememberOverlaySliderPalette(): OverlaySliderPalette {
    val appearance = LocalCardAppearance.current
    val colors = MaterialTheme.colors
    return remember(
        appearance,
        colors.primaryVariant,
        colors.secondaryVariant,
        colors.onSurface,
        colors.isLight,
    ) {
        if (!appearance.isFrostedGlass) {
            OverlaySliderPalette(
                accentStart = colors.primaryVariant,
                accentEnd = colors.secondaryVariant,
                inactiveTrackColor = colors.onSurface.copy(alpha = 0.24f),
            )
        } else {
            OverlaySliderPalette(
                accentStart = colors.primaryVariant.copy(alpha = if (colors.isLight) 0.54f else 0.62f),
                accentEnd = colors.secondaryVariant.copy(alpha = if (colors.isLight) 0.48f else 0.56f),
                inactiveTrackColor = colors.onSurface.copy(alpha = if (colors.isLight) 0.16f else 0.26f),
            )
        }
    }
}

@Composable
fun rememberOverlaySliderColors(): SliderColors {
    val palette = rememberOverlaySliderPalette()
    val colors = MaterialTheme.colors
    return SliderDefaults.colors(
        thumbColor = palette.accentEnd,
        activeTrackColor = palette.accentStart,
        inactiveTrackColor = palette.inactiveTrackColor,
        activeTickColor = palette.accentStart,
        inactiveTickColor = palette.inactiveTrackColor,
        disabledThumbColor = colors.onSurface.copy(alpha = 0.36f),
        disabledActiveTrackColor = palette.accentStart.copy(alpha = 0.34f),
        disabledInactiveTrackColor = palette.inactiveTrackColor.copy(alpha = 0.7f),
    )
}

@Composable
fun rememberOverlayPanelStyle(): OverlayPanelStyle {
    val appearance = LocalCardAppearance.current
    val colors = MaterialTheme.colors
    return remember(
        appearance,
        colors.primaryVariant,
        colors.secondaryVariant,
        colors.onPrimary,
        colors.onSurface,
        colors.isLight,
    ) {
        if (!appearance.isFrostedGlass) {
            OverlayPanelStyle(
                backgroundBrush = Brush.horizontalGradient(
                    listOf(colors.primaryVariant, colors.secondaryVariant)
                ),
                backgroundColor = colors.surface,
                contentColor = colors.onPrimary,
            )
        } else {
            val base = frostedBaseContainerColor(appearance.containerColor)
            val accentStart = frostedAccentColor(
                accent = colors.primaryVariant,
                base = base,
                alpha = if (colors.isLight) 0.12f else 0.18f,
            )
            val accentEnd = frostedAccentColor(
                accent = colors.secondaryVariant,
                base = base,
                alpha = if (colors.isLight) 0.09f else 0.14f,
            )
            OverlayPanelStyle(
                backgroundBrush = Brush.horizontalGradient(listOf(accentStart, accentEnd)),
                backgroundColor = base,
                contentColor = colors.onSurface,
            )
        }
    }
}

private fun frostedBaseContainerColor(
    base: Color,
) = base.copy(alpha = (base.alpha + 0.08f).coerceIn(0.50f, 0.82f))

private fun frostedAccentColor(
    accent: Color,
    base: Color,
    alpha: Float,
) = accent.copy(alpha = alpha).compositeOver(base)
