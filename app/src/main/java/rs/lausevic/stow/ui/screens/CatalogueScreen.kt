// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.model.CatalogSort
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.ui.components.BottomDestination
import rs.lausevic.stow.ui.components.EmptyState
import rs.lausevic.stow.ui.components.IconAction
import rs.lausevic.stow.ui.components.FilterRow
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StowBottomBar
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")

@Composable
fun CatalogueScreen(
    container: AppContainer,
    destinations: List<BottomDestination>,
    route: String,
    onSelectTab: (BottomDestination) -> Unit,
) {
    var sort by remember { mutableStateOf(CatalogSort.MOST_USED) }
    var editing by remember { mutableStateOf<CatalogItemEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val items by container.catalog.observe(sort).collectAsState(initial = emptyList())
    val itemCount by container.catalog.observeItemCount().collectAsState(initial = 0)
    val taskCount by container.catalog.observeTaskCount().collectAsState(initial = 0)

    val grouped = remember(items) { items.groupBy { it.defaultSection.orEmpty() } }

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.catalogue_title),
                subtitle = stringResource(R.string.catalogue_subtitle, itemCount, taskCount),
                actions = {
                    IconAction(
                        icon = StowIcons.Add,
                        description = stringResource(R.string.catalogue_add),
                        onClick = { adding = true },
                    )
                },
            )
        },
        bottomBar = { StowBottomBar(destinations, route, onSelectTab) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilterRow(
                    options = CatalogSort.entries,
                    selected = sort,
                    label = {
                        when (it) {
                            CatalogSort.MOST_USED -> stringResource(R.string.catalogue_sort_most_used)
                            CatalogSort.UNUSED_OVER_YEAR -> stringResource(R.string.catalogue_sort_unused)
                            CatalogSort.ALPHABETICAL -> stringResource(R.string.catalogue_sort_alphabetical)
                        }
                    },
                    onSelect = { sort = it },
                )
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (sort == CatalogSort.UNUSED_OVER_YEAR) {
                        EmptyState(
                            title = stringResource(R.string.catalogue_unused_empty_title),
                            body = stringResource(R.string.catalogue_unused_empty_body),
                            mark = "◷",
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.catalogue_empty_title),
                            body = stringResource(R.string.catalogue_empty_body),
                            mark = "▦",
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    grouped.forEach { (section, sectionItems) ->
                        item(key = "hdr-$section") {
                            SectionHeader(
                                title = section.ifBlank { stringResource(R.string.bag_unassigned) },
                                counter = sectionItems.size.toString(),
                            )
                        }
                        item(key = "grp-$section") {
                            ItemGroup {
                                sectionItems.forEachIndexed { index, entry ->
                                    CatalogueRow(entry, onClick = { editing = entry })
                                    if (index != sectionItems.lastIndex) RowDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        CatalogueEditSheet(container = container, existing = null, onDismiss = { adding = false })
    }
    editing?.let { item ->
        CatalogueEditSheet(container = container, existing = item, onDismiss = { editing = null })
    }
}

@Composable
private fun CatalogueRow(item: CatalogItemEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            ItemTitle(item.name, done = false)
            MicroLabel(usageLine(item), Modifier.padding(top = 2.dp))
            if (item.timesLeftBehind > 0) {
                Box(Modifier.padding(top = 5.dp)) {
                    Pill(
                        stringResource(R.string.catalogue_left_behind, item.timesLeftBehind),
                        tone = PillTone.ALERT,
                    )
                }
            }
            if (item.note != null) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = rs.lausevic.stow.ui.theme.StowTheme.state.ink2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (item.kind == ItemKind.TASK) {
            Pill(stringResource(R.string.catalogue_kind_task))
        }
    }
}

@Composable
private fun usageLine(item: CatalogItemEntity): String {
    val bag = when (item.kind) {
        ItemKind.TASK -> null
        ItemKind.ITEM -> when (item.defaultBag) {
            rs.lausevic.stow.data.model.Bag.CHECKED -> stringResource(R.string.bag_checked)
            rs.lausevic.stow.data.model.Bag.CARRY_ON -> stringResource(R.string.bag_carry_on)
            rs.lausevic.stow.data.model.Bag.PERSONAL -> stringResource(R.string.bag_personal)
            rs.lausevic.stow.data.model.Bag.UNASSIGNED -> null
        }
    }
    val usage = if (item.timesUsed == 0) {
        stringResource(R.string.catalogue_never_used)
    } else {
        stringResource(R.string.catalogue_used_times, item.timesUsed)
    }
    val last = item.lastUsedAt?.let {
        stringResource(
            R.string.catalogue_last_used,
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(monthYear),
        )
    }
    return listOfNotNull(bag, usage, last).joinToString(" · ")
}
