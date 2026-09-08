// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.model.GroupBy
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.TravellerChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.ui.components.IconAction
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/** Ploča režima povratka: inverzija vrednosti, pa dijagonalna traka koja se meko završava. */
@Composable
internal fun ReturnModeBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val c = StowTheme.state
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(StowShapes.panel)
                .background(c.ink)
                .padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = StowIcons.Back,
                description = stringResource(R.string.action_back),
                onClick = onBack,
                bordered = false,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.onInk,
                    maxLines = 1,
                )
                MicroLabel(subtitle, Modifier.padding(top = 3.dp), color = c.onInk.copy(alpha = 0.75f))
            }
            IconAction(
                icon = StowIcons.Share,
                description = stringResource(R.string.trip_export_pdf),
                onClick = onExport,
                bordered = false,
            )
            IconAction(
                icon = StowIcons.SwapMode,
                description = stringResource(R.string.trip_mode_toggle),
                onClick = onToggleMode,
                bordered = false,
            )
        }
        HazardStripe()
    }
}

/**
 * Dijagonalna traka ispod ploče režima. Signal koji preživljava sive tonove i daltonizam,
 * a krajevi su joj zaobljeni kao i sve ostalo.
 */
@Composable
private fun HazardStripe() {
    val c = StowTheme.state
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(9.dp)
            .clip(StowShapes.panel)
            .background(c.paper),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(28) {
            Box(
                Modifier
                    .weight(1f)
                    .height(9.dp)
                    .clip(StowShapes.pill)
                    .background(c.ink),
            )
        }
    }
}


@Composable
internal fun WhyIsThisHereSheet(item: TripItemEntity, onDismiss: () -> Unit) {
    val c = StowTheme.state
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x85101211))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .clip(StowShapes.sheet)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(stringResource(R.string.why_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.why_body, item.title),
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(StowShapes.field)
                    .background(c.accentSoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                MicroLabel(stringResource(R.string.why_rule), color = c.accent)
                Text(
                    text = item.ruleId.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink,
                )
            }
            Text(
                text = ruleSentence(item.ruleId),
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )
            StowButton(
                text = stringResource(R.string.action_ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Pogled na listu: grupisanje i ulaz u preuređivanje.
 *
 * Oboje su isti soj radnje — menjaju kako lista izgleda, a ne šta u njoj piše — pa stoje
 * na jednom mestu, do filtera, a ne kao još dve ikone u zaglavlju.
 */
@Composable
internal fun ViewOptionsSheet(
    grouping: GroupBy,
    onGrouping: (GroupBy) -> Unit,
    onReorder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = StowTheme.state
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x85101211))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .clip(StowShapes.sheet)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.trip_view_options),
                style = MaterialTheme.typography.headlineSmall,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MicroLabel(stringResource(R.string.trip_grouping))
                SegmentedControl(
                    options = GroupBy.entries,
                    selected = grouping,
                    label = {
                        when (it) {
                            GroupBy.SECTION -> stringResource(R.string.group_by_section)
                            GroupBy.BAG -> stringResource(R.string.group_by_bag)
                            GroupBy.TRAVELLER -> stringResource(R.string.group_by_traveller)
                        }
                    },
                    onSelect = onGrouping,
                )
            }

            // Redni broj stavke je po sekciji. Prevlačenje u pogledu po torbi ili putniku
            // nema gde da se upiše, pa se ne nudi — umesto da se ponudi pa ne uradi ništa.
            val canReorder = grouping == GroupBy.SECTION
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StowButton(
                    text = stringResource(R.string.trip_reorder),
                    onClick = onReorder,
                    enabled = canReorder,
                    style = ButtonStyle.GHOST,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!canReorder) {
                    MicroLabel(stringResource(R.string.trip_reorder_only_sections), color = c.muted)
                }
            }

            StowButton(
                text = stringResource(R.string.action_ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Dodela putnika jednoj stavci. „Zajedničko" je prva stavka, ne izostavljena — ono je
 * podrazumevano stanje i mora se moći vratiti.
 */
@Composable
internal fun AssignTravellerSheet(
    travellers: List<TravellerEntity>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x85101211))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .clip(StowShapes.sheet)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.trip_assign),
                style = MaterialTheme.typography.headlineSmall,
            )
            ItemGroup {
                AssignRow(
                    name = null,
                    selected = selectedId == null,
                    onClick = { onPick(null) },
                )
                travellers.forEach { traveller ->
                    RowDivider()
                    AssignRow(
                        name = traveller.name,
                        selected = selectedId == traveller.id,
                        onClick = { onPick(traveller.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignRow(name: String?, selected: Boolean, onClick: () -> Unit) {
    val c = StowTheme.state
    val label = name ?: stringResource(R.string.traveller_shared)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TravellerChip(name)
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = StowIcons.Check,
                contentDescription = null,
                tint = c.ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
