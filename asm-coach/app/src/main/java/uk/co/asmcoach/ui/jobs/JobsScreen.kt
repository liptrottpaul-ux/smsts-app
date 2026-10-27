package uk.co.asmcoach.ui.jobs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.data.model.JobListing
import uk.co.asmcoach.data.model.SearchRegion
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.ui.components.ChoiceChip
import uk.co.asmcoach.ui.components.CircleIconButton
import uk.co.asmcoach.ui.components.EmptyState
import uk.co.asmcoach.ui.components.ErrorBanner
import uk.co.asmcoach.ui.components.LargeTitleHeader
import uk.co.asmcoach.ui.components.PillBadge
import uk.co.asmcoach.ui.components.PrimaryButton
import uk.co.asmcoach.ui.components.SecondaryButton
import uk.co.asmcoach.ui.components.SectionLabel
import uk.co.asmcoach.ui.components.SurfaceCard
import uk.co.asmcoach.ui.components.TonalCard
import uk.co.asmcoach.ui.containerViewModel
import uk.co.asmcoach.ui.theme.AsmTheme

@Composable
fun JobsScreen(container: AppContainer, onOpenStrategy: () -> Unit, onOpenSettings: () -> Unit) {
    val vm = containerViewModel { JobsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val hasKey by vm.hasSearchKey.collectAsStateWithLifecycle()
    val briefs by vm.briefs.collectAsStateWithLifecycle()
    val latestBuild by vm.updates.latestBuild.collectAsStateWithLifecycle()
    val c = AsmTheme.colors
    val uri = LocalUriHandler.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitleHeader(
                eyebrow = "Minutes 0–5 · Strategy",
                title = "Find your role",
                subtitle = "Live Assistant Site Manager roles across the North West, scored against your profile.",
            ) {
                CircleIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }

        if ((latestBuild ?: 0) > vm.updates.currentBuild) {
            item {
                TonalCard(tint = c.tintSage, onClick = { uri.openUri(UpdateChecker.DOWNLOAD_URL) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Update available · build $latestBuild", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                "Tap to download, then open the file and choose Update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = c.good)
                    }
                }
            }
        }

        item {
            SurfaceCard {
                Text("Region", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchRegion.entries) { region ->
                        ChoiceChip(region.label, region == state.region) { vm.setRegion(region) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Posted this week", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text("Reed, Indeed, Totaljobs and CV-Library", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Switch(
                        checked = state.pastWeekOnly,
                        onCheckedChange = vm::setPastWeekOnly,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = c.good,
                            checkedThumbColor = c.surface,
                            uncheckedTrackColor = c.hairline,
                            uncheckedBorderColor = c.hairline,
                        ),
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (state.loading) "Searching…" else "Search live jobs",
                    onClick = vm::search,
                    loading = state.loading,
                    enabled = hasKey,
                    icon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!hasKey) {
                    Spacer(Modifier.height(12.dp))
                    TonalCard(tint = c.tintAmber, onClick = onOpenSettings, contentPadding = 14.dp) {
                        Text("Live search needs one free key", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Easiest: sign in at serper.dev with Google, copy your free key, and paste it in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            SecondaryButton("Open Settings", onClick = onOpenSettings, icon = Icons.Rounded.Settings)
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton("Get Serper key", onClick = { uri.openUri("https://serper.dev") })
                        }
                    }
                }
            }
        }

        state.error?.let { message ->
            item { ErrorBanner(message, onDismiss = vm::dismissError, actionLabel = "Retry", onAction = vm::search) }
        }

        if (state.jobs.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Construction,
                    title = if (state.searched) "No listings found" else "Ready when you are",
                    message = if (state.searched) {
                        "Try another region or include older posts."
                    } else {
                        "Search live roles, or explore with sample listings while you set up your keys."
                    },
                ) {
                    SecondaryButton("Try sample listings", onClick = vm::loadSamples)
                }
            }
        } else if (state.jobs.isNotEmpty()) {
            item { SectionLabel("${state.jobs.size} roles") }
            items(state.jobs, key = { it.id }) { job ->
                JobCard(job, briefs[job.id]) {
                    vm.select(job)
                    onOpenStrategy()
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobListing, brief: StrategyBrief?, onClick: () -> Unit) {
    val c = AsmTheme.colors
    TonalCard(tint = c.surface, border = !c.isDark, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillBadge(
                job.source,
                color = if (job.isSample) c.textSecondary else c.slate,
                background = if (job.isSample) c.surfaceSunken else c.tintSlate,
            )
            job.location?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(brief != null) {
                brief?.let {
                    PillBadge(
                        "${it.compatibilityScore}% fit",
                        color = c.scoreColor(it.compatibilityScore),
                        background = c.tintSage,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(job.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        job.company?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        Spacer(Modifier.height(6.dp))
        Text(job.snippet, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            job.salary?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.textPrimary) }
            job.postedLabel?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text("Build strategy", style = MaterialTheme.typography.labelLarge, color = c.accent)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.accent)
        }
    }
}
