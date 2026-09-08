// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.Settings
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.GroupBy
import rs.lausevic.stow.data.model.ItemFilter
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode
import rs.lausevic.stow.domain.ReturnList
import rs.lausevic.stow.domain.TextMatching
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.EmptyState
import rs.lausevic.stow.ui.components.FilterRow
import rs.lausevic.stow.ui.components.IconAction
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.components.TravellerChip
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

@Composable
fun TripDetailScreen(
    container: AppContainer,
    tripId: Long,
    settings: Settings,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trip by container.trips.observeById(tripId).collectAsState(initial = null)
    val sections by container.trips.observeSections(tripId).collectAsState(initial = emptyList())
    val items by container.trips.observeItems(tripId).collectAsState(initial = emptyList())
    val travellers by container.catalog.observeTravellers().collectAsState(initial = emptyList())
    val progress by container.trips.observeProgress(tripId).collectAsState(initial = null)

    var filter by remember { mutableStateOf(ItemFilter.ALL) }
    var explaining by remember { mutableStateOf<TripItemEntity?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var assigning by remember { mutableStateOf<TripItemEntity?>(null) }
    var reordering by remember { mutableStateOf(false) }
    // Pogled se seje iz podešavanja, pa se dalje menja za ovo putovanje — podešavanje je
    // podrazumevana vrednost, ne zaključana.
    var grouping by remember(settings.defaultGrouping) { mutableStateOf(settings.defaultGrouping) }
    var message by remember { mutableStateOf<String?>(null) }

    val current = trip ?: return
    val returning = current.mode == TripMode.RETURNING
    // Pravilo količine se razrešava prema dužini putovanja — isto kao pri pravljenju.
    val nights = current.startDate?.let { start ->
        current.endDate?.let { end -> (end - start).toInt().takeIf { n -> n > 0 } }
    }

    val visible = remember(items, sections, filter, returning, query) {
        val bySection = sections.associateBy { it.id }
        // Pretraga ide preko normalizovanog naziva, pa „punjac" nađe „punjač" — ista
        // normalizacija koju koristi i poklapanje sličnih u katalogu.
        val needle = TextMatching.normalize(query)
        fun matches(item: TripItemEntity) =
            needle.isEmpty() || TextMatching.normalize(item.title).contains(needle)

        if (returning) {
            val built = ReturnList.build(items) {
                bySection[it.tripSectionId]?.phase ?: SectionPhase.PACKING
            }
            ReturnList.Result(built.included.filter(::matches), built.notTaken.filter(::matches))
        } else {
            items.filter { item ->
                matches(item) && when (filter) {
                    ItemFilter.ALL -> true
                    ItemFilter.TO_BUY -> item.packStatus == PackStatus.TO_BUY
                    ItemFilter.TO_PACK -> item.packStatus == PackStatus.TO_PACK
                    ItemFilter.PACKED -> item.packStatus == PackStatus.PACKED
                }
            }.let { ReturnList.Result(it, emptyList()) }
        }
    }

    StowScreen(
        topBar = {
            if (returning) {
                ReturnModeBar(
                    title = current.name,
                    subtitle = stringResource(
                        R.string.trip_returned_progress,
                        progress?.returned ?: 0,
                        visible.included.size,
                    ),
                    onBack = onBack,
                    onExport = { exporting = true },
                    onToggleMode = {
                        scope.launch { container.trips.setMode(tripId, TripMode.PACKING) }
                    },
                )
            } else {
                StowTopBar(
                    title = current.name,
                    subtitle = stringResource(R.string.trip_mode_packing) + " · " +
                        stringResource(
                            R.string.trips_progress,
                            progress?.packed ?: 0,
                            progress?.trackable ?: 0,
                        ),
                    onBack = onBack,
                    actions = {
                        IconAction(
                            icon = StowIcons.Share,
                            description = stringResource(R.string.trip_export_pdf),
                            onClick = { exporting = true },
                        )
                        IconAction(
                            icon = StowIcons.SwapMode,
                            description = stringResource(R.string.trip_mode_toggle),
                            onClick = {
                                // Preuređivanje ne sme da preživi promenu režima —
                                // povratna lista ga nema, pa bi se vratilo neočekivano.
                                reordering = false
                                scope.launch { container.trips.setMode(tripId, TripMode.RETURNING) }
                            },
                        )
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!returning) {
                if (reordering) {
                    // Traka režima stoji tu gde su inače filteri, a ne u zaglavlju:
                    // zaglavlje sa četiri ikone pojede naziv putovanja.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MicroLabel(
                            stringResource(R.string.trip_reorder_hint),
                            Modifier.weight(1f),
                        )
                        StowButton(
                            text = stringResource(R.string.trip_reorder_done),
                            onClick = { reordering = false },
                            style = ButtonStyle.INK,
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            FilterRow(
                                options = ItemFilter.entries,
                                selected = filter,
                                label = { filterLabel(it) },
                                onSelect = { filter = it },
                            )
                        }
                        IconAction(
                            icon = StowIcons.Search,
                            description = stringResource(R.string.action_search),
                            onClick = {
                                searching = !searching
                                // Zatvaranje polja briše upit: skriveni filter koji i
                                // dalje krati listu je najgori mogući ishod.
                                if (!searching) query = ""
                            },
                            bordered = false,
                        )
                        IconAction(
                            icon = StowIcons.More,
                            description = stringResource(R.string.trip_view_options),
                            onClick = { viewOptions = true },
                            bordered = false,
                        )
                    }
                    if (searching) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TripSearchField(query) { query = it }
                        }
                    }
                }
            }

            if (visible.included.isEmpty() && visible.notTaken.isEmpty()) {
                // Prazna pretraga nije prazno putovanje: „nema ništa na listi" bi bilo
                // netačno u trenutku kad lista ima trideset stavki a upit nijednu.
                val filtered = query.isNotBlank() || filter != ItemFilter.ALL
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(
                            if (returning && !filtered) R.string.return_empty_title
                            else if (filtered) R.string.trip_filter_empty_title
                            else R.string.trip_empty_title,
                        ),
                        body = stringResource(
                            if (returning && !filtered) R.string.return_empty_body
                            else if (query.isNotBlank()) R.string.trip_search_empty_body
                            else if (filtered) R.string.trip_filter_empty_body
                            else R.string.trip_empty_body,
                        ),
                        mark = if (returning) "↩" else "▤",
                    )
                }
            } else {
                TripItemList(
                    sections = sections,
                    included = visible.included,
                    notTaken = visible.notTaken,
                    returning = returning,
                    // Povratna lista je snimak: grupiše se po sekciji i ne preuređuje se.
                    grouping = if (returning) GroupBy.SECTION else grouping,
                    reordering = reordering && !returning,
                    travellers = travellers,
                    onToggle = { item -> scope.launch { container.trips.toggle(item, returning) } },
                    onExplain = { explaining = it },
                    onMarkToBuy = { item ->
                        scope.launch {
                            val next = if (item.packStatus == PackStatus.TO_BUY) {
                                PackStatus.TO_PACK
                            } else {
                                PackStatus.TO_BUY
                            }
                            container.trips.setStatus(item.id, next)
                        }
                    },
                    onAssign = { assigning = it },
                    onReorder = { sectionId, orderedIds ->
                        scope.launch { container.trips.reorderItems(sectionId, orderedIds) }
                    },
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (settings.sortCheckedToBottom && !returning) {
                    StowButton(
                        text = stringResource(R.string.trip_tidy),
                        onClick = { scope.launch { container.trips.tidy(tripId) } },
                        style = ButtonStyle.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!returning && !reordering) {
                    StowButton(
                        text = stringResource(R.string.trip_add_item),
                        onClick = { adding = true },
                        style = ButtonStyle.INK,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (returning) {
                    StowButton(
                        text = stringResource(R.string.trip_finish),
                        onClick = onFinish,
                        style = ButtonStyle.INK,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    explaining?.let { item ->
        WhyIsThisHereSheet(item = item, onDismiss = { explaining = null })
    }


    if (adding) {
        TripAddSheet(
            container = container,
            tripId = tripId,
            sections = sections,
            nights = nights,
            onAdded = { message = it },
            onDismiss = { adding = false },
        )
    }
    if (viewOptions) {
        ViewOptionsSheet(
            grouping = grouping,
            onGrouping = { grouping = it },
            onReorder = {
                viewOptions = false
                reordering = true
            },
            onDismiss = { viewOptions = false },
        )
    }

    assigning?.let { item ->
        AssignTravellerSheet(
            travellers = travellers,
            selectedId = item.assigneeId,
            onPick = { travellerId ->
                scope.launch { container.trips.assignItem(item.id, travellerId) }
                assigning = null
            },
            onDismiss = { assigning = null },
        )
    }

    if (exporting) {
        ExportSheet(
            container = container,
            trip = current,
            travellers = travellers,
            returning = returning,
            onDismiss = { exporting = false },
            onMessage = { message = it },
        )
    }

    message?.let { text ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    // Koren nema insete, pa poruka mora sama da preskoci sistemsku
                    // navigaciju — inace joj tap ode sistemskom tasteru.
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
                    .clip(StowShapes.card)
                    .background(StowTheme.state.ink)
                    .clickable { message = null }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = StowTheme.state.onInk)
            }
        }
    }
}
