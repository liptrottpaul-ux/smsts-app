package uk.co.asmcoach.ui.prep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.Lesson
import uk.co.asmcoach.data.model.Masterclass
import uk.co.asmcoach.data.model.ModuleTint
import uk.co.asmcoach.ui.components.AccordionCard
import uk.co.asmcoach.ui.components.Bullets
import uk.co.asmcoach.ui.components.ChipFlow
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.ErrorBanner
import uk.co.asmcoach.ui.components.IconTile
import uk.co.asmcoach.ui.components.LargeTitleHeader
import uk.co.asmcoach.ui.components.MeterBar
import uk.co.asmcoach.ui.components.ReadAloudBox
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme

@Composable
fun PrepScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val vm = containerViewModel { PrepViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speaking by vm.speakingId.collectAsStateWithLifecycle()
    val target by vm.targetJob.collectAsStateWithLifecycle()
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalLessons = vm.modules.sumOf { it.lessons.size }

    DisposableEffect(Unit) { onDispose { vm.stopSpeaking() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                eyebrow = "Minutes 5–15 · Masterclass",
                title = "Technical prep",
                subtitle = target?.let { "Tailored to ${it.title}" } ?: "Four focused modules. Read, listen, then say it back.",
            ) {
                CircleIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's listening", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text("${state.completed.size} / $totalLessons", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                }
                Spacer(Modifier.height(10.dp))
                MeterBar(state.completed.size / totalLessons.toFloat(), c.accent)
            }
        }
        state.error?.let { msg -> item { ErrorBanner(msg, onDismiss = vm::dismissError) } }

        items(vm.modules, key = { it.id }) { module ->
            AccordionCard(
                title = module.title,
                subtitle = "${module.subtitle} · ${module.minutes} min",
                expanded = state.expandedModule == module.id,
                onToggle = { vm.toggleModule(module.id) },
                tint = c.tint(module.tint),
                leading = { IconTile(module.icon(), tint = c.textPrimary, background = c.surface.copy(alpha = if (c.isDark) 0.4f else 0.8f)) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    module.lessons.forEach { lesson ->
                        LessonCard(
                            lesson = lesson,
                            expanded = lesson.id in state.expandedLessons,
                            speakingId = speaking,
                            personalised = state.personalised[lesson.id],
                            personalising = lesson.id in state.personalising,
                            onToggle = { vm.toggleLesson(lesson.id) },
                            onSpeak = vm::toggleSpeak,
                            onPersonalise = { vm.personalise(module, lesson) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    expanded: Boolean,
    speakingId: String?,
    personalised: String?,
    personalising: Boolean,
    onToggle: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onPersonalise: () -> Unit,
) {
    val c = AsmTheme.colors
    AccordionCard(
        title = lesson.title,
        subtitle = null,
        expanded = expanded,
        onToggle = onToggle,
        tint = c.surface,
    ) {
        Bullets(lesson.points)
        if (lesson.keyTerms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            ChipFlow(lesson.keyTerms)
        }
        Spacer(Modifier.height(14.dp))
        ReadAloudBox(
            text = lesson.readAloud,
            speaking = speakingId == "${lesson.id}:core",
            onToggle = { onSpeak("${lesson.id}:core", lesson.readAloud) },
        )
        Spacer(Modifier.height(10.dp))
        if (personalised != null) {
            ReadAloudBox(
                text = personalised,
                speaking = speakingId == "${lesson.id}:mine",
                onToggle = { onSpeak("${lesson.id}:mine", personalised) },
                label = "Your version · coach agent",
            )
        } else {
            SecondaryButton(
                text = if (personalising) "Coach is writing…" else "Personalise with AI coach",
                onClick = onPersonalise,
                enabled = !personalising,
                icon = Icons.Rounded.AutoAwesome,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Masterclass.icon(): ImageVector = when (tint) {
    ModuleTint.Amber -> Icons.Rounded.RecordVoiceOver
    ModuleTint.Slate -> Icons.Rounded.Devices
    ModuleTint.Sage -> Icons.Rounded.Construction
    ModuleTint.Rose -> Icons.Rounded.Warning
}
