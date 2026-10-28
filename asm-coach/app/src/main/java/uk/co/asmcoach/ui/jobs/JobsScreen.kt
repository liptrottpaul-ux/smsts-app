package uk.co.asmcoach.ui.jobs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import uk.co.asmcoach.ui.theme.Gradients
import uk.co.asmcoach.ui.theme.ContinuousShape
import uk.co.asmcoach.ui.prep.PrepViewModel
import uk.co.asmcoach.ui.components.ScoreRing
import uk.co.asmcoach.ui.components.HeroCard
import uk.co.asmcoach.ui.components.HeroBadge
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import uk.co.asmcoach.ui.components.IosScreen
import uk.co.asmcoach.ui.components.IosSwitch
import uk.co.asmcoach.ui.components.NavIconButton
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
    val packs by vm.packs.collectAsStateWithLifecycle()
    val latestBuild by vm.updates.latestBuild.collectAsStateWithLifecycle()
    val currentRole by vm.currentRole.collectAsStateWithLifecycle()
    val practice by vm.practice.collectAsStateWithLifecycle()
    val c = AsmTheme.colors
    val uri = LocalUriHandler.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    IosScreen(
        title = "Jobs",
        subtitle = "Pick a real role. The app prepares you for it, then interviews you for it.",
        trailing = { NavIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings) },
    ) {
        item {
            val role = currentRole
            val readiness = role?.let { r -> packs[r.id]?.let { PrepViewModel.readiness(r.id, it, practice).percent } }
            PlanHero(role, readiness) { vm.select(role!!); onOpenStrategy() }
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
                    IosSwitch(checked = state.pastWeekOnly, onCheckedChange = vm::setPastWeekOnly)
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

        state.notice?.let { message ->
            item {
                TonalCard(tint = c.tintSlate, onClick = vm::dismissNotice, contentPadding = 12.dp) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
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
                JobCard(job, briefs[job.id], ready = job.id in packs, expanding = state.expanding == job.id) {
                    vm.open(job, onOpenStrategy)
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobListing, brief: StrategyBrief?, ready: Boolean, expanding: Boolean, onClick: () -> Unit) {
    val c = AsmTheme.colors
    val employer = job.company?.takeIf { it.isNotBlank() } ?: job.source
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            EmployerBadge(employer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(job.company, job.source.takeIf { job.company == null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            if (brief != null) {
                Spacer(Modifier.width(8.dp))
                ScoreRing(brief.compatibilityScore, size = 44.dp, stroke = 4.dp, caption = null)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            job.location?.let { MetaItem(Icons.Rounded.Place, it) }
            job.salary?.let { MetaItem(Icons.Rounded.Payments, it) }
            job.postedLabel?.let { MetaItem(Icons.Rounded.Schedule, it.removePrefix("Posted ")) }
        }
        if (job.snippet.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(job.snippet, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hairline))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                job.isSearchPage -> PillBadge("Several jobs", color = c.slate, background = c.tintSlate)
                ready -> PillBadge("Prep ready", color = c.good, background = c.tintSage)
                job.isSample -> PillBadge("Sample", color = c.textSecondary, background = c.surfaceSunken)
                else -> PillBadge(job.source, color = c.textSecondary, background = c.surfaceSunken)
            }
            Spacer(Modifier.weight(1f))
            if (expanding) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    expanding -> "Finding jobs…"
                    job.isSearchPage -> "Show jobs"
                    ready -> "Open"
                    else -> "Prepare"
                },
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.accent)
        }
    }
}

/** Employer initials on a coloured squircle, colour stable per employer. */
@Composable
private fun EmployerBadge(name: String) {
    val palette = listOf(0xFF0A84FF, 0xFF5E5CE6, 0xFF30B0C7, 0xFFFF9F0A, 0xFF34C759, 0xFFFF375F, 0xFFAF52DE)
    val color = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    val initials = name.split(Regex("[\\s&]+")).filter { it.firstOrNull()?.isLetter() == true }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        Modifier.size(44.dp).clip(ContinuousShape(11.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    val c = AsmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.gray, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
    }
}

/** Top of the Jobs tab: the 3-step routine, or the current role's readiness. */
@Composable
private fun PlanHero(role: JobListing?, readiness: Int?, onContinue: () -> Unit) {
    HeroCard(Gradients.blue, onClick = if (role != null) onContinue else null) {
        if (role == null) {
            HeroBadge("Your 30-minute routine")
            Spacer(Modifier.height(10.dp))
            Text("Find a real role, prep for it, then rehearse the interview.", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 · Find", "2 · Prep", "3 · Interview").forEach { HeroBadge(it) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HeroBadge("Current role")
                    Spacer(Modifier.height(8.dp))
                    Text(role.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(role.company, role.location).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                if (readiness != null) {
                    Spacer(Modifier.width(12.dp))
                    ScoreRing(readiness, size = 72.dp, stroke = 7.dp, caption = "ready", onHero = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Continue →", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}
