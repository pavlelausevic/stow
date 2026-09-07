// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.combine
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.db.TripProgressByTrip
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.ui.components.BottomDestination
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.EmptyState
import rs.lausevic.stow.ui.components.IconAction
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.ProgressStrip
import rs.lausevic.stow.ui.components.StowBottomBar
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.components.StowCard
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.theme.StowTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TripRow(val trip: TripEntity, val progress: TripProgressByTrip?)

@Composable
fun TripsScreen(
    container: AppContainer,
    destinations: List<BottomDestination>,
    route: String,
    onSelectTab: (BottomDestination) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onNewTrip: () -> Unit,
) {
    val flow = remember(container) {
        combine(
            container.trips.observeAll(),
            container.trips.observeAllProgress(),
        ) { trips, progress ->
            val byTrip = progress.associateBy { it.tripId }
            trips.map { TripRow(it, byTrip[it.id]) }
        }
    }
    val rows by flow.collectAsState(initial = emptyList())

    val activeCount by container.trips.observeActiveCount().collectAsState(initial = 0)
    val archivedCount by container.trips.observeArchivedCount().collectAsState(initial = 0)
    val itemCount by container.catalog.observeItemCount().collectAsState(initial = 0)
    val taskCount by container.catalog.observeTaskCount().collectAsState(initial = 0)

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.trips_title),
                subtitle = stringResource(R.string.trips_subtitle, activeCount, archivedCount),
                actions = {
                    IconAction(
                        icon = StowIcons.Add,
                        description = stringResource(R.string.trips_empty_action),
                        onClick = onNewTrip,
                    )
                },
            )
        },
        bottomBar = { StowBottomBar(destinations, route, onSelectTab) },
    ) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.trips_empty_title),
                    body = stringResource(R.string.trips_empty_body, itemCount, taskCount),
                    action = {
                        StowButton(
                            text = stringResource(R.string.trips_empty_action),
                            onClick = onNewTrip,
                            style = ButtonStyle.ACCENT,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    secondary = stringResource(R.string.trips_empty_secondary),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(rows, key = { it.trip.id }) { row ->
                    TripTagCard(row, onClick = { onOpenTrip(row.trip.id) })
                }
            }
        }
    }
}

/**
 * Kartica putovanja je privezak za prtljag: perforirani patrljak sa kodom odredišta i
 * tipom smeštaja, pa naziv, pa traka napretka.
 */
@Composable
private fun TripTagCard(row: TripRow, onClick: () -> Unit) {
    val c = StowTheme.state
    val trip = row.trip
    val current = !trip.isArchived && trip.completedAt == null

    StowCard(
        onClick = onClick,
        borderColor = if (current) c.accent else null,
        modifier = if (trip.isArchived) Modifier.alpha(0.62f) else Modifier,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(66.dp)
                    .fillMaxHeight()
                    .background(if (current) c.accentSoft else c.surface2)
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = destinationCode(trip),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) c.accent else c.ink2,
                )
                MicroLabel(accommodationCode(trip.accommodation), color = c.muted)
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(14.dp),
            ) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MicroLabel(dateRange(trip), Modifier.padding(top = 2.dp))

                val progress = row.progress
                if (trip.completedAt != null) {
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val notReturned = (progress?.packed ?: 0) - (progress?.returned ?: 0)
                        if (notReturned > 0) {
                            Pill(
                                stringResource(R.string.trips_left_behind_count, notReturned),
                                tone = PillTone.ALERT,
                            )
                        }
                        Pill(stringResource(R.string.trips_archived))
                    }
                } else if (progress != null && progress.total > 0) {
                    Box(Modifier.padding(top = 9.dp)) {
                        ProgressStrip(
                            done = progress.packed,
                            trackable = progress.trackable,
                            blocked = progress.toBuy,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MicroLabel(
                            stringResource(R.string.trips_progress, progress.packed, progress.trackable),
                            color = c.ink2,
                        )
                        if (progress.toBuy > 0) {
                            MicroLabel(
                                stringResource(R.string.trips_to_buy_count, progress.toBuy),
                                color = c.alert,
                            )
                        }
                    }
                } else {
                    MicroLabel(
                        stringResource(R.string.trips_created_recently),
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Tri slova odredišta, kao kod aerodroma. Bez odredišta — prva tri slova naziva. */
private fun destinationCode(trip: TripEntity): String {
    val source = trip.destination?.takeIf { it.isNotBlank() } ?: trip.name
    return source.filter { it.isLetter() }.take(3).uppercase(Locale.ROOT).ifEmpty { "STW" }
}

private fun accommodationCode(accommodation: Accommodation): String = when (accommodation) {
    Accommodation.HOTEL -> "HTL"
    Accommodation.APARTMENT -> "APT"
    Accommodation.CAMPING -> "CMP"
    Accommodation.FAMILY -> "FAM"
}

private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

@Composable
private fun dateRange(trip: TripEntity): String {
    val start = trip.startDate?.let { LocalDate.ofEpochDay(it) }
    val end = trip.endDate?.let { LocalDate.ofEpochDay(it) }
    return when {
        start != null && end != null -> {
            val nights = (end.toEpochDay() - start.toEpochDay()).toInt()
            "${start.format(dayMonth)} – ${end.format(dayMonth)} · " +
                pluralStringResource(R.plurals.nights_count, nights, nights)
        }
        start != null -> start.format(dayMonth)
        else -> stringResource(R.string.wizard_no_dates)
    }
}
