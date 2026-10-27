package uk.co.asmcoach.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import uk.co.asmcoach.ui.theme.AsmTheme

val CardShape = RoundedCornerShape(20.dp)

/** Large iOS-style title block with an eyebrow phase label and an optional trailing action. */
@Composable
fun LargeTitleHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillBadge(eyebrow)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, color = AsmTheme.colors.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AsmTheme.colors.textSecondary)
        }
    }
}

/** Plain raised surface card with a hairline border, like an iOS inset grouped section. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    TonalCard(
        tint = AsmTheme.colors.surface,
        modifier = modifier,
        onClick = onClick,
        border = !AsmTheme.colors.isDark,
        contentPadding = contentPadding,
        content = content,
    )
}

/** Soft pastel card with a gentle press-scale; the core container of the design language. */
@Composable
fun TonalCard(
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Boolean = false,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CardShape)
            .background(tint)
            .then(if (border) Modifier.border(BorderStroke(1.dp, AsmTheme.colors.hairline), CardShape) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PillBadge(text: String, color: Color = AsmTheme.colors.accent, background: Color = AsmTheme.colors.accentSoft) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AsmTheme.colors.textTertiary,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btn")
    Row(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(if (enabled) c.accent else c.surfaceSunken)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
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
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = AsmTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(CircleShape)
            .background(if (c.isDark) c.surfaceRaised else c.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.textPrimary else c.textTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** iOS-style segmented control with a sliding thumb. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AsmTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (c.isDark) c.surfaceRaised else c.surfaceSunken)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(stiffness = Spring.StiffnessMediumLow), label = "thumb")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(if (c.isDark) Color(0xFF3A3B40) else Color.White),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = c.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Expandable card with a rotating chevron and a spring-y reveal. */
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
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chevron")
    TonalCard(tint = tint, modifier = modifier.animateContentSize(), border = tint == AsmTheme.colors.surface && !AsmTheme.colors.isDark, contentPadding = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AsmTheme.colors.textPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AsmTheme.colors.textSecondary)
                }
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = AsmTheme.colors.textTertiary,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), content = content)
        }
    }
}

/** Read-out-loud summary box backed by the native TTS engine. */
@Composable
fun ReadAloudBox(
    text: String,
    speaking: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Say it like this",
) {
    val c = AsmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.accentSoft)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.accent, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (speaking) "Stop reading" else "Read aloud",
                    tint = c.onAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("“$text”", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

@Composable
fun Bullets(items: List<String>, modifier: Modifier = Modifier, dotColor: Color = AsmTheme.colors.accent) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(12.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = AsmTheme.colors.textPrimary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { PillBadge(it, color = AsmTheme.colors.slate, background = AsmTheme.colors.tintSlate) }
    }
}

/** Selectable capsule chip. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AsmTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) c.onAccent else c.textPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.accent else if (c.isDark) c.surfaceRaised else c.surface)
            .then(if (!selected && !c.isDark) Modifier.border(1.dp, c.hairline, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Animated circular score gauge out of 100. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 120.dp, stroke: Dp = 10.dp, caption: String? = "/ 100") {
    val c = AsmTheme.colors
    val progress by animateFloatAsState(score.coerceIn(0, 100) / 100f, tween(900), label = "score")
    val color = c.scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(c.hairline, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        }
    }
}

/** Thin horizontal progress bar used for competency and STAR scores. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(AsmTheme.colors.hairline),
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
    TonalCard(tint = c.tintRose, modifier = modifier, onClick = onDismiss, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.bad, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val c = AsmTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** Icon in a soft rounded square, used as a leading glyph on cards. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Round icon-only button for headers. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val c = AsmTheme.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (c.isDark) c.surfaceRaised else c.surface)
            .then(if (!c.isDark) Modifier.border(1.dp, c.hairline, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(20.dp))
    }
}
