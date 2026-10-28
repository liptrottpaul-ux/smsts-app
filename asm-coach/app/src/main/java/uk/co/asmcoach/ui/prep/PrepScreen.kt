package uk.co.asmcoach.ui.prep

import androidx.compose.foundation.layout.Arrangement
import uk.co.asmcoach.ui.theme.ChartColors
import uk.co.asmcoach.ui.components.RingValue
import uk.co.asmcoach.ui.components.ConcentricRings
import uk.co.asmcoach.ui.theme.Gradients
import uk.co.asmcoach.ui.strategy.ThinSourceCard
import uk.co.asmcoach.ui.components.HeroCard
import uk.co.asmcoach.ui.components.HeroBadge
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import uk.co.asmcoach.ui.components.ScoreRing
import uk.co.asmcoach.data.persist.DrillResult
import uk.co.asmcoach.data.content.PrepChecklist
import uk.co.asmcoach.data.content.ChecklistItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
import uk.co.asmcoach.ui.components.IosScreen
import uk.co.asmcoach.ui.components.NavIconButton
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
    val practice by vm.practice.collectAsStateWithLifecycle()
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val job = target
    val pack = job?.let { packs[it.id] }
    val progress = job?.let { progressMap[it.id] }

    DisposableEffect(Unit) { onDispose { vm.stopSpeaking() } }

    IosScreen(
        title = "Prep",
        subtitle = job?.let { listOfNotNull(it.title, it.company, it.location).joinToString(" · ") }
            ?: "Pick a role on the Jobs tab and your prep is built for it.",
        trailing = { NavIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings) },
    ) {
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
                val readiness = PrepViewModel.readiness(job.id, pack, practice)
                item { ReadinessCard(readiness, onInterview) }
                if (progress?.running == true) {
                    item {
                        TonalCard(tint = c.tintSlate, contentPadding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(progress?.stage.orEmpty(), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                    Text(
                                        "Start with what's here; more questions appear as they're written.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (pack.thinSource) item { ThinSourceCard() }
                if (pack.basedOn.isNotBlank()) {
                    item {
                        Text(
                            "Built from: ${pack.basedOn}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (pack.roleSummary.isNotBlank()) {
                    item {
                        TonalCard(tint = c.tintSlate) {
                            Text("What this role needs", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(pack.roleSummary, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
                item { SectionLabel("1 · Before the interview") }
                item {
                    SurfaceCard(contentPadding = 8.dp) {
                        PrepChecklist.items.forEach { item ->
                            ChecklistRow(item, done = "${job.id}|${item.id}" in practice.done) { vm.toggleChecklist(item.id) }
                        }
                    }
                }
                item { SectionLabel("2 · Prep topics for this role") }
                itemsIndexed(pack.prepTopics) { i, topic ->
                    val id = "topic-$i"
                    AccordionCard(
                        title = topic.title,
                        subtitle = (if ("${job.id}|$id" in practice.done) "✓ Reviewed · " else "") + topic.whyItMatters,
                        expanded = id in state.expandedLessons,
                        onToggle = { vm.toggleLesson(id) },
                        tint = c.surface,
                    ) {
                        if (topic.listingRef.isNotBlank()) {
                            AdvertQuote(topic.listingRef)
                            Spacer(Modifier.height(10.dp))
                        }
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
                item { SectionLabel("3 · Practise this panel's questions out loud") }
                itemsIndexed(pack.questions) { i, q ->
                    val id = "q-$i"
                    val key = PrepViewModel.questionKey(q)
                    val drill = practice.drills["${job.id}|$key"]
                    AccordionCard(
                        title = q.question,
                        subtitle = listOfNotNull(
                            drill?.let { "Best ${it.score}/5" },
                            q.category.takeIf { it.isNotBlank() },
                            pack.panel.getOrNull(q.askedBy)?.role,
                        ).joinToString(" · ").ifBlank { null },
                        expanded = id in state.expandedLessons,
                        onToggle = { vm.toggleLesson(id) },
                    ) {
                        if (q.listingRef.isNotBlank()) {
                            AdvertQuote(q.listingRef)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (q.lookingFor.isNotBlank()) {
                            Text("They're looking for", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text(q.lookingFor, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                            Spacer(Modifier.height(12.dp))
                        }
                        if (q.probes.isNotEmpty()) {
                            Text("Expect follow-ups like", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Bullets(q.probes, dotColor = c.slate)
                            Spacer(Modifier.height(12.dp))
                        }
                        DrillPanel(
                            active = state.drillKey == key,
                            text = state.drillText,
                            listening = state.drillListening,
                            scoring = state.scoring && state.drillKey == key,
                            voiceAvailable = vm.voiceAvailable,
                            result = drill,
                            onStart = { vm.startDrill(key) },
                            onEdit = vm::editDrill,
                            onMic = vm::toggleMic,
                            onScore = { vm.scoreDrill(key, q) },
                            onCancel = vm::cancelDrill,
                            speaking = speaking,
                            speakId = "$id:better",
                            onSpeak = vm::toggleSpeak,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (key in state.revealed || drill != null) {
                            ReadAloudBox(
                                text = q.modelAnswer,
                                speaking = speaking == "$id:answer",
                                onToggle = { vm.toggleSpeak("$id:answer", q.modelAnswer) },
                                label = "Model answer",
                            )
                        } else {
                            Text(
                                "Show model answer (try it yourself first)",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.accent,
                                modifier = Modifier.clickable { vm.reveal(key) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton(
                        "4 · Mock interview for this role",
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
                leading = { IconTile(module.icon(), tint = Color.White, background = module.tileColor()) },
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
private fun ReadinessCard(r: Readiness, onInterview: () -> Unit) {
    HeroCard(Gradients.night) {
        HeroBadge("Interview readiness")
        Spacer(Modifier.height(6.dp))
        Text(r.verdict, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(14.dp))
        ConcentricRings(
            rings = listOf(
                RingValue(
                    r.questionsPractised.toFloat() / r.questionsTotal.coerceAtLeast(1) * (r.averageScore.toFloat() / 5f).coerceAtLeast(0.2f),
                    ChartColors.coral, "Answers practised",
                    "${r.questionsPractised}/${r.questionsTotal}" + if (r.questionsPractised > 0) " · ${"%.1f".format(r.averageScore)}" else "",
                ),
                RingValue(r.topicsReviewed.toFloat() / r.topicsTotal.coerceAtLeast(1), ChartColors.teal, "Topics", "${r.topicsReviewed}/${r.topicsTotal}"),
                RingValue(r.checklistDone.toFloat() / r.checklistTotal.coerceAtLeast(1), ChartColors.olive, "Prep steps", "${r.checklistDone}/${r.checklistTotal}"),
                RingValue((r.lastInterviewScore ?: 0) / 100f, ChartColors.sage, "Mock interview", r.lastInterviewScore?.let { "$it/100" } ?: "—"),
            ),
            centre = "${r.percent}%",
            centreCaption = "ready",
            size = 140.dp,
            stroke = 10.dp,
            onHero = true,
        )
        r.lastOfferLikelihood?.let {
            Spacer(Modifier.height(10.dp))
            Text("Last mock: offer chance $it%", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        if (r.nextSteps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Do next", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Bullets(r.nextSteps.take(3), dotColor = ChartColors.teal, textColor = Color.White)
        }
        if (r.percent >= 50) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Start mock interview →",
                style = MaterialTheme.typography.labelLarge,
                color = ChartColors.teal,
                modifier = Modifier.clickable(onClick = onInterview).padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ReadinessBar(label: String, value: String, fraction: Float) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
    }
}

/** The advert line a topic or question comes from, styled as a quote. */
@Composable
private fun AdvertQuote(text: String) {
    val c = AsmTheme.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accent))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("From the advert", style = MaterialTheme.typography.labelMedium, color = c.accent)
            Text("\u201C$text\u201D", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, done: Boolean, onToggle: () -> Unit) {
    val c = AsmTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) c.good else c.textTertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun DrillPanel(
    active: Boolean,
    text: String,
    listening: Boolean,
    scoring: Boolean,
    voiceAvailable: Boolean,
    result: DrillResult?,
    onStart: () -> Unit,
    onEdit: (String) -> Unit,
    onMic: () -> Unit,
    onScore: () -> Unit,
    onCancel: () -> Unit,
    speaking: String?,
    speakId: String,
    onSpeak: (String, String) -> Unit,
) {
    val c = AsmTheme.colors
    if (result != null && !active) {
        TonalCard(tint = if (result.score >= 4) c.tintSage else if (result.score == 3) c.tintAmber else c.tintRose, contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your best: ${result.score}/5", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Try again",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clickable(onClick = onStart).padding(6.dp),
                )
            }
            if (result.feedback.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(result.feedback, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
            }
            if (result.betterAnswer.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                ReadAloudBox(
                    text = result.betterAnswer,
                    speaking = speaking == speakId,
                    onToggle = { onSpeak(speakId, result.betterAnswer) },
                    label = "Better version of your answer",
                )
            }
        }
        return
    }
    if (!active) {
        PrimaryButton("Practise this answer", onClick = onStart, icon = Icons.Rounded.Mic, modifier = Modifier.fillMaxWidth())
        return
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = text,
            onValueChange = onEdit,
            placeholder = { Text(if (voiceAvailable) "Tap the mic and answer out loud, or type…" else "Type your answer…") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surfaceSunken,
                unfocusedContainerColor = c.surfaceSunken,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = c.accent,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (voiceAvailable) {
                SecondaryButton(if (listening) "Done" else "Speak", onClick = onMic, icon = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic)
            }
            PrimaryButton(
                if (scoring) "Scoring…" else "Score it",
                onClick = onScore,
                loading = scoring,
                enabled = text.isNotBlank() && !listening,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = c.textSecondary,
            modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
        )
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
        tint = c.surfaceRaised,
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

/** Settings-style tile colours: systemOrange, systemBlue, systemGreen, systemRed. */
private fun Masterclass.tileColor(): Color = when (tint) {
    ModuleTint.Amber -> Color(0xFFFF9500)
    ModuleTint.Slate -> Color(0xFF007AFF)
    ModuleTint.Sage -> Color(0xFF34C759)
    ModuleTint.Rose -> Color(0xFFFF3B30)
}

private fun Masterclass.icon(): ImageVector = when (tint) {
    ModuleTint.Amber -> Icons.Rounded.RecordVoiceOver
    ModuleTint.Slate -> Icons.Rounded.Devices
    ModuleTint.Sage -> Icons.Rounded.Construction
    ModuleTint.Rose -> Icons.Rounded.Warning
}
