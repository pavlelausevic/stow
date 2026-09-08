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
