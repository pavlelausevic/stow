// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.R
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Zaglavlje ekrana. Naslov je u rečeničnom slogu; podnaslov je mikro-oznaka.
 * Pozadina je boja papira, ne druga boja i ne providna — čim lista klizne ispod,
 * providno zaglavlje se preklopi sa prvom karticom.
 */
@Composable
fun StowTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = if (onBack != null) 6.dp else 18.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconAction(
                icon = StowIcons.Back,
                description = stringResource(R.string.action_back),
                onClick = onBack,
                bordered = false,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                MicroLabel(subtitle, Modifier.padding(top = 3.dp))
            }
        }
        actions?.invoke()
    }
}

/** Okrugla ikona-dugme. 48 dp cilj dodira, i kad je nacrtana manja. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val c = StowTheme.state
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(StowShapes.pill)
                .background(if (bordered) MaterialTheme.colorScheme.surface else Color.Transparent)
                .then(if (bordered) Modifier.border(1.dp, c.line2, StowShapes.pill) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.ink2, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * Donja navigacija. Aktivna stavka nosi MEKU PILULU iza ikone — nikad oštru crtu ispod,
 * koja je highlight koji ne poštuje oblik svog nosioca.
 */
@Composable
fun StowBottomBar(
    destinations: List<BottomDestination>,
    selectedRoute: String,
    onSelect: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StowTheme.state
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.line2),
        )
        Row(
            Modifier
                .fillMaxWidth()
                // Koren nema insete, pa ih donja traka nosi sama.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val on = selectedRoute.startsWith(destination.route)
                Column(
                    modifier = Modifier
                        .clip(StowShapes.panel)
                        .clickable { onSelect(destination) }
                        .semantics { contentDescription = destination.label }
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier
                            .clip(StowShapes.pill)
                            .background(if (on) c.accentSoft else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                            tint = if (on) c.accent else c.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    MicroLabel(destination.label, color = if (on) c.accent else c.muted)
                }
            }
        }
    }
}

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Ekran sa zaglavljem, telom i (opciono) donjom trakom. Jedan Scaffold po ekranu.
 *
 * Aplikacija je edge-to-edge, pa prozor ide ISPOD statusne trake i sistemske navigacije.
 * Insete zato mora da nosi neko — i to tacno jednom. Ovde ih nosi zaglavlje (gore) i,
 * kad donje trake nema, samo telo (dole). Kad donja traka postoji, ona nosi svoj inset
 * sama; racunanje na oba mesta je isti onaj bug od dva ugnezdena Scaffold-a, samo obrnut.
 */
@Composable
fun StowScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { topBar() }
        Box(
            Modifier
                .weight(1f)
                .then(
                    if (bottomBar == null) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
        bottomBar?.invoke()
    }
}
