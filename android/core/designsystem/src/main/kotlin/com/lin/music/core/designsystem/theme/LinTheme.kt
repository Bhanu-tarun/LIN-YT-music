package com.lin.music.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class LinColors(
    val isDark: Boolean,
    val background: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val defaultAccent: Color,
    val searchOverlayBg: Color,
    val queueItemBg: Color,
    val queueItemHover: Color,
    val progressTrack: Color,
    val btnInvertBg: Color,
    val btnInvertText: Color,
    val tagActiveBg: Color,
    val tagActiveText: Color,
    val danger: Color
)

val LinDarkColors = LinColors(
    isDark = true,
    background = Color(0xFF000000),
    glassBg = Color(0xB30A0A0A),
    glassBorder = Color(0x1AFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF999999),
    defaultAccent = Color(0xFF00FF00),
    searchOverlayBg = Color(0xE6000000),
    queueItemBg = Color(0x0DFFFFFF),
    queueItemHover = Color(0x1AFFFFFF),
    progressTrack = Color(0x33FFFFFF),
    btnInvertBg = Color(0xFFFFFFFF),
    btnInvertText = Color(0xFF000000),
    tagActiveBg = Color(0xFFFFFFFF),
    tagActiveText = Color(0xFF000000),
    danger = Color(0xFFFF4444)
)

val LinLightColors = LinColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    glassBg = Color(0xCCFFFFFF),
    glassBorder = Color(0x1A000000),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF666666),
    defaultAccent = Color(0xFF00AA00),
    searchOverlayBg = Color(0xF2FFFFFF),
    queueItemBg = Color(0x0D000000),
    queueItemHover = Color(0x1A000000),
    progressTrack = Color(0x26000000),
    btnInvertBg = Color(0xFF000000),
    btnInvertText = Color(0xFFFFFFFF),
    tagActiveBg = Color(0xFF000000),
    tagActiveText = Color(0xFFFFFFFF),
    danger = Color(0xFFCC2222)
)

val LocalLinColors = staticCompositionLocalOf { LinDarkColors }
val LocalCapitalizeLabels = staticCompositionLocalOf { false }

val LinBrandOrange = Color(0xFFF9721F)

@Composable
@ReadOnlyComposable
fun linLabel(text: String): String = formatLinLabel(text, LocalCapitalizeLabels.current)

fun formatLinLabel(text: String, capitalize: Boolean): String {
    if (!capitalize || text.isBlank()) return text
    return text.split(" ").joinToString(" ") { word ->
        if (word.startsWith("#")) word
        else {
            val idx = word.indexOfFirst { it.isLetter() }
            if (idx < 0) word
            else word.substring(0, idx) + word[idx].uppercaseChar() + word.substring(idx + 1)
        }
    }
}

private val LinSharpShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

object LinTextStyles {
    private val sans = FontFamily.SansSerif

    val brand = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 2.sp
    )

    val drilldownTitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 36.sp
    )

    val sectionHeading = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 26.sp
    )

    val navItem = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Light,
        fontSize = 15.sp
    )

    val navItemActive = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )

    val trackTitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 25.sp
    )

    val trackTitlePlaying = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    )

    val subtitle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )

    val tagPill = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )

    val searchInput = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 34.sp
    )
}

private val LinMaterialTypography = Typography(
    displayLarge = LinTextStyles.drilldownTitle,
    headlineMedium = LinTextStyles.sectionHeading,
    titleLarge = LinTextStyles.trackTitlePlaying,
    titleMedium = LinTextStyles.trackTitle,
    bodyMedium = LinTextStyles.subtitle,
    labelMedium = LinTextStyles.tagPill
)

object LinTheme {
    val colors: LinColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLinColors.current
}

@Composable
fun LinTheme(
    isDark: Boolean = true,
    capitalizeLabels: Boolean = false,
    content: @Composable () -> Unit
) {
    val linColors = if (isDark) LinDarkColors else LinLightColors
    val materialScheme = if (isDark) {
        darkColorScheme(
            primary = linColors.btnInvertBg,
            onPrimary = linColors.btnInvertText,
            background = linColors.background,
            onBackground = linColors.textPrimary,
            surface = linColors.glassBg,
            onSurface = linColors.textPrimary,
            onSurfaceVariant = linColors.textSecondary,
            outline = linColors.glassBorder,
            error = linColors.danger
        )
    } else {
        lightColorScheme(
            primary = linColors.btnInvertBg,
            onPrimary = linColors.btnInvertText,
            background = linColors.background,
            onBackground = linColors.textPrimary,
            surface = linColors.glassBg,
            onSurface = linColors.textPrimary,
            onSurfaceVariant = linColors.textSecondary,
            outline = linColors.glassBorder,
            error = linColors.danger
        )
    }

    CompositionLocalProvider(
        LocalLinColors provides linColors,
        LocalCapitalizeLabels provides capitalizeLabels
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            shapes = LinSharpShapes,
            typography = LinMaterialTypography,
            content = content
        )
    }
}

val LinSharpShape = RectangleShape
