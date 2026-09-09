// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.GroupBy
import rs.lausevic.stow.data.model.ItemFilter
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.ReorderableColumn
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.components.TravellerChip
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Jedna grupa u listi putovanja.
 *
 * [sectionId] postoji samo kad je grupa zaista sekcija. Redni broj stavke je po sekciji,
 * pa preuređivanje ima gde da se upiše jedino tada; grupisanje po torbi ili putniku je
 * drugi pogled na iste stavke, a ne novi poredak, i tamo hvataljke nema.
 */
private data class Grouped(
    val key: String,
    val title: String,
    val items: List<TripItemEntity>,
    val sectionId: Long?,
)

@Composable
internal fun TripItemList(
    sections: List<TripSectionEntity>,
    included: List<TripItemEntity>,
    notTaken: List<TripItemEntity>,
    returning: Boolean,
    grouping: GroupBy,
    reordering: Boolean,
    travellers: List<TravellerEntity>,
    onToggle: (TripItemEntity) -> Unit,
    onExplain: (TripItemEntity) -> Unit,
    onMarkToBuy: (TripItemEntity) -> Unit,
    onAssign: (TripItemEntity) -> Unit,
    onReorder: (Long, List<Long>) -> Unit,
) {
    val travellerNames = remember(travellers) { travellers.associate { it.id to it.name } }
    val groups = groupsOf(sections, included, grouping, travellers)
    // Dodela ima smisla tek kad ima kome da se dodeli, i nikad u režimu povratka —
    // povratna lista je snimak onoga što je poneto, ne mesto za preraspodelu.
    val canAssign = travellers.size > 1 && !returning && !reordering

    // Promena grupisanja menja i naslove i redosled, pa je zatečena pozicija skrola
    // besmislena: ostaneš nasred nečega što više nije isto. Nazad na vrh.
    val listState = rememberLazyListState()
    LaunchedEffect(grouping) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        groups.forEach { group ->
            item(key = "hdr-${group.key}") {
                val done = group.items.count {
                    if (returning) it.isReturned else it.packStatus == PackStatus.PACKED
                }
                SectionHeader(
                    title = group.title,
                    counter = "$done/${group.items.size}",
                    dotted = returning,
                )
            }
            item(key = "grp-${group.key}") {
                ItemGroup {
                    val sectionId = group.sectionId
                    if (reordering && sectionId != null) {
                        ReorderableColumn(
                            items = group.items,
                            keyOf = { it.id },
                            onCommit = { ordered -> onReorder(sectionId, ordered.map { it.id }) },
                        ) { item, index, handle ->
                            TripItemRow(
                                item = item,
                                returning = returning,
                                travellerName = item.assigneeId?.let { travellerNames[it] },
                                canAssign = false,
                                dragHandle = handle,
                                onToggle = {},
                                onExplain = {},
                                onMarkToBuy = {},
                                onAssign = {},
                            )
                            if (index != group.items.lastIndex) RowDivider()
                        }
                    } else {
                        group.items.forEachIndexed { index, item ->
                            TripItemRow(
                                item = item,
                                returning = returning,
                                travellerName = item.assigneeId?.let { travellerNames[it] },
                                canAssign = canAssign,
                                dragHandle = null,
                                onToggle = { onToggle(item) },
                                onExplain = { onExplain(item) },
                                onMarkToBuy = { onMarkToBuy(item) },
                                onAssign = { onAssign(item) },
                            )
                            if (index != group.items.lastIndex) RowDivider()
                        }
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
                                canAssign = false,
                                dragHandle = null,
                                onToggle = {},
                                onExplain = {},
                                onMarkToBuy = {},
                                onAssign = {},
                            )
                            if (index != notTaken.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Naslovi grupa se razrešavaju **pri kompoziciji**, pa tek onda ulaze u `remember`.
 * `stringResource` pozvan kasnije, iz lambde, ne bi video promenu jezika — ista zamka
 * zbog koje lint obara build na `context.getString` u povratnom pozivu.
 */
@Composable
private fun groupsOf(
    sections: List<TripSectionEntity>,
    items: List<TripItemEntity>,
    grouping: GroupBy,
    travellers: List<TravellerEntity>,
): List<Grouped> {
    val shared = stringResource(R.string.traveller_shared)
    val bagTitles = listOf(
        Bag.CHECKED to stringResource(R.string.bag_checked),
        Bag.CARRY_ON to stringResource(R.string.bag_carry_on),
        Bag.PERSONAL to stringResource(R.string.bag_personal),
        Bag.UNASSIGNED to stringResource(R.string.bag_unassigned),
    )
    return remember(sections, items, grouping, travellers, shared, bagTitles) {
        when (grouping) {
            GroupBy.SECTION -> {
                val bySection = items.groupBy { it.tripSectionId }
                sections.mapNotNull { section ->
                    val rows = bySection[section.id].orEmpty()
                    if (rows.isEmpty()) {
                        null
                    } else {
                        Grouped("sec-${section.id}", section.title, rows, section.id)
                    }
                }
            }

            GroupBy.BAG -> {
                val byBag = items.groupBy { it.bag }
                bagTitles.mapNotNull { (bag, title) ->
                    val rows = byBag[bag].orEmpty()
                    if (rows.isEmpty()) null else Grouped("bag-$bag", title, rows, null)
                }
            }

            GroupBy.TRAVELLER -> {
                val byWho = items.groupBy { it.assigneeId }
                buildList {
                    travellers.forEach { traveller ->
                        val rows = byWho[traveller.id].orEmpty()
                        if (rows.isNotEmpty()) {
                            add(Grouped("who-${traveller.id}", traveller.name, rows, null))
                        }
                    }
                    // Zajedničko ide na dno: ono je ostatak, a ne prvi putnik.
                    val rows = byWho[null].orEmpty()
                    if (rows.isNotEmpty()) add(Grouped("who-shared", shared, rows, null))
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
    canAssign: Boolean,
    dragHandle: Modifier?,
    onToggle: () -> Unit,
    onExplain: () -> Unit,
    onMarkToBuy: () -> Unit,
    onAssign: () -> Unit,
) {
    val c = StowTheme.state
    val done = if (returning) item.isReturned else item.packStatus == PackStatus.PACKED
    val reorderLabel = stringResource(R.string.trip_reorder)
    val assignLabel = stringResource(R.string.trip_assign)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // U režimu preuređivanja red ne reaguje na dodir: prevlači se hvataljka, a
            // štikliranje usput bi bilo tačno ono pomeranje pod prstom koje ne želimo.
            .then(
                if (dragHandle == null) {
                    Modifier.combinedClickable(onClick = onToggle, onLongClick = onMarkToBuy)
                } else {
                    Modifier
                },
            )
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
            // „Zašto je ovo ovde?" je nekad bila pilula ispod svakog generisanog reda i
            // trošila je celu liniju na svakoj stavci. Objašnjenje sada visi o samom
            // opisu — tu i pripada, jer opis i jeste ono što je pravilo upisalo.
            if (meta.isNotEmpty()) {
                MicroLabel(
                    meta.joinToString(" · "),
                    Modifier
                        .then(
                            if (item.ruleId != null) {
                                Modifier
                                    .clip(StowShapes.pill)
                                    .clickable(onClick = onExplain)
                            } else {
                                Modifier
                            },
                        )
                        .padding(top = 2.dp, bottom = 2.dp),
                )
            }
            if (item.note != null) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (item.packStatus == PackStatus.TO_BUY && !returning) {
                Row(Modifier.padding(top = 5.dp)) {
                    Pill(stringResource(R.string.state_to_buy), tone = PillTone.ALERT)
                }
            }
        }
        if (dragHandle == null && (canAssign || travellerName != null)) {
            Box(
                Modifier
                    .clip(StowShapes.pill)
                    .then(
                        if (canAssign) {
                            Modifier
                                .semantics { contentDescription = assignLabel }
                                .clickable(onClick = onAssign)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                TravellerChip(travellerName)
            }
        }
        if (item.quantityCount != null && item.quantityCount > 1) {
            Text(
                text = item.quantityCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = c.ink2,
            )
        }
        if (dragHandle != null) {
            Box(
                modifier = dragHandle.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = StowIcons.Reorder,
                    contentDescription = reorderLabel,
                    tint = c.muted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
internal fun filterLabel(filter: ItemFilter): String = when (filter) {
    ItemFilter.ALL -> stringResource(R.string.filter_all)
    ItemFilter.TO_BUY -> stringResource(R.string.state_to_buy)
    ItemFilter.TO_PACK -> stringResource(R.string.state_to_pack)
    ItemFilter.PACKED -> stringResource(R.string.state_packed)
}

@Composable
private fun bagLabel(item: TripItemEntity): String? = when (item.bag) {
    Bag.CHECKED -> stringResource(R.string.bag_checked)
    Bag.CARRY_ON -> stringResource(R.string.bag_carry_on)
    Bag.PERSONAL -> stringResource(R.string.bag_personal)
    Bag.UNASSIGNED -> null
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
        RuleType.FIXED -> null
        RuleType.UNSPECIFIED -> null
        RuleType.PER_NIGHTS -> buildString {
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
