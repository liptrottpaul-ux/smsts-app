package uk.co.asmcoach.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.asmcoach.ui.theme.AsmTheme
import uk.co.asmcoach.ui.theme.ContinuousShape
import uk.co.asmcoach.ui.theme.IosMotion
import uk.co.asmcoach.ui.theme.IosType
import uk.co.asmcoach.ui.theme.switchOff
import uk.co.asmcoach.ui.theme.switchOn

val CardShape = ContinuousShape(20.dp)
private val NavBarHeight = 44.dp

/** Space the floating tab bar covers at the bottom of the screen (0 on pushed screens). */
val LocalBottomInset = compositionLocalOf { 0.dp }

// ---- Haptics -------------------------------------------------------------------------------------

/** The closest Android equivalents of UIImpactFeedbackGenerator / UINotificationFeedbackGenerator. */
class Haptics(private val view: View) {
    /** `.impact(.light)`: buttons and taps. */
    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** `UISelectionFeedbackGenerator`: toggles, segments, tab switches. */
    fun selection() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** `.notification(.success)`: something finished well. */
    fun success() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    /** `.notification(.warning)`: an error the user should notice. */
    fun warning() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---- Screen scaffold with iOS navigation bar --------------------------------------------------

/**
 * A scrolling screen with an iOS navigation bar. With [largeTitle], the title starts large in
 * the content and collapses into the centred inline title as it scrolls away, while the bar's
 * material and hairline fade in, as in UINavigationBar with prefersLargeTitles.
 */
@Composable
fun IosScreen(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    spacing: Dp = 12.dp,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = AsmTheme.colors
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = LocalBottomInset.current
    val threshold = with(density) { 40.dp.toPx() }
    val collapsed by remember(largeTitle) {
        derivedStateOf { !largeTitle || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold }
    }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
    }
    val inlineAlpha by animateFloatAsState(if (collapsed) 1f else 0f, IosMotion.spring(), label = "inlineTitle")
    val barAlpha by animateFloatAsState(if (scrolled) 1f else 0f, IosMotion.spring(), label = "barMaterial")

    Box(modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + NavBarHeight, bottom = bottom + 28.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            if (largeTitle) {
                item(key = "__large_title") {
                    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                        Text(title, style = IosType.largeTitle, color = c.textPrimary)
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, style = IosType.subheadline, color = c.textSecondary)
                        }
                    }
                }
            }
            content()
        }
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.barMaterial.copy(alpha = c.barMaterial.alpha * barAlpha))
                    .padding(top = top)
                    .height(NavBarHeight),
            ) {
                Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
                Text(
                    title,
                    style = IosType.headline,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 100.dp).alpha(inlineAlpha),
                )
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hairline.copy(alpha = c.hairline.alpha * barAlpha)))
        }
    }
}

/** Bar button with an SF-Symbol-style glyph in the tint colour (44 × 44 target). */
@Composable
fun NavIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AsmTheme.colors.accent, modifier = Modifier.size(24.dp))
    }
}

/** Plain-text bar button ("Done", "Cancel"); [prominent] uses the semibold "done" style. */
@Composable
fun NavTextButton(text: String, onClick: () -> Unit, prominent: Boolean = false, enabled: Boolean = true) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(ContinuousShape(8.dp))
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (prominent) IosType.headline else IosType.body,
            color = if (enabled) AsmTheme.colors.accent else AsmTheme.colors.textTertiary,
        )
    }
}

/** iOS back button: chevron plus the previous screen's title. */
@Composable
fun NavBackButton(label: String = "Back", onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(ContinuousShape(8.dp))
            .clickable { haptics.tap(); onClick() }
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = null, tint = AsmTheme.colors.accent, modifier = Modifier.size(20.dp))
        Text(label, style = IosType.body, color = AsmTheme.colors.accent)
    }
}

// ---- Containers -----------------------------------------------------------------------------

/** Kept for older call sites: renders the large title block inline (prefer [IosScreen]). */
@Composable
fun LargeTitleHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = AsmTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = IosType.largeTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
            trailing()
        }
        Text(subtitle ?: eyebrow, style = IosType.subheadline, color = c.textSecondary)
    }
}

