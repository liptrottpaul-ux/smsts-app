package uk.co.asmcoach.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
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

// Palette: infographic-inspired cream, charcoal, teal, coral, olive and sage.
private val Teal = Color(0xFF1FA2AE)
private val TealBright = Color(0xFF3FC1C9)
private val Coral = Color(0xFFEE6352)
private val CoralBright = Color(0xFFF27A6A)
private val Olive = Color(0xFFB5BD45)
private val OliveBright = Color(0xFFC9CF5E)
private val Sage = Color(0xFF8FB28A)
private val Charcoal = Color(0xFF263238)
private val Cream = Color(0xFFF1ECE4)

val LightColors = AppColors(
    background = Cream,
    surface = Color(0xFFFBF8F3),
    surfaceRaised = Color(0xFFEDE7DE),
    surfaceSunken = Color(0x14263238),
    hairline = Color(0x2E263238),
    textPrimary = Charcoal,
    textSecondary = Color(0xA6263238),
    textTertiary = Color(0x66263238),
    accent = Teal,
    onAccent = Color.White,
    accentSoft = Teal.copy(alpha = 0.14f),
    slate = Color(0xFF3D5A66),
    tintAmber = Olive.copy(alpha = 0.18f),
    tintSage = Sage.copy(alpha = 0.22f),
    tintSlate = Teal.copy(alpha = 0.12f),
    tintRose = Coral.copy(alpha = 0.14f),
    good = Color(0xFF4F8A55),
    warn = Color(0xFF8F9A1E),
    bad = Color(0xFFE5533F),
    gray = Color(0xFF8A9497),
    barMaterial = Color(0xE6F1ECE4),
    isDark = false,
)

val DarkColors = AppColors(
    background = Color(0xFF1E2629),
    surface = Color(0xFF27323A),
    surfaceRaised = Color(0xFF303D45),
    surfaceSunken = Color(0x29F1ECE4),
    hairline = Color(0x33F1ECE4),
    textPrimary = Cream,
    textSecondary = Color(0xA6F1ECE4),
    textTertiary = Color(0x66F1ECE4),
    accent = TealBright,
    onAccent = Color(0xFF10262A),
    accentSoft = TealBright.copy(alpha = 0.20f),
    slate = Color(0xFF8FB7C4),
    tintAmber = OliveBright.copy(alpha = 0.18f),
    tintSage = Sage.copy(alpha = 0.22f),
    tintSlate = TealBright.copy(alpha = 0.16f),
    tintRose = CoralBright.copy(alpha = 0.18f),
    good = Color(0xFF93C08C),
    warn = OliveBright,
    bad = CoralBright,
    gray = Color(0xFF8A9497),
    barMaterial = Color(0xE61E2629),
    isDark = true,
)

/** Hero gradients from the same palette. */
object Gradients {
    val blue = listOf(TealBright, Color(0xFF1E8F9C))
    val green = listOf(Color(0xFF8FC08A), Color(0xFF4F8A55))
    val orange = listOf(OliveBright, Color(0xFF9AA537))
    val red = listOf(CoralBright, Color(0xFFD9434F))
    val night = listOf(Color(0xFF2B3A40), Color(0xFF1B2427))

    fun forScore(score: Int) = when {
        score >= 70 -> green
        score >= 45 -> orange
        else -> red
    }
}

/** Series colours for rings and charts (coral, teal, olive, sage), as in the reference infographics. */
object ChartColors {
    val coral = CoralBright
    val teal = TealBright
    val olive = OliveBright
    val sage = Sage
}

/** Toggle track colours (UISwitch). */
val AppColors.switchOn: Color get() = accent
val AppColors.switchOff: Color get() = if (isDark) Color(0xFF3B474D) else Color(0xFFDCD5CA)

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

// Material's slots require corner-based shapes; app components use ContinuousShape directly.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
