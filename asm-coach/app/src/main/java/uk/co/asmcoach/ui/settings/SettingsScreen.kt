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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.data.settings.SettingsRepository
import uk.co.asmcoach.ui.components.ChoiceChip
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.PillBadge
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(container) }
    val form by vm.form.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val c = AsmTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                form.message?.let { PillBadge(it, color = c.good, background = c.tintSage) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
            Text(
                "Everything runs on free tiers. Keys stay on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
            )
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
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.hairline),
                )
                SecondaryButton("Preview voice", onClick = vm::previewVoice, icon = Icons.AutoMirrored.Rounded.VolumeUp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Uses your phone's own speech engines. For the best British voice, install " +
                        "\"English (United Kingdom)\" voice data in Android's text-to-speech settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

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

        item {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "Save",
                onClick = { vm.save(onBack) },
                loading = form.saving,
                enabled = form.loaded,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AsmTheme.colors.surfaceSunken,
    unfocusedContainerColor = AsmTheme.colors.surfaceSunken,
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
