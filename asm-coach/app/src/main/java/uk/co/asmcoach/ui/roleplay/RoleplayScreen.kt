package uk.co.asmcoach.ui.roleplay

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import uk.co.asmcoach.data.repo.PrepProgress
import uk.co.asmcoach.data.model.RolePack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.AnswerReview
import uk.co.asmcoach.data.model.CrisisScenario
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.InterviewMode
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.SessionKind
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.model.Speaker
import uk.co.asmcoach.ui.components.Bullets
import uk.co.asmcoach.ui.components.ChoiceChip
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.ErrorBanner
import uk.co.asmcoach.ui.components.LargeTitleHeader
import uk.co.asmcoach.ui.components.MeterBar
import uk.co.asmcoach.ui.components.PillBadge
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.ReadAloudBox
import uk.co.asmcoach.ui.components.ScoreRing
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.SegmentedControl
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.components.TonalCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme
import java.text.DateFormat
import java.util.Date

private val lengths = listOf(4 to "Quick · 4", 8 to "Full · 8", 12 to "Panel · 12")

@Composable
fun RoleplayScreen(container: AppContainer, onOpenSettings: () -> Unit, onOpenJobs: () -> Unit) {
    val vm = containerViewModel { RoleplayViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val target by vm.targetJob.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val speaking by vm.speakingId.collectAsStateWithLifecycle()
    val hasGemini by vm.hasGemini.collectAsStateWithLifecycle()
    val pack by vm.pack.collectAsStateWithLifecycle()
    val progressMap by vm.prepProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.start(voice = granted)
    }
    val startInterview = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || !vm.voiceInputAvailable) vm.start(voice = granted) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Never keep the mic or speaker running when the screen isn't visible.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }
    DisposableEffect(Unit) { onDispose { vm.pause() } }

    AnimatedContent(
        targetState = state.stage,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
        label = "stage",
        modifier = Modifier.fillMaxSize(),
    ) { stage ->
        when (stage) {
            Stage.Setup -> SetupContent(
                state, target, pack, target?.let { progressMap[it.id] }, history, hasGemini, vm, onOpenSettings, onOpenJobs, startInterview,
            )
            Stage.Live -> LiveContent(state, hasGemini, vm, onOpenSettings)
            Stage.Evaluating -> EvaluatingContent()
            Stage.Results -> state.evaluation?.let { ResultsContent(it, speaking, vm) }
        }
    }
}

// ---- Setup -----------------------------------------------------------------------------------

