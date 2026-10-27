package uk.co.asmcoach.ui.theme

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
 * Design tokens: warm stone and obsidian surfaces, slate neutrals and a single construction-amber
 * accent. Pastel tints are used for tonal cards; nothing uses default Material blue.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val slate: Color,
    val tintAmber: Color,
    val tintSage: Color,
    val tintSlate: Color,
    val tintRose: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
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

val LightColors = AppColors(
    background = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEAE6DF),
    hairline = Color(0xFFE2DDD4),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF66666B),
    textTertiary = Color(0xFF9A9894),
    accent = Color(0xFFE0921F),
    onAccent = Color(0xFF1C1407),
    accentSoft = Color(0xFFFBEBD3),
    slate = Color(0xFF5B6573),
    tintAmber = Color(0xFFFBEEDA),
    tintSage = Color(0xFFE5EDE2),
    tintSlate = Color(0xFFE4E8EE),
    tintRose = Color(0xFFF4E4E0),
    good = Color(0xFF3F8C5A),
    warn = Color(0xFFC98316),
    bad = Color(0xFFC0533B),
    isDark = false,
)

val DarkColors = AppColors(
    background = Color(0xFF0B0C0E),
    surface = Color(0xFF15161A),
    surfaceRaised = Color(0xFF1D1F23),
    surfaceSunken = Color(0xFF101114),
    hairline = Color(0xFF2A2C31),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA1A1A6),
    textTertiary = Color(0xFF6E6E73),
    accent = Color(0xFFF5B04A),
    onAccent = Color(0xFF1C1407),
    accentSoft = Color(0xFF2F2516),
    slate = Color(0xFF94A3B8),
    tintAmber = Color(0xFF2A2217),
    tintSage = Color(0xFF1A241D),
    tintSlate = Color(0xFF1B2029),
    tintRose = Color(0xFF291E1C),
    good = Color(0xFF6CC28A),
    warn = Color(0xFFF5B04A),
    bad = Color(0xFFE5806A),
    isDark = true,
)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

/** iOS-inspired type ramp mapped onto Material 3 roles. */
private val Sans = FontFamily.SansSerif
val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object AsmTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun AsmCoachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.textPrimary,
            secondary = colors.slate, onSecondary = colors.background,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised, surfaceContainerLow = colors.surface,
            outline = colors.hairline, outlineVariant = colors.hairline,
            error = colors.bad,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.textPrimary,
            secondary = colors.slate, onSecondary = Color.White,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surface, surfaceContainerLow = colors.surface,
            outline = colors.hairline, outlineVariant = colors.hairline,
            error = colors.bad,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