/** Inset-grouped card (secondarySystemBackground). */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    TonalCard(tint = AsmTheme.colors.surface, modifier = modifier, onClick = onClick, contentPadding = contentPadding, content = content)
}

/** Continuous-corner container; dims slightly on press like a highlighted table cell. */
@Composable
fun TonalCard(
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") border: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.985f else 1f, IosMotion.spring(), label = "press")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CardShape)
            .background(tint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { haptics.tap(); onClick() }
                } else {
                    Modifier
                },
            )
            .then(if (pressed && onClick != null) Modifier.background(AsmTheme.colors.surfaceSunken) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Gradient summary card with white content, like the Fitness and Health hero tiles. */
@Composable
fun HeroCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, ContinuousShape(24.dp), ambientColor = colors.first(), spotColor = colors.first())
            .clip(ContinuousShape(24.dp))
            .background(Brush.linearGradient(colors))
            .then(if (onClick != null) Modifier.clickable { haptics.tap(); onClick() } else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Translucent white capsule for use on hero cards. */
@Composable
fun HeroBadge(text: String) = PillBadge(text, color = Color.White, background = Color.White.copy(alpha = 0.22f))

/** Capsule badge with a tinted fill. */
@Composable
fun PillBadge(text: String, color: Color = AsmTheme.colors.accent, background: Color = AsmTheme.colors.accentSoft) {
    Text(
        text = text,
        style = IosType.caption1.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Inset-grouped section header: uppercase footnote in secondaryLabel, aligned with cell text. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = IosType.footnote,
        color = AsmTheme.colors.textSecondary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 2.dp),
    )
}

// ---- Buttons -------------------------------------------------------------------------------

/** Filled (prominent) button: systemBlue with white label, 50pt tall, continuous corners. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.7f else 1f, tween(120), label = "btnAlpha")
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(ContinuousShape(14.dp))
            .background(if (enabled) c.accent else c.surfaceSunken)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) {
                haptics.tap()
                onClick()
            }
            .alpha(alpha)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.onAccent else c.textTertiary
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = IosType.headline, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Gray (tinted) button: tertiarySystemFill with a systemBlue label, 44pt minimum. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(120), label = "btnAlpha")
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(ContinuousShape(12.dp))
            .background(c.surfaceSunken)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.tap()
                onClick()
            }
            .alpha(alpha)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.accent else c.textTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

// ---- Controls ------------------------------------------------------------------------------

/** UISwitch look: green track when on, light gray when off, same-size white thumb. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { haptics.selection(); onCheckedChange(it) },
        // A non-null thumbContent keeps the thumb full size in both states, as on iOS.
        thumbContent = {},
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.switchOn,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.switchOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** UISegmentedControl: gray track, raised thumb that springs between segments. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(ContinuousShape(9.dp))
            .background(c.surfaceSunken)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, IosMotion.spring(), label = "thumb")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, ContinuousShape(7.dp))
                .clip(ContinuousShape(7.dp))
                .background(if (c.isDark) Color(0xFF636366) else Color.White),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (index != selectedIndex) haptics.selection()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosType.footnote,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = c.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Disclosure card: chevron rotates and content springs open. */
