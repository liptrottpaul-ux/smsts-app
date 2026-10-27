package uk.co.asmcoach.ui.strategy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.PlanPhase
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.ui.components.Bullets
import uk.co.asmcoach.ui.components.ChipFlow
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.EmptyState
import uk.co.asmcoach.ui.components.ErrorBanner
import uk.co.asmcoach.ui.components.PillBadge
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.ReadAloudBox
import uk.co.asmcoach.ui.components.ScoreRing
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.components.TonalCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme

@Composable
fun StrategyScreen(container: AppContainer, onBack: () -> Unit, onPrep: () -> Unit, onPractise: () -> Unit) {
    val vm = containerViewModel { StrategyViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speaking by vm.speakingId.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val job = state.job

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                if (job != null && !job.isSample) {
                    CircleIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "Open listing") { uri.openUri(job.url) }
                }
            }
        }
        if (job == null) {
            item { EmptyState(Icons.Rounded.AutoAwesome, "No role selected", "Pick a listing on the Jobs tab first.") }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                PillBadge("Why you win · ${job.source}")
                Spacer(Modifier.height(10.dp))
                Text(job.title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                val meta = listOfNotNull(job.company, job.location, job.salary).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        state.error?.let { msg ->
            item { ErrorBanner(msg, onDismiss = vm::dismissError, actionLabel = "Retry", onAction = vm::generate) }
        }
        item {
            AnimatedContent(
                targetState = state.stage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stage",
            ) { stage ->
                if (stage != null) LoadingCard(stage)
            }
        }
        if (state.pack != null) {
            item { NextStepsCard(state.pack!!.roleSummary, onPrep, onPractise) }
        }
        state.brief?.let { brief ->
            briefContent(brief, speaking, vm::toggleSpeak)
            item {
                Spacer(Modifier.height(8.dp))
                if (state.pack != null) {
                    PrimaryButton(
                        "Interview for this role",
                        onClick = onPractise,
                        icon = Icons.Rounded.Mic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                SecondaryButton(
                    "Rebuild everything for this role",
                    onClick = vm::regenerate,
                    icon = Icons.Rounded.Refresh,
                    enabled = state.stage == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NextStepsCard(summary: String, onPrep: () -> Unit, onPractise: () -> Unit) {
    val c = AsmTheme.colors
    TonalCard(tint = c.tintSage) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = c.good, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Prep and interview ready for this role", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        if (summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Prep", onClick = onPrep, icon = Icons.Rounded.AutoStories, modifier = Modifier.weight(1f))
            PrimaryButton("Interview", onClick = onPractise, icon = Icons.Rounded.Mic, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadingCard(stage: String) {
    val c = AsmTheme.colors
    TonalCard(tint = c.tintAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Preparing this role", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(stage, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(
                    "About 30–60 seconds, once per role. You can leave this screen; it keeps going.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.briefContent(
    brief: StrategyBrief,
    speakingId: String?,
    onSpeak: (String, String) -> Unit,
) {
    item {
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(brief.compatibilityScore, size = 104.dp, caption = "fit")
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(brief.fitLabel.ifBlank { "Compatibility" }, style = MaterialTheme.typography.titleLarge, color = AsmTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(brief.matchSummary, style = MaterialTheme.typography.bodyMedium, color = AsmTheme.colors.textSecondary)
                    if (brief.projectType.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PillBadge(brief.projectType, color = AsmTheme.colors.slate, background = AsmTheme.colors.tintSlate)
                    }
                }
            }
        }
    }
    item {
        SectionLabel("Why you win this role")
        TonalCard(tint = AsmTheme.colors.tintAmber) {
            Text(brief.whyYouWin, style = MaterialTheme.typography.bodyLarge, color = AsmTheme.colors.textPrimary)
        }
    }
    if (brief.elevatorPitch.isNotBlank()) {
        item {
            ReadAloudBox(
                text = brief.elevatorPitch,
                speaking = speakingId == "pitch",
                onToggle = { onSpeak("pitch", brief.elevatorPitch) },
                label = "Your 60-second pitch",
            )
        }
    }
    if (brief.strengths.isNotEmpty()) {
        item {
            SectionLabel("What they want · your proof")
            SurfaceCard {
                brief.strengths.forEachIndexed { i, match ->
                    if (i > 0) Divider()
                    Row {
                        Icon(Icons.Rounded.CheckCircle, null, tint = AsmTheme.colors.good, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(match.requirement, style = MaterialTheme.typography.titleSmall, color = AsmTheme.colors.textPrimary)
                            Text(match.evidence, style = MaterialTheme.typography.bodyMedium, color = AsmTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
    if (brief.gaps.isNotEmpty()) {
        item {
            SectionLabel("Gaps to close")
            TonalCard(tint = AsmTheme.colors.tintRose) {
                brief.gaps.forEachIndexed { i, gap ->
                    if (i > 0) Divider()
                    Row {
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, tint = AsmTheme.colors.bad, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(gap.gap, style = MaterialTheme.typography.titleSmall, color = AsmTheme.colors.textPrimary)
                            Text(gap.mitigation, style = MaterialTheme.typography.bodyMedium, color = AsmTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
    item { SectionLabel("30-60-90 day site management plan") }
    item { PlanCard("30", brief.plan.days30, AsmTheme.colors.tintSage, AsmTheme.colors.good) }
    item { PlanCard("60", brief.plan.days60, AsmTheme.colors.tintSlate, AsmTheme.colors.slate) }
    item { PlanCard("90", brief.plan.days90, AsmTheme.colors.tintAmber, AsmTheme.colors.accent) }
    if (brief.questionsToAsk.isNotEmpty()) {
        item {
            SectionLabel("Ask the panel")
            SurfaceCard { Bullets(brief.questionsToAsk) }
        }
    }
    if (brief.keywords.isNotEmpty()) {
        item {
            SectionLabel("Echo these keywords")
            ChipFlow(brief.keywords)
        }
    }
}

@Composable
private fun PlanCard(days: String, phase: PlanPhase, tint: Color, accent: Color) {
    val c = AsmTheme.colors
    TonalCard(tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text(days, style = MaterialTheme.typography.titleMedium, color = c.background)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Days ${if (days == "30") "1–30" else if (days == "60") "31–60" else "61–90"}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(phase.theme.ifBlank { "Plan" }, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Bullets(phase.actions, dotColor = accent)
        if (phase.successMeasure.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Success looks like: ${phase.successMeasure}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(AsmTheme.colors.hairline))
}
