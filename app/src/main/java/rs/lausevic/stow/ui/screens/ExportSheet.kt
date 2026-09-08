// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.db.TravellerEntity
import rs.lausevic.stow.data.db.TripEntity
import rs.lausevic.stow.data.repo.PdfExporter
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Opcije izvoza.
 *
 * Rečenica "kvačice se štikliraju u bilo kom čitaču" stoji tačno tamo gde se odluka
 * donosi. To je jedina reklama koja aplikaciji treba, i tačna je.
 */
@Composable
fun ExportSheet(
    container: AppContainer,
    trip: TripEntity,
    travellers: List<TravellerEntity>,
    returning: Boolean,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = StowTheme.state

    val whoOptions = remember(travellers) { listOf<TravellerEntity?>(null) + travellers }
    var who by remember { mutableStateOf<TravellerEntity?>(null) }
    var mode by remember { mutableStateOf(returning) }
    var grouping by remember { mutableStateOf(PdfExporter.Grouping.SECTION) }
    var includeToBuy by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }

    // Razreseno pri kompoziciji — vidi istu napomenu u SettingsScreen.
    val doneText = stringResource(R.string.export_done)
    val failedText = stringResource(R.string.export_failed, "")

    fun export(share: Boolean) {
        if (working) return
        working = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val exporter = PdfExporter(context)
                    val options = PdfExporter.Options(
                        travellerId = who?.id,
                        travellerName = who?.name,
                        returning = mode,
                        includeToBuy = includeToBuy,
                        grouping = grouping,
                    )
                    val document = exporter.build(
                        trip = trip,
                        sections = container.trips.sections(trip.id),
                        items = container.trips.items(trip.id),
                        options = options,
                    )
                    exporter.write(document, exporter.fileName(trip, options))
                }
            }.onSuccess { file ->
                if (share) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            null,
                        ),
                    )
                }
                onMessage(doneText)
                onDismiss()
            }.onFailure {
                onMessage(failedText + " " + it.message.orEmpty())
                working = false
            }
        }
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
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)

            if (travellers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MicroLabel(stringResource(R.string.export_who))
                    SegmentedControl(
                        options = whoOptions,
                        selected = who,
                        label = { traveller ->
                            traveller?.let { stringResource(R.string.export_who_one, it.name) }
                                ?: stringResource(R.string.traveller_everyone)
                        },
                        onSelect = { who = it },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MicroLabel(stringResource(R.string.export_which_list))
                SegmentedControl(
                    options = listOf(false, true),
                    selected = mode,
                    label = {
                        stringResource(if (it) R.string.trip_mode_returning else R.string.trip_mode_packing)
                    },
                    onSelect = { mode = it },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MicroLabel(stringResource(R.string.export_grouping))
                SegmentedControl(
                    options = PdfExporter.Grouping.entries,
                    selected = grouping,
                    label = {
                        stringResource(
                            if (it == PdfExporter.Grouping.SECTION) {
                                R.string.group_by_section
                            } else {
                                R.string.group_by_bag
                            },
                        )
                    },
                    onSelect = { grouping = it },
                )
            }

            ItemGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { includeToBuy = !includeToBuy }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StateBox(
                        status = if (includeToBuy) PackStatus.PACKED else PackStatus.TO_PACK,
                        kind = ItemKind.ITEM,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.export_include_to_buy),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        MicroLabel(
                            stringResource(R.string.export_include_to_buy_note),
                            Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.export_interactive_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StowButton(
                    text = stringResource(R.string.export_save),
                    onClick = { export(share = false) },
                    style = ButtonStyle.GHOST,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                )
                StowButton(
                    text = stringResource(R.string.export_send),
                    onClick = { export(share = true) },
                    style = ButtonStyle.ACCENT,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
