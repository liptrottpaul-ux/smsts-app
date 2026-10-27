package uk.co.asmcoach.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.asmcoach.data.model.ModuleTint

/**
 * iOS (HIG, iOS 18) semantic colours. Field names are the app's own roles; the comments give
 * the UIKit semantic colour each one maps to, so light/dark adapt exactly like a native app.
 */
@Immutable
data class AppColors(
    /** systemBackground: canvas behind everything. */
    val background: Color,
    /** secondarySystemBackground: grouped cards and table sections. */
    val surface: Color,
    /** tertiarySystemBackground: elements nested inside cards. */
    val surfaceRaised: Color,
    /** tertiarySystemFill: text fields, segmented tracks, grey buttons. */
    val surfaceSunken: Color,
    /** separator: hairlines between rows and under bars. */
    val hairline: Color,
    /** label / secondaryLabel / tertiaryLabel. */
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** systemBlue: every interactive tint. */
    val accent: Color,
    val onAccent: Color,
    /** systemBlue at low opacity: tinted button and badge fills. */
    val accentSoft: Color,
    /** systemIndigo: secondary informational tint. */
    val slate: Color,
    /** Tinted card fills derived from systemOrange / systemGreen / systemBlue / systemRed. */
    val tintAmber: Color,
    val tintSage: Color,
    val tintSlate: Color,
    val tintRose: Color,
    /** systemGreen / systemOrange / systemRed status colours. */
    val good: Color,
    val warn: Color,
    val bad: Color,
    /** systemGray: unselected tab items, chevrons. */
    val gray: Color,
    /** Bar material: background at high opacity, shown over the blur. */
    val barMaterial: Color,
    val isDark: Boolean,
) {
    fun tint(tint: ModuleTint): Color = when (tint) {
        ModuleTint.Amber -> tintAmber
        ModuleTint.Sage -> tintSage
        ModuleTint.Slate -> tintSlate
        ModuleTint.Rose -> tintRose
    }

    fun scoreColor(score: Int): Color = when {
        score >= 70 -> good
        score >= 45 -> warn
        else -> bad
    }
}

private val BlueLight = Color(0xFF007AFF)
private val BlueDark = Color(0xFF0A84FF)
private val GreenLight = Color(0xFF34C759)
private val GreenDark = Color(0xFF30D158)
private val OrangeLight = Color(0xFFFF9500)
private val OrangeDark = Color(0xFFFF9F0A)
private val RedLight = Color(0xFFFF3B30)
private val RedDark = Color(0xFFFF453A)

val LightColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F7),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0x1F767680),
    hairline = Color(0x4A3C3C43),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    textTertiary = Color(0x4D3C3C43),
    accent = BlueLight,
    onAccent = Color.White,
    accentSoft = BlueLight.copy(alpha = 0.12f),
    slate = Color(0xFF5856D6),
    tintAmber = OrangeLight.copy(alpha = 0.12f),
    tintSage = GreenLight.copy(alpha = 0.12f),
    tintSlate = BlueLight.copy(alpha = 0.08f),
    tintRose = RedLight.copy(alpha = 0.10f),
    good = Color(0xFF248A3D),
    warn = Color(0xFFC93400),
    bad = RedLight,
    gray = Color(0xFF8E8E93),
    barMaterial = Color(0xE6F9F9F9),
    isDark = false,
)

val DarkColors = AppColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    surfaceSunken = Color(0x3D767680),
    hairline = Color(0x99545458),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textTertiary = Color(0x4DEBEBF5),
    accent = BlueDark,
    onAccent = Color.White,
    accentSoft = BlueDark.copy(alpha = 0.18f),
    slate = Color(0xFF5E5CE6),
    tintAmber = OrangeDark.copy(alpha = 0.16f),
    tintSage = GreenDark.copy(alpha = 0.16f),
    tintSlate = BlueDark.copy(alpha = 0.14f),
    tintRose = RedDark.copy(alpha = 0.16f),
    good = GreenDark,
    warn = OrangeDark,
    bad = RedDark,
    gray = Color(0xFF8E8E93),
    barMaterial = Color(0xD91D1D1D),
    isDark = true,
)

/** Toggle track colours (UISwitch). */
val AppColors.switchOn: Color get() = if (isDark) GreenDark else GreenLight
val AppColors.switchOff: Color get() = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

/**
 * iOS Dynamic Type default (Large) sizes with SF tracking values. SF Pro cannot be licensed for
 * Android, so the platform sans is used; sizes are in sp so they follow the system text size.
 */
private val Sans = FontFamily.Default
private fun ios(size: Int, line: Int, weight: FontWeight, tracking: Double) =
    TextStyle(fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

object IosType {
    val largeTitle = ios(34, 41, FontWeight.Bold, 0.37)
    val title1 = ios(28, 34, FontWeight.Bold, 0.36)
    val title2 = ios(22, 28, FontWeight.SemiBold, 0.35)
    val title3 = ios(20, 25, FontWeight.SemiBold, 0.38)
    val headline = ios(17, 22, FontWeight.SemiBold, -0.43)
    val body = ios(17, 22, FontWeight.Normal, -0.43)
    val callout = ios(16, 21, FontWeight.Normal, -0.31)
    val subheadline = ios(15, 20, FontWeight.Normal, -0.23)
    val footnote = ios(13, 18, FontWeight.Normal, -0.08)
    val caption1 = ios(12, 16, FontWeight.Normal, 0.0)
    val caption2 = ios(11, 13, FontWeight.Normal, 0.07)
}

/** Material roles mapped onto the iOS type ramp so every existing Text picks it up. */
val AppTypography = Typography(
    displaySmall = IosType.largeTitle,
    headlineMedium = IosType.title1,
    headlineSmall = IosType.title2,
    titleLarge = IosType.title3,
    titleMedium = IosType.headline,
    titleSmall = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = IosType.body,
    bodyMedium = IosType.subheadline,
    bodySmall = IosType.footnote,
    labelLarge = IosType.headline,
    labelMedium = IosType.footnote.copy(fontWeight = FontWeight.Medium),
    labelSmall = IosType.caption1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = ContinuousShape(8.dp),
    small = ContinuousShape(10.dp),
    medium = ContinuousShape(14.dp),
    large = ContinuousShape(20.dp),
    extraLarge = ContinuousShape(24.dp),
)

/** iOS default spring: damping ratio 0.8, response ≈ 0.35 s (stiffness ≈ (2π / 0.35)²). */
object IosMotion {
    const val DAMPING = 0.8f
    const val STIFFNESS = 320f
    fun <T> spring(): FiniteAnimationSpec<T> = spring(dampingRatio = DAMPING, stiffness = STIFFNESS)
    /** Critically damped variant for screen pushes, which never overshoot on iOS. */
    fun <T> push(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = STIFFNESS)
}

object AsmTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun AsmCoachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val common = { scheme: androidx.compose.material3.ColorScheme ->
        scheme.copy(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accent,
            secondary = colors.gray, onSecondary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.background, onSurface = colors.textPrimary,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised, surfaceContainerLow = colors.surface,
            outline = colors.hairline, outlineVariant = colors.hairline,
            error = colors.bad,
        )
    }
    val scheme = common(if (darkTheme) darkColorScheme() else lightColorScheme())
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
