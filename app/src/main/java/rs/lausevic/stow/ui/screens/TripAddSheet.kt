// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.FieldBox
import rs.lausevic.stow.ui.components.FilterRow
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.PillTone
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Dodavanje stavke na već napravljeno putovanje.
 *
 * List ostaje otvoren posle svakog dodavanja. Pakovanje se ne seća u jednoj stavci nego
 * u naletu — setiš se punjača, pa adaptera, pa slušalica — i zatvaranje lista posle
 * svake od njih bi značilo tri otvaranja za jednu misao.
 *
 * „Često nosiš" je isti taj nalet, samo unapred: najkorišćenije iz kataloga što na ovom
 * putovanju još nije. Dodata stavka nestaje iz predloga sama, jer je upit tako pisan.
 */
@Composable
internal fun TripAddSheet(
    container: AppContainer,
    tripId: Long,
    sections: List<TripSectionEntity>,
    nights: Int?,
    onAdded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = StowTheme.state
    val scope = rememberCoroutineScope()
    val suggestions by container.trips.observeSuggestions(tripId)
        .collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ItemKind.ITEM) }
    var section by remember(sections) { mutableStateOf(sections.firstOrNull()) }
    var similar by remember { mutableStateOf<List<CatalogItemEntity>>(emptyList()) }

    // Poklapanje sličnih radi isto kao u katalogu: da se „punjač" ne doda drugi put kao
    // „punjac". Ovde ne brani dodavanje, nego nudi katalošku stavku — s njom dolaze
    // torba, napomena i pravilo količine.
    LaunchedEffect(name) {
        similar = if (name.trim().length >= 3) container.catalog.similarTo(name) else emptyList()
    }

    val addedText = stringResource(R.string.trip_add_done)
    val defaultSection = stringResource(R.string.section_default)

    fun add(catalogItem: CatalogItemEntity?, title: String) {
        scope.launch {
            // Putovanje napravljeno "ni od čega" nema nijednu sekciju. Prva dodata stavka
            // je pravi trenutak da se napravi — dugme koje ne radi ništa je gore.
            val targetId = section?.id
                ?: container.trips.addSection(tripId, defaultSection, SectionPhase.PACKING)
            container.trips.addItem(targetId, catalogItem, title, kind, nights)
            onAdded(addedText)
        }
        name = ""
        similar = emptyList()
    }

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
                stringResource(R.string.trip_add_item),
                style = MaterialTheme.typography.headlineSmall,
            )

            if (suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MicroLabel(stringResource(R.string.trip_add_often))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions.size) { index ->
                            val candidate = suggestions[index]
                            Box(Modifier.clickable { add(candidate, candidate.name) }) {
                                Pill(candidate.name, tone = PillTone.ACCENT)
                            }
                        }
                    }
                }
            }

            FieldBox(
                label = stringResource(R.string.catalogue_field_name),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (similar.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MicroLabel(stringResource(R.string.fuzzy_title), color = c.alert)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(similar.size) { index ->
                            val candidate = similar[index]
                            Box(Modifier.clickable { add(candidate, candidate.name) }) {
                                Pill(candidate.name, tone = PillTone.ALERT)
                            }
                        }
                    }
                }
            }

            if (sections.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MicroLabel(stringResource(R.string.trip_add_section))
                    FilterRow(
                        options = sections,
                        selected = section ?: sections.first(),
                        label = { it.title },
                        onSelect = { section = it },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MicroLabel(stringResource(R.string.trip_add_kind))
                SegmentedControl(
                    options = ItemKind.entries,
                    selected = kind,
                    label = {
                        stringResource(
                            if (it == ItemKind.ITEM) {
                                R.string.catalogue_kind_item
                            } else {
                                R.string.catalogue_kind_task
                            },
                        )
                    },
                    onSelect = { kind = it },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StowButton(
                    text = stringResource(R.string.action_add),
                    onClick = { add(null, name) },
                    enabled = name.isNotBlank() && section != null,
                    style = ButtonStyle.INK,
                    modifier = Modifier.weight(1f),
                )
                StowButton(
                    text = stringResource(R.string.trip_reorder_done),
                    onClick = onDismiss,
                    style = ButtonStyle.GHOST,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