@Composable
fun AccordionCard(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AsmTheme.colors.surface,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, IosMotion.spring(), label = "chevron")
    TonalCard(tint = tint, modifier = modifier.animateContentSize(IosMotion.spring()), contentPadding = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { haptics.selection(); onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = IosType.headline, color = c.textPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = IosType.footnote, color = c.textSecondary)
                }
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = c.gray,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(IosMotion.spring()) + fadeIn(),
            exit = shrinkVertically(IosMotion.spring()) + fadeOut(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

/** Read-out-loud callout backed by the native TTS engine. */
@Composable
fun ReadAloudBox(
    text: String,
    speaking: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Say it like this",
) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .clip(ContinuousShape(14.dp))
            .background(c.accentSoft)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { haptics.tap(); onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(
                        if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (speaking) "Stop reading" else "Read aloud",
                        tint = c.onAccent,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Text(text, style = IosType.callout, color = c.textPrimary, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
fun Bullets(
    items: List<String>,
    modifier: Modifier = Modifier,
    dotColor: Color = AsmTheme.colors.accent,
    textColor: Color = AsmTheme.colors.textPrimary,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(10.dp))
                Text(item, style = IosType.subheadline, color = textColor)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { PillBadge(it, color = AsmTheme.colors.textSecondary, background = AsmTheme.colors.surfaceSunken) }
    }
}

/** Selectable capsule; visually 34pt, with a 48dp minimum touch target. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable { if (!selected) haptics.selection(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IosType.subheadline.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) c.onAccent else c.textPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) c.accent else c.surfaceSunken)
                .sizeIn(minHeight = 34.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** Activity-ring style gauge out of 100. */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    caption: String? = "/ 100",
    onHero: Boolean = false,
) {
    val c = AsmTheme.colors
    val progress by animateFloatAsState(score.coerceIn(0, 100) / 100f, IosMotion.spring(), label = "score")
    val color = if (onHero) Color.White else c.scoreColor(score)
    val textColor = if (onHero) Color.White else c.textPrimary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(color.copy(alpha = if (onHero) 0.28f else 0.18f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = if (size >= 110.dp) IosType.title1 else IosType.title3, color = textColor)
            if (caption != null) Text(caption, style = IosType.caption1, color = textColor.copy(alpha = 0.7f))
        }
    }
}

/** UIProgressView-style bar. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), IosMotion.spring(), label = "meter")
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(AsmTheme.colors.surfaceSunken),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val c = AsmTheme.colors
    TonalCard(tint = c.tintRose, modifier = modifier, onClick = onDismiss, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.bad, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = IosType.subheadline, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                NavTextButton(actionLabel, onAction, prominent = true)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val c = AsmTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = c.gray, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = IosType.title3, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = IosType.subheadline, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** Settings-style icon: white glyph on a coloured squircle. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).clip(ContinuousShape(size * 0.24f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}

/** Bar-style icon button (kept name for existing call sites). */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) =
    NavIconButton(icon, contentDescription, onClick)

/** One ring in [ConcentricRings]: progress 0..1, its colour and a short legend label. */
data class RingValue(val progress: Float, val color: Color, val label: String, val value: String)

/**
 * Concentric progress rings with a legend, in the style of the reference infographic
 * (coral outside, teal, then olive), with a big number in the centre.
 */
@Composable
fun ConcentricRings(
    rings: List<RingValue>,
    centre: String,
    centreCaption: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    stroke: Dp = 12.dp,
    onHero: Boolean = false,
) {
    val c = AsmTheme.colors
    val animated = rings.map { ring ->
        animateFloatAsState(ring.progress.coerceIn(0f, 1f), IosMotion.spring(), label = ring.label).value
    }
    val textColor = if (onHero) Color.White else c.textPrimary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val strokePx = stroke.toPx()
                val gap = strokePx * 0.45f
                rings.forEachIndexed { i, ring ->
                    val inset = strokePx / 2 + i * (strokePx + gap)
                    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    if (arcSize.width <= 0f) return@forEachIndexed
                    drawArc(ring.color.copy(alpha = 0.2f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                    drawArc(ring.color, 135f, 270f * animated[i], false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centre, style = IosType.title1, color = textColor)
                Text(centreCaption, style = IosType.caption1, color = textColor.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rings.forEach { ring ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ring.color))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(ring.value, style = IosType.headline, color = textColor)
                        Text(ring.label.uppercase(), style = IosType.caption2, color = textColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/** Smooth, overlapping translucent area chart (values 0..100), like the reference's layered waves. */
@Composable
fun AreaChart(
    series: List<Pair<List<Int>, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val c = AsmTheme.colors
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        // Baseline grid, faint.
        for (g in 0..3) {
            val y = h * g / 3f
            drawLine(c.hairline, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        series.forEach { (values, color) ->
            if (values.size < 2) return@forEach
            val stepX = w / (values.size - 1)
            val points = values.mapIndexed { i, v -> Offset(i * stepX, h - h * v.coerceIn(0, 100) / 100f) }
            val line = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val midX = (p0.x + p1.x) / 2
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line)
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.08f))))
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            points.forEach { drawCircle(Color.White, radius = 3.5.dp.toPx(), center = it) ; drawCircle(color, radius = 2.2.dp.toPx(), center = it) }
        }
    }
}
