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
import rs.lausevic.stow.data.model.ItemFilter
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode
import rs.lausevic.stow.domain.ReturnList
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
    var message by remember { mutableStateOf<String?>(null) }

    val current = trip ?: return
    val returning = current.mode == TripMode.RETURNING
    val travellerNames = remember(travellers) { travellers.associate { it.id to it.name } }

    val visible = remember(items, sections, filter, returning) {
        val bySection = sections.associateBy { it.id }
        if (returning) {
            ReturnList.build(items) { bySection[it.tripSectionId]?.phase ?: SectionPhase.PACKING }
        } else {
            items.filter { item ->
                when (filter) {
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
                Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    FilterRow(
                        options = ItemFilter.entries,
                        selected = filter,
                        label = { filterLabel(it) },
                        onSelect = { filter = it },
                    )
                }
            }

            if (visible.included.isEmpty() && visible.notTaken.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(
                            if (returning) R.string.return_empty_title
                            else if (filter == ItemFilter.ALL) R.string.trip_empty_title
                            else R.string.trip_filter_empty_title,
                        ),
                        body = stringResource(
                            if (returning) R.string.return_empty_body
                            else if (filter == ItemFilter.ALL) R.string.trip_empty_body
                            else R.string.trip_filter_empty_body,
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
                    travellerNames = travellerNames,
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

@Composable
private fun TripItemList(
    sections: List<TripSectionEntity>,
    included: List<TripItemEntity>,
    notTaken: List<TripItemEntity>,
    returning: Boolean,
    travellerNames: Map<Long, String>,
    onToggle: (TripItemEntity) -> Unit,
    onExplain: (TripItemEntity) -> Unit,
    onMarkToBuy: (TripItemEntity) -> Unit,
) {
    val bySection = included.groupBy { it.tripSectionId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
    ) {
        sections.forEach { section ->
            val sectionItems = bySection[section.id].orEmpty()
            if (sectionItems.isEmpty()) return@forEach

            item(key = "hdr-${section.id}") {
                val done = sectionItems.count {
                    if (returning) it.isReturned else it.packStatus == PackStatus.PACKED
                }
                SectionHeader(
                    title = section.title,
                    counter = "$done/${sectionItems.size}",
                    dotted = returning,
                )
            }
            item(key = "grp-${section.id}") {
                ItemGroup {
                    sectionItems.forEachIndexed { index, item ->
                        TripItemRow(
                            item = item,
                            returning = returning,
                            travellerName = item.assigneeId?.let { travellerNames[it] },
                            onToggle = { onToggle(item) },
                            onExplain = { onExplain(item) },
                            onMarkToBuy = { onMarkToBuy(item) },
                        )
                        if (index != sectionItems.lastIndex) RowDivider()
                    }
                }
            }
        }

        // Odluka B: nespakovano ne nestaje iz režima povratka, nego pada u prigušenu
        // grupu van imenioca. Ne mozes zaboraviti ono sto nisi ni poneo.
        if (notTaken.isNotEmpty()) {
            item(key = "hdr-not-taken") {
                SectionHeader(
                    title = stringResource(R.string.section_not_taken),
                    counter = notTaken.size.toString(),
                    dotted = true,
                )
            }
            item(key = "grp-not-taken") {
                Box(Modifier.alpha(0.55f)) {
                    ItemGroup {
                        notTaken.forEachIndexed { index, item ->
                            TripItemRow(
                                item = item,
                                returning = false,
                                travellerName = null,
                                onToggle = {},
                                onExplain = {},
                                onMarkToBuy = {},
                            )
                            if (index != notTaken.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripItemRow(
    item: TripItemEntity,
    returning: Boolean,
    travellerName: String?,
    onToggle: () -> Unit,
    onExplain: () -> Unit,
    onMarkToBuy: () -> Unit,
) {
    val c = StowTheme.state
    val done = if (returning) item.isReturned else item.packStatus == PackStatus.PACKED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onMarkToBuy)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StateBox(
            status = item.packStatus,
            kind = item.kind,
            returning = returning,
            isReturned = item.isReturned,
        )
        Column(Modifier.weight(1f)) {
            ItemTitle(item.title, done = done)

            val meta = buildList {
                if (item.kind == ItemKind.ITEM) bagLabel(item)?.let(::add)
                quantityLabel(item)?.let(::add)
            }
            if (meta.isNotEmpty()) {
                MicroLabel(meta.joinToString(" · "), Modifier.padding(top = 2.dp))
            }
            if (item.note != null) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Row(
                Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (item.packStatus == PackStatus.TO_BUY && !returning) {
                    Pill(stringResource(R.string.state_to_buy), tone = PillTone.ALERT)
                }
                if (item.ruleId != null) {
                    Box(Modifier.clickable(onClick = onExplain)) {
                        Pill(stringResource(R.string.why_title), tone = PillTone.ACCENT)
                    }
                }
            }
        }
        if (travellerName != null) TravellerChip(travellerName)
        if (item.quantityCount != null && item.quantityCount > 1) {
            Text(
                text = item.quantityCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = c.ink2,
            )
        }
    }
}

@Composable
private fun filterLabel(filter: ItemFilter): String = when (filter) {
    ItemFilter.ALL -> stringResource(R.string.filter_all)
    ItemFilter.TO_BUY -> stringResource(R.string.filter_to_buy)
    ItemFilter.TO_PACK -> stringResource(R.string.filter_to_pack)
    ItemFilter.PACKED -> stringResource(R.string.filter_packed)
}

@Composable
private fun bagLabel(item: TripItemEntity): String? = when (item.bag) {
    rs.lausevic.stow.data.model.Bag.CHECKED -> stringResource(R.string.bag_checked)
    rs.lausevic.stow.data.model.Bag.CARRY_ON -> stringResource(R.string.bag_carry_on)
    rs.lausevic.stow.data.model.Bag.PERSONAL -> stringResource(R.string.bag_personal)
    rs.lausevic.stow.data.model.Bag.UNASSIGNED -> null
}

/**
 * Ljudski oblik količine se SASTAVLJA pri prikazu, iz zamrznutog pravila.
 *
 * Da je tekst upisan u bazu, "1 po noći, +1" bi ostalo na srpskom i posle prebacivanja
 * aplikacije na engleski — ista greška kao da je i objašnjenje pravila upisano kao rečenica.
 */
@Composable
private fun quantityLabel(item: TripItemEntity): String? {
    if (item.kind == ItemKind.TASK) return null
    return when (item.ruleType) {
        rs.lausevic.stow.data.model.RuleType.FIXED -> null
        rs.lausevic.stow.data.model.RuleType.UNSPECIFIED -> null
        rs.lausevic.stow.data.model.RuleType.PER_NIGHTS -> buildString {
            val per = item.rulePer ?: 1
            append(
                if (per == 1) {
                    stringResource(R.string.quantity_per_nights, 1)
                } else {
                    stringResource(R.string.quantity_per_nights_n, per)
                },
            )
            item.rulePlus?.takeIf { it > 0 }?.let { append(stringResource(R.string.quantity_plus, it)) }
            item.ruleCap?.let { append(stringResource(R.string.quantity_cap, it)) }
        }
    }
}