@Composable
private fun SetupContent(
    state: RoleplayUiState,
    target: JobListing?,
    pack: RolePack?,
    progress: PrepProgress?,
    history: List<SessionRecord>,
    hasGemini: Boolean,
    vm: RoleplayViewModel,
    onOpenSettings: () -> Unit,
    onOpenJobs: () -> Unit,
    onStart: () -> Unit,
) {
    val usePack = pack != null && state.tailorToJob
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                eyebrow = "Minutes 15–30 · Live voice",
                title = "Roleplay",
                subtitle = "An interview for the role you picked, with that employer's kind of panel.",
            ) {
                CircleIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }
        state.error?.let { msg -> item { ErrorBanner(msg, onDismiss = vm::dismissError) } }

        item { RoleCard(target, pack, progress, state.tailorToJob, vm, onOpenJobs) }

        item { SectionLabel("Mode") }
        items(InterviewMode.entries) { mode ->
            ModeCard(mode, selected = mode == state.mode) { vm.setMode(mode) }
        }

        if (state.mode == InterviewMode.CrisisSimulation) {
            item {
                SectionLabel("Scenario")
                val roleScenarios = pack?.scenarios.orEmpty().takeIf { usePack }.orEmpty()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (roleScenarios.isNotEmpty()) {
                        itemsIndexed(roleScenarios) { i, sc ->
                            ChoiceChip(sc.title, i == state.roleScenarioIndex) { vm.setRoleScenario(i) }
                        }
                    } else {
                        items(CrisisScenario.entries) { scenario ->
                            ChoiceChip(scenario.title, scenario == state.scenario) { vm.setScenario(scenario) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TonalCard(tint = c.tintRose) {
                    Text(
                        roleScenarios.getOrNull(state.roleScenarioIndex)?.briefing ?: state.scenario.briefing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                    )
                }
            }
        }

        item {
            SectionLabel("Length")
            SegmentedControl(
                options = lengths.map { it.second },
                selectedIndex = lengths.indexOfFirst { it.first == state.questionCount }.coerceAtLeast(0),
                onSelect = { vm.setQuestionCount(lengths[it].first) },
            )
        }

        item {
            SectionLabel(if (usePack) "Your panel for this role" else "Your panel")
            val seats = pack?.panel?.takeIf { usePack && it.isNotEmpty() }
                ?.mapIndexed { i, m -> Seat(m.name, m.role, Persona.entries[i % Persona.entries.size]) }
                ?: state.mode.personas.map { Seat(it.displayName, it.role, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                seats.forEach { SeatMini(it, Modifier.weight(1f)) }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            if (!hasGemini) {
                GeminiKeyCard(onOpenSettings)
                Spacer(Modifier.height(12.dp))
            }
            PrimaryButton(
                "Start interview",
                onClick = onStart,
                enabled = hasGemini,
                icon = Icons.Rounded.Mic,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!vm.voiceInputAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Speech recognition isn't available on this device, so you'll type your answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        val interviews = history.filter { it.kind == SessionKind.Interview }
        if (interviews.isNotEmpty()) {
            item { SectionLabel("Recent sessions") }
            item {
                SurfaceCard(contentPadding = 6.dp) {
                    interviews.take(6).forEachIndexed { i, record ->
                        if (i > 0) Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(c.hairline))
                        HistoryRow(record)
                    }
                }
            }
        }
    }
}

/** Sentinel for the "panel is thinking" state in the question crossfade. */
private const val THINKING = "\u0000thinking"

@Composable
private fun GeminiKeyCard(onOpenSettings: () -> Unit) {
    val c = AsmTheme.colors
    val uri = LocalUriHandler.current
    TonalCard(tint = c.tintAmber, contentPadding = 16.dp) {
        Text("Add your free Gemini key first", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "The interview panel runs on Google Gemini. Create a free key in Google AI Studio " +
                "(sign in with any Google account), then paste it in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row {
            SecondaryButton("Get key", onClick = { uri.openUri("https://aistudio.google.com/apikey") })
            Spacer(Modifier.width(8.dp))
            SecondaryButton("Open Settings", onClick = onOpenSettings, icon = Icons.Rounded.Settings)
        }
    }
}

@Composable
private fun ModeCard(mode: InterviewMode, selected: Boolean, onClick: () -> Unit) {
    val c = AsmTheme.colors
    val tint = when (mode) {
        InterviewMode.StandardPanel -> c.tintAmber
        InterviewMode.CrisisSimulation -> c.tintRose
        InterviewMode.DigitalSiteTech -> c.tintSlate
        InterviewMode.TechnicalTradeQa -> c.tintSage
    }
    TonalCard(
        tint = tint,
        onClick = onClick,
        modifier = if (selected) Modifier.border(2.dp, c.accent, RoundedCornerShape(20.dp)) else Modifier,
        contentPadding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mode.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.accent else Color.Transparent)
                    .border(2.dp, if (selected) c.accent else c.textTertiary, CircleShape),
            )
        }
    }
}

@Composable
private fun RoleCard(
    target: JobListing?,
    pack: RolePack?,
    progress: PrepProgress?,
    tailor: Boolean,
    vm: RoleplayViewModel,
    onOpenJobs: () -> Unit,
) {
    val c = AsmTheme.colors
    if (target == null) {
        TonalCard(tint = c.tintAmber) {
            Text("Pick a real role first", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a job on the Jobs tab and this interview is built for it: its panel, its questions, its scenarios. " +
                    "Without one you get a general ASM interview.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Find a role", onClick = onOpenJobs, icon = Icons.Rounded.Search)
        }
        return
    }
    TonalCard(tint = if (pack != null) c.tintSage else c.tintAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Interviewing for", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(target.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2)
                listOfNotNull(target.company, target.location).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (pack != null) {
                Switch(
                    checked = tailor,
                    onCheckedChange = vm::setTailor,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.good, checkedThumbColor = c.surface),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            pack != null -> Text(
                if (tailor) "Ready: ${pack.questions.size} role questions, ${pack.scenarios.size} scenarios. Questions start instantly."
                else "Role questions off: general ASM interview.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            progress?.running == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(progress?.stage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
            }
            else -> {
                Text(
                    progress?.error ?: "Build this role's panel and questions first (about 30–60 seconds, once).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress?.error != null) c.bad else c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Prepare this role", onClick = vm::prepareRole, icon = Icons.Rounded.AutoAwesome)
            }
        }
    }
}

@Composable
private fun SeatAvatar(seat: Seat, size: Int = 44, active: Boolean = false) {
    val c = AsmTheme.colors
    val color = when (seat.voice) {
        Persona.ProjectDirector -> c.slate
        Persona.CommercialManager -> c.accent
        Persona.SafetyAuditor -> c.good
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ring")
    Box(contentAlignment = Alignment.Center) {
        if (active) {
            Box(Modifier.size(size.dp).scale(ring).clip(CircleShape).background(color.copy(alpha = 0.25f)))
        }
        Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(
                seat.initials,
                style = if (size > 50) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                color = c.background,
            )
        }
    }
}

@Composable
private fun SeatMini(seat: Seat, modifier: Modifier = Modifier) {
    val c = AsmTheme.colors
    SurfaceCard(modifier = modifier, contentPadding = 12.dp) {
        SeatAvatar(seat, size = 36)
        Spacer(Modifier.height(8.dp))
        Text(seat.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
        Text(seat.role, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2)
    }
}

@Composable
private fun HistoryRow(record: SessionRecord) {
    val c = AsmTheme.colors
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(record.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
            Text(
                "${record.subtitle} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        PillBadge("${record.score}", color = c.scoreColor(record.score), background = c.surfaceSunken)
    }
}

// ---- Live ------------------------------------------------------------------------------------

@Composable
private fun LiveContent(state: RoleplayUiState, hasGemini: Boolean, vm: RoleplayViewModel, onOpenSettings: () -> Unit) {
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val lastQuestion = state.transcript.lastOrNull { it.speaker == Speaker.Interviewer }
    val seat = state.activeSeat ?: state.seats.firstOrNull() ?: state.mode.personas.first().let { Seat(it.displayName, it.role, it) }
    var showTranscript by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillBadge(if (state.onFollowUp) "Follow-up · question ${state.mainAsked} of ${state.total}" else "Question ${state.mainAsked.coerceAtLeast(1)} of ${state.total}")
                Spacer(Modifier.weight(1f))
                if (state.phase == Phase.Paused) {
                    CircleIconButton(Icons.Rounded.PlayArrow, "Resume", vm::resume)
                } else {
                    CircleIconButton(Icons.Rounded.Pause, "Pause", vm::pause)
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Rounded.Close, "End and score", vm::endEarly)
            }
            Spacer(Modifier.height(10.dp))
            MeterBar((state.mainAsked - 1).coerceAtLeast(0) / state.total.coerceAtLeast(1).toFloat(), c.accent)
        }

        item {
            TonalCard(tint = c.surface, border = !c.isDark, contentPadding = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeatAvatar(seat, size = 56, active = state.phase == Phase.Speaking)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(seat.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(seat.role, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(18.dp))
                AnimatedContent(
                    targetState = if (state.phase == Phase.Thinking) THINKING else lastQuestion?.text,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                    label = "question",
                ) { text ->
                    if (text == THINKING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("${seat.name} is making a note…", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        }
                    } else if (text == null) {
                        Text("Waiting for the panel.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    } else {
                        Text(text, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    }
                }
                if (state.phase == Phase.Speaking) {
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton("Skip to my answer", onClick = vm::skipQuestionAudio, icon = Icons.Rounded.SkipNext)
                }
            }
        }

        state.error?.let { msg ->
            item {
                ErrorBanner(
                    msg,
                    onDismiss = vm::dismissError,
                    actionLabel = when {
                        !hasGemini -> "Settings"
                        state.phase == Phase.Idle -> "Try again"
                        else -> null
                    },
                    onAction = if (!hasGemini) onOpenSettings else vm::retry,
                )
            }
        }

        item {
            when (state.phase) {
                Phase.Listening -> ListeningPanel(state, vm)
                Phase.Typing -> TypingPanel(vm)
                Phase.Paused -> PausedPanel(vm::resume)
                Phase.Idle -> { if (state.error == null) PausedPanel(vm::retry, label = "Continue") }
                Phase.Thinking, Phase.Speaking -> {}
            }
        }

        if (state.transcript.size > 1) {
            item {
                Text(
                    if (showTranscript) "Hide transcript" else "Show transcript",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clickable { showTranscript = !showTranscript }.padding(vertical = 6.dp),
                )
            }
            if (showTranscript) {
                items(state.transcript) { turn ->
                    val mine = turn.speaker == Speaker.Candidate
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Text(
                            if (mine) "You" else turn.speakerName ?: "Panel",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            turn.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) c.onAccent else c.textPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (mine) c.accent else c.surfaceRaised)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningPanel(state: RoleplayUiState, vm: RoleplayViewModel) {
    val c = AsmTheme.colors
    val orb by animateFloatAsState(1f + state.micLevel * 0.35f, tween(120), label = "orb")
    TonalCard(tint = c.tintAmber, contentPadding = 22.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).scale(orb).clip(CircleShape).background(c.accent.copy(alpha = 0.22f)))
                Box(Modifier.size(68.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Listening", tint = c.onAccent, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                state.liveText.ifBlank { "Listening… take your time and use STAR." },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.liveText.isBlank()) c.textSecondary else c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done answering", onClick = vm::finishSpeaking, icon = Icons.Rounded.Stop, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Type instead",
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.clip(CircleShape).clickable(onClick = vm::switchToTyping).padding(10.dp),
            )
        }
    }
}

@Composable
private fun TypingPanel(vm: RoleplayViewModel) {
    val c = AsmTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    SurfaceCard {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Type your answer…") },
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
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.voiceInputAvailable) {
                SecondaryButton("Speak", onClick = vm::switchToVoice, icon = Icons.Rounded.Mic)
                Spacer(Modifier.width(10.dp))
            }
            PrimaryButton(
                "Send answer",
                onClick = {
                    vm.submitAnswer(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                icon = Icons.AutoMirrored.Rounded.Send,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PausedPanel(onResume: () -> Unit, label: String = "Resume interview") {
    PrimaryButton(label, onClick = onResume, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EvaluatingContent() {
    val c = AsmTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text("The panel is scoring you", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Readiness score, STAR critique and better answers are on their way.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Results ---------------------------------------------------------------------------------

@Composable
private fun ResultsContent(evaluation: Evaluation, speakingId: String?, vm: RoleplayViewModel) {
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(eyebrow = "Post-interview evaluation", title = "How you did")
        }
        item {
            SurfaceCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreRing(evaluation.overallScore, size = 150.dp, stroke = 12.dp)
                    Spacer(Modifier.height(14.dp))
                    PillBadge(
                        evaluation.readinessBand.ifBlank { "Readiness" },
                        color = c.scoreColor(evaluation.overallScore),
                        background = c.surfaceSunken,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(evaluation.headline, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                }
            }
        }
        if (evaluation.offerVerdict.isNotBlank() || evaluation.offerLikelihood > 0) {
            item { OfferCard(evaluation) }
        }
        if (evaluation.redFlags.isNotEmpty()) {
            item {
                SectionLabel("Red flags a panel would mark down")
                TonalCard(tint = c.tintRose) { Bullets(evaluation.redFlags, dotColor = c.bad) }
            }
        }
        if (evaluation.answerReviews.isNotEmpty()) {
            item { SectionLabel("Every answer, reviewed") }
            itemsIndexed(evaluation.answerReviews) { index, review ->
                AnswerReviewCard(index, review, speakingId, vm)
            }
        }
        if (evaluation.competencies.isNotEmpty()) {
            item {
                SectionLabel("Competencies")
                SurfaceCard {
                    evaluation.competencies.forEachIndexed { i, comp ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        Row {
                            Text(comp.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text("${comp.score}", style = MaterialTheme.typography.labelLarge, color = c.scoreColor(comp.score))
                        }
                        Spacer(Modifier.height(6.dp))
                        MeterBar(comp.score / 100f, c.scoreColor(comp.score))
                    }
                }
            }
        }
        if (evaluation.strengths.isNotEmpty()) {
            item {
                SectionLabel("What landed")
                TonalCard(tint = c.tintSage) { Bullets(evaluation.strengths, dotColor = c.good) }
            }
        }
        if (evaluation.improvements.isNotEmpty()) {
            item {
                SectionLabel("Fix next time")
                TonalCard(tint = c.tintRose) { Bullets(evaluation.improvements, dotColor = c.bad) }
            }
        }
        if (evaluation.answerReviews.isEmpty() && evaluation.starCritique.isNotEmpty()) {
            item { SectionLabel("STAR critique") }
            items(evaluation.starCritique) { critique ->
                SurfaceCard {
                    Text(critique.question, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    if (critique.answerSummary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(critique.answerSummary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StarPip("S", critique.situation, Modifier.weight(1f))
                        StarPip("T", critique.task, Modifier.weight(1f))
                        StarPip("A", critique.action, Modifier.weight(1f))
                        StarPip("R", critique.result, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(critique.critique, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
        }
        if (evaluation.answerReviews.isEmpty() && evaluation.betterAnswers.isNotEmpty()) {
            item { SectionLabel("Better answers you should have used") }
            items(evaluation.betterAnswers.size) { index ->
                val answer = evaluation.betterAnswers[index]
                TonalCard(tint = c.tintAmber) {
                    Text(answer.question, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    if (answer.whyItWins.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(answer.whyItWins, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    ReadAloudBox(
                        text = answer.script,
                        speaking = speakingId == "better-$index",
                        onToggle = { vm.toggleSpeak("better-$index", answer.script) },
                        label = "Say this instead",
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Run another interview", onClick = vm::reset, icon = Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OfferCard(evaluation: Evaluation) {
    val c = AsmTheme.colors
    val likelihood = evaluation.offerLikelihood
    TonalCard(tint = if (likelihood >= 60) c.tintSage else if (likelihood >= 35) c.tintAmber else c.tintRose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(likelihood, size = 84.dp, stroke = 8.dp, caption = "%")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Chance of an offer", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(evaluation.offerVerdict.ifBlank { "Offer likelihood" }, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(
                    "Against a typical shortlist for this role, on this performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        if (evaluation.likelihoodReasons.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Why", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            Bullets(evaluation.likelihoodReasons, dotColor = c.slate)
        }
        if (evaluation.toRaiseIt.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("To raise your chances", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            Bullets(evaluation.toRaiseIt, dotColor = c.good)
        }
    }
}

@Composable
private fun AnswerReviewCard(index: Int, review: AnswerReview, speakingId: String?, vm: RoleplayViewModel) {
    val c = AsmTheme.colors
    var open by rememberSaveable { mutableStateOf(review.score <= 3) }
    SurfaceCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.Top) {
            Text(review.question, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            PillBadge("${review.score}/5", color = c.scoreColor(review.score * 20), background = c.surfaceSunken)
        }
        if (open) {
            if (review.yourAnswer.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("You said: ${review.yourAnswer}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StarPip("S", review.situation, Modifier.weight(1f))
                StarPip("T", review.task, Modifier.weight(1f))
                StarPip("A", review.action, Modifier.weight(1f))
                StarPip("R", review.result, Modifier.weight(1f))
            }
            if (review.whatWorked.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Worked: ${review.whatWorked}", style = MaterialTheme.typography.bodyMedium, color = c.good)
            }
            if (review.whatWasMissing.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Missing: ${review.whatWasMissing}", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
            if (review.betterAnswer.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ReadAloudBox(
                    text = review.betterAnswer,
                    speaking = speakingId == "review-$index",
                    onToggle = { vm.toggleSpeak("review-$index", review.betterAnswer) },
                    label = "A better answer",
                )
            }
        }
    }
}

@Composable
private fun StarPip(letter: String, score: Int, modifier: Modifier = Modifier) {
    val c = AsmTheme.colors
    val color = c.scoreColor(score * 20)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(letter, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        MeterBar(score / 5f, color)
        Spacer(Modifier.height(4.dp))
        Text("$score/5", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
    }
}
