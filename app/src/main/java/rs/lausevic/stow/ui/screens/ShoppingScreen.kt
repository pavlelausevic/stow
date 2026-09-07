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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.ui.components.BottomDestination
import rs.lausevic.stow.ui.components.EmptyState
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowBottomBar
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Sve što je označeno za nabavku, sa svih aktivnih putovanja.
 *
 * Grupisano po sekciji kataloga, jer to je grupisanje koje odgovara redosledu obilaska
 * radnji — apoteka je jedno mesto, začini drugo. Putovanje je čip, ne naslov: flasteri
 * koji trebaju za dva putovanja kupuju se jednom.
 */
@Composable
fun ShoppingScreen(
    container: AppContainer,
    destinations: List<BottomDestination>,
    route: String,
    onSelectTab: (BottomDestination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rows by container.trips.observeShoppingList().collectAsState(initial = emptyList())
    val grouped = remember(rows) { rows.groupBy { it.sectionTitle } }
    val tripCount = remember(rows) { rows.map { it.tripId }.distinct().size }

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.shopping_title),
                subtitle = stringResource(R.string.shopping_subtitle, rows.size, tripCount),
            )
        },
        bottomBar = { StowBottomBar(destinations, route, onSelectTab) },
    ) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.shopping_empty_title),
                    body = stringResource(R.string.shopping_empty_body),
                    mark = "◇",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                grouped.forEach { (section, sectionRows) ->
                    item(key = "hdr-$section") {
                        SectionHeader(title = section, counter = sectionRows.size.toString())
                    }
                    item(key = "grp-$section") {
                        ItemGroup {
                            sectionRows.forEachIndexed { index, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Tap ovde znaci "kupio sam je", pa se vraca
                                            // u listu za pakovanje — ne u spakovano.
                                            scope.launch {
                                                container.trips.setStatus(row.id, PackStatus.TO_PACK)
                                            }
                                        }
                                        .padding(vertical = 11.dp),
                                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    StateBox(PackStatus.TO_BUY, ItemKind.ITEM)
                                    Column(Modifier.weight(1f)) {
                                        ItemTitle(row.title, done = false)
                                        Row(
                                            Modifier.padding(top = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Pill(row.tripName, tone = PillTone.ACCENT)
                                        }
                                        if (row.note != null) {
                                            Text(
                                                text = row.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StowTheme.state.ink2,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                    if (row.quantityCount != null) {
                                        Text(
                                            text = row.quantityCount.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = StowTheme.state.ink2,
                                        )
                                    }
                                }
                                if (index != sectionRows.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
