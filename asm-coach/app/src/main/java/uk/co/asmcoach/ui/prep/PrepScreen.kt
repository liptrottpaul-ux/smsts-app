package uk.co.asmcoach.ui.prep

import androidx.compose.foundation.layout.Arrangement
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.TonalCard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
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
fun PrepScreen(container: AppContainer, onOpenSettings: () -> Unit, onOpenJobs: () -> Unit, onInterview: () -> Unit) {
    val vm = containerViewModel { PrepViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speaking by vm.speakingId.collectAsStateWithLifecycle()
    val target by vm.targetJob.collectAsStateWithLifecycle()
    val packs by vm.packs.collectAsStateWithLifecycle()
    val progressMap by vm.progress.collectAsStateWithLifecycle()
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val job = target
    val pack = job?.let { packs[it.id] }
    val progress = job?.let { progressMap[it.id] }

    DisposableEffect(Unit) { onDispose { vm.stopSpeaking() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                eyebrow = "Minutes 5–15 · Role prep",
                title = "Prep",
                subtitle = job?.let { listOfNotNull(it.title, it.company, it.location).joinToString(" · ") }
                    ?: "Pick a role on the Jobs tab and your prep is built for it.",
            ) {
                CircleIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }
        state.error?.let { msg -> item { ErrorBanner(msg, onDismiss = vm::dismissError) } }

        when {
            job == null -> item {
                TonalCard(tint = c.tintAmber) {
                    Text("No role selected yet", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Search live jobs, tap a role, and the app writes prep and an interview specific to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Find a role", onClick = onOpenJobs, icon = Icons.Rounded.Search)
                }
            }
            pack == null -> item {
                TonalCard(tint = c.tintAmber) {
                    if (progress?.running == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(progress?.stage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    } else {
                        Text("Prep for this role isn't built yet", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        progress?.error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = c.bad)
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Prepare this role", onClick = vm::prepareRole, icon = Icons.Rounded.AutoAwesome)
                    }
                }
            }
            else -> {
                if (pack.roleSummary.isNotBlank()) {
                    item {
                        TonalCard(tint = c.tintSlate) {
                            Text("What this role needs", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(pack.roleSummary, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
                item { SectionLabel("Prep topics for this role") }
                itemsIndexed(pack.prepTopics) { i, topic ->
                    val id = "topic-$i"
                    AccordionCard(
                        title = topic.title,
                        subtitle = topic.whyItMatters.takeIf { it.isNotBlank() },
                        expanded = id in state.expandedLessons,
                        onToggle = { vm.toggleLesson(id) },
                        tint = c.tintSage,
                    ) {
                        Bullets(topic.points)
                        if (topic.sayThis.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            ReadAloudBox(
                                text = topic.sayThis,
                                speaking = speaking == "$id:say",
                                onToggle = { vm.toggleSpeak("$id:say", topic.sayThis) },
                            )
                        }
                    }
                }
                item { SectionLabel("Questions this panel is likely to ask") }
                itemsIndexed(pack.questions) { i, q ->
                    val id = "q-$i"
                    AccordionCard(
                        title = q.question,
                        subtitle = listOfNotNull(q.category.takeIf { it.isNotBlank() }, pack.panel.getOrNull(q.askedBy)?.role)
                            .joinToString(" · ")
                            .ifBlank { null },
                        expanded = id in state.expandedLessons,
                        onToggle = { vm.toggleLesson(id) },
                    ) {
                        if (q.lookingFor.isNotBlank()) {
                            Text("They're looking for", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text(q.lookingFor, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                            Spacer(Modifier.height(12.dp))
                        }
                        ReadAloudBox(
                            text = q.modelAnswer,
                            speaking = speaking == "$id:answer",
                            onToggle = { vm.toggleSpeak("$id:answer", q.modelAnswer) },
                            label = "A strong answer from you",
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton(
                        "Interview for this role",
                        onClick = onInterview,
                        icon = Icons.Rounded.Mic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionLabel("Core skills · any ASM role") }
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
