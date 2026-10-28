package uk.co.asmcoach.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.data.settings.SettingsRepository
import uk.co.asmcoach.ui.components.ChoiceChip
import uk.co.asmcoach.ui.components.IosScreen
import uk.co.asmcoach.ui.components.NavTextButton
import uk.co.asmcoach.ui.theme.switchOff
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.MeterBar
import uk.co.asmcoach.ui.components.PillBadge
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme
import uk.co.asmcoach.voice.PackState
import uk.co.asmcoach.voice.VoicePack

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(container) }
    val form by vm.form.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val packs by vm.voicePacks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Pick up voices installed while the user was in Android's settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshVoices() }
    val uri = LocalUriHandler.current
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    IosScreen(
        title = "Settings",
        largeTitle = false,
        modifier = Modifier.imePadding(),
        spacing = 8.dp,
        leading = { NavTextButton("Cancel", onBack) },
        trailing = { NavTextButton("Done", { vm.save(onBack) }, prominent = true, enabled = form.loaded && !form.saving) },
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                form.message?.let {
                    PillBadge(it, color = c.good, background = c.tintSage)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Everything runs on free tiers. Keys stay on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        item { SectionLabel("AI agents · Google Gemini") }
        item {
            SurfaceCard {
                KeyField("Gemini API key", form.geminiKey) { v -> vm.edit { it.copy(geminiKey = v) } }
                LinkText("Get a free key at Google AI Studio") { uri.openUri("https://aistudio.google.com/apikey") }
                Spacer(Modifier.height(14.dp))
                PlainField("Model", form.model) { v -> vm.edit { it.copy(model = v) } }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SettingsRepository.SUGGESTED_MODELS) { model ->
                        ChoiceChip(model, model == form.model) { vm.edit { it.copy(model = model) } }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    if (form.testing) "Testing…" else "Test connection",
                    onClick = vm::testGemini,
                    icon = Icons.Rounded.Bolt,
                    enabled = !form.testing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { SectionLabel("Live job search") }
        item {
            SurfaceCard {
                Text(
                    "One key is enough. Serper is the easiest: sign in with Google and copy the key from your dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                KeyField("Serper API key", form.serperKey) { v -> vm.edit { it.copy(serperKey = v) } }
                LinkText("2,500 free searches at serper.dev") { uri.openUri("https://serper.dev") }
                Spacer(Modifier.height(14.dp))
                KeyField("Reed API key (optional)", form.reedKey) { v -> vm.edit { it.copy(reedKey = v) } }
                LinkText("Optional: adds salaries and full job details") { uri.openUri("https://www.reed.co.uk/developers/jobseeker") }
                Spacer(Modifier.height(14.dp))
                KeyField("Firecrawl API key", form.firecrawlKey) { v -> vm.edit { it.copy(firecrawlKey = v) } }
                LinkText("Free credits at firecrawl.dev (also reads full listings)") { uri.openUri("https://firecrawl.dev") }
            }
        }

        item { SectionLabel("Your profile") }
        item {
            SurfaceCard {
                PlainField("First name", form.name) { v -> vm.edit { it.copy(name = v) } }
                Spacer(Modifier.height(12.dp))
                PlainField(
                    "Extra notes for the coach (CSCS card, projects, availability…)",
                    form.notes,
                    minLines = 3,
                ) { v -> vm.edit { it.copy(notes = v) } }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Built in: 44, HND Construction, SMSTS, 10 years roofing/waterproofing/drylining, 8 years M&A deal execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        item { SectionLabel("Voice") }
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speaking rate", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text("%.2fx".format(form.speechRate), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                }
                Slider(
                    value = form.speechRate,
                    onValueChange = { v -> vm.edit { it.copy(speechRate = v) } },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.switchOff, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                SecondaryButton("Preview the panel's voices", onClick = vm::previewVoice, icon = Icons.AutoMirrored.Rounded.VolumeUp)
                Spacer(Modifier.height(14.dp))
                Text("British English voices on this phone", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    "${voices.male} male · ${voices.female} female" +
                        (if (voices.other > 0) " · ${voices.other} unlabelled" else "") +
                        (if (voices.online > 0) " · natural online voices available" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (voices.hasBoth) c.good else c.warn,
                )
                if (packs.values.none { it == PackState.Installed } && (!voices.hasBoth || voices.online == 0)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "For natural, gender-matched interviewers: open Text-to-speech settings, choose " +
                            "\"Speech Services by Google\" as the engine, then install \"English (United Kingdom)\" " +
                            "voice data. Come back and tap Preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Text-to-speech settings", onClick = {
                        runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                    SecondaryButton("Install voices", onClick = {
                        runCatching {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
            }
        }

        item { SectionLabel("Natural voices") }
        item { NaturalVoicesCard(packs, onDownload = vm::downloadVoices, onDelete = vm::deleteVoice, onPreview = vm::previewVoice) }

        item { SectionLabel("App updates") }
        item {
            SurfaceCard {
                Text("ASM Coach · build ${vm.currentBuild}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (val u = update) {
                        UpdateStatus.Idle -> "Check GitHub for a newer build."
                        UpdateStatus.Checking -> "Checking…"
                        UpdateStatus.UpToDate -> "You're on the latest build."
                        is UpdateStatus.Available -> "Build ${u.build} is available."
                        is UpdateStatus.Failed -> u.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                if (update is UpdateStatus.Available) {
                    PrimaryButton(
                        "Download update",
                        onClick = { uri.openUri(UpdateChecker.DOWNLOAD_URL) },
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "When it finishes, open the downloaded file and choose Update. Your keys stay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                } else {
                    SecondaryButton(
                        "Check for updates",
                        onClick = vm::checkForUpdates,
                        icon = Icons.Rounded.Refresh,
                        enabled = update != UpdateStatus.Checking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionLabel("Sync") }
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .width(10.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (vm.cloudSync) c.good else c.textTertiary),
                    )
                    Column {
                        Text(
                            if (vm.cloudSync) "Firestore sync on" else "On-device only",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary,
                        )
                        Text(
                            if (vm.cloudSync) {
                                "Briefs and scores back up to your Firebase project."
                            } else {
                                "Add app/google-services.json and rebuild to enable Firestore."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AsmTheme.colors.surfaceRaised,
    unfocusedContainerColor = AsmTheme.colors.surfaceRaised,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = AsmTheme.colors.accent,
    focusedLabelColor = AsmTheme.colors.textSecondary,
    unfocusedLabelColor = AsmTheme.colors.textTertiary,
)

@Composable
private fun PlainField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = fieldColors(),
    )
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key",
                    tint = AsmTheme.colors.textTertiary,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = fieldColors(),
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = AsmTheme.colors.accent,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/**
 * Downloadable neural voices: far more human than the phone's built-in voices, offline and free.
 * Each pack is a one-off download; the panel uses them automatically once installed.
 */
@Composable
private fun NaturalVoicesCard(
    packs: Map<VoicePack, PackState>,
    onDownload: (List<VoicePack>) -> Unit,
    onDelete: (VoicePack) -> Unit,
    onPreview: () -> Unit,
) {
    val c = AsmTheme.colors
    val installed = packs.filterValues { it == PackState.Installed }.keys
    val recommended = VoicePack.entries.filter { it.recommended && it !in installed }
    SurfaceCard {
        Text(
            if (installed.isEmpty()) "Make the panel sound like real people" else "Natural voices are on",
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Human-sounding British voices that run on your phone: free, no internet needed once " +
                "downloaded, and a different person for each interviewer. Best downloaded on Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        if (recommended.isNotEmpty() && recommended.none { packs[it] is PackState.Downloading }) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Download the panel voices (${recommended.sumOf { it.sizeMb }} MB)",
                onClick = { onDownload(recommended) },
                icon = Icons.Rounded.Download,
            )
        }
        VoicePack.entries.forEach { pack ->
            val state = packs[pack] ?: PackState.Missing
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Text(
                        when (state) {
                            PackState.Installed -> "${pack.detail} · installed"
                            is PackState.Downloading -> "Downloading… ${(state.fraction * 100).toInt()}%"
                            is PackState.Failed -> state.message
                            PackState.Missing -> "${pack.detail} · ${pack.sizeMb} MB"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (state) {
                            PackState.Installed -> c.good
                            is PackState.Failed -> c.bad
                            else -> c.textSecondary
                        },
                    )
                }
                when (state) {
                    PackState.Installed -> NavTextButton("Remove", { onDelete(pack) })
                    is PackState.Downloading -> Unit
                    else -> NavTextButton(if (state is PackState.Failed) "Retry" else "Get", { onDownload(listOf(pack)) }, prominent = true)
                }
            }
            if (state is PackState.Downloading) {
                Spacer(Modifier.height(6.dp))
                MeterBar(state.fraction, c.accent, Modifier.fillMaxWidth())
            }
        }
        if (installed.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SecondaryButton("Hear the panel", onClick = onPreview, icon = Icons.AutoMirrored.Rounded.VolumeUp)
        }
    }
}
