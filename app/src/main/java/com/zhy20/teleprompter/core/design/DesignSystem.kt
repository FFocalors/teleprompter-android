package com.zhy20.teleprompter.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt

object AppColors {
    /** Cool charcoal chrome; orange and yellow stay reserved for emphasis. */
    val Background = Color(0xFF222627)
    val Surface = Color(0xFF2A2F30)
    val SurfaceRaised = Color(0xFF343A3B)
    val Primary = Color(0xFFED8F19)
    val PrimaryAlt = Color(0xFFFEE935)
    val PrimaryPressed = Color(0xFFC97410)
    val OnPrimary = Color(0xFF1B1F20)
    val Secondary = Color(0xFF3B4243)
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFFC8CED3)
    val TextWeak = Color(0xFF8B9594)
    val Border = Color(0xFF3D4445)
    val Divider = Color(0x663D4445)
    val Success = Color(0xFF7FA98A)
    val Warning = Color(0xFFFEE935)
    val Danger = Color(0xFFE96C62)
    val GuideLineBrightRed = Color(0xFFFF453A)
    val GuideLineDeepRed = Color(0xFFC62828)
    val GuideLineRedSoft = Color(0x4DFF453A)
    val Scrim = Color(0xCC16191A)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
    val EditorTail = 160.dp
}

object AppShapes {
    val Material = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    )
}

object AppElevation {
    val None = 0.dp
    val Card = 1.dp
    val Overlay = 6.dp
}

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 66.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

private val AppColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    primaryContainer = AppColors.Secondary,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.Secondary,
    onSecondary = AppColors.TextPrimary,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceRaised,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    error = AppColors.Danger,
    onError = AppColors.TextPrimary,
    scrim = AppColors.Scrim,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes.Material,
        content = content,
    )
}

fun colorFromHex(hex: String): Color = runCatching {
    Color(hex.toColorInt())
}.getOrDefault(AppColors.Background)
