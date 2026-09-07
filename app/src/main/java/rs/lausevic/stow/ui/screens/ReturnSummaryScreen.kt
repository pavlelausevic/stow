// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.ProgressStrip
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.components.StowCard
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Kraj putovanja — trenutak u kome se "ostavljeno" uopšte može izračunati.
 *
 * Zatvaranje upisuje brojač zaboravljanja u katalog i arhivira putovanje. Bez ove tačke
 * (`Trip.completedAt`) aplikacija nikad ne zna da je povratak gotov, pa se brojač
 * nikad ne uveća, a punjač koji stalno zaboravljaš to nikad ne bi ni rekao.
 */
@Composable
fun ReturnSummaryScreen(
    container: AppContainer,
    tripId: Long,
    onBack: () -> Unit,
    onClosed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trip by container.trips.observeById(tripId).collectAsState(initial = null)
    val progress by container.trips.observeProgress(tripId).collectAsState(initial = null)
    var leftBehind by remember { mutableStateOf<List<TripItemEntity>>(emptyList()) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        leftBehind = container.trips.leftBehindPreview(tripId)
    }

    val c = StowTheme.state
    val returned = progress?.returned ?: 0
    val packed = progress?.packed ?: 0

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.return_title),
                subtitle = trip?.name.orEmpty(),
                onBack = onBack,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StowCard {
                Column(Modifier.padding(16.dp)) {
                    MicroLabel(stringResource(R.string.return_summary_returned))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = returned.toString(),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = "/$packed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.muted,
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                        )
                    }
                    Box(Modifier.padding(top = 10.dp)) {
                        ProgressStrip(
                            done = returned,
                            trackable = packed,
                            blocked = (packed - returned).coerceAtLeast(0),
                        )
                    }
                }
            }

            if (leftBehind.isEmpty()) {
                Box(Modifier.padding(top = 20.dp)) {
                    Text(
                        text = stringResource(R.string.return_left_behind_none),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                SectionHeader(
                    title = stringResource(R.string.return_left_behind),
                    counter = leftBehind.size.toString(),
                    dotted = true,
                )
                ItemGroup {
                    leftBehind.forEachIndexed { index, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            StateBox(PackStatus.TO_BUY, ItemKind.ITEM)
                            Column(Modifier.weight(1f)) {
                                ItemTitle(item.title, done = false)
                            }
                        }
                        if (index != leftBehind.lastIndex) RowDivider()
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(StowShapes.card)
                        .background(c.surface2)
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.return_writeback),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink2,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StowButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBack,
                    style = ButtonStyle.GHOST,
                    modifier = Modifier.weight(1f),
                )
                StowButton(
                    text = stringResource(R.string.return_close_trip),
                    onClick = {
                        if (!closing) {
                            closing = true
                            scope.launch {
                                container.trips.finishTrip(tripId)
                                onClosed()
                            }
                        }
                    },
                    enabled = !closing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
