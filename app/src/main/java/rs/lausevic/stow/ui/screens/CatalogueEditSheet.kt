// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.PhotoStore
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.QuantityRule
import rs.lausevic.stow.data.model.QuantityRuleColumns
import rs.lausevic.stow.ui.components.ButtonStyle
import rs.lausevic.stow.ui.components.FieldBox
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.StowButton
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Dodavanje i izmena stavke kataloga.
 *
 * Poklapanje sličnih se pokreće **dok se kuca**, ne pri čuvanju: ponuda postojeće stavke
 * ima smisla samo pre nego što je korisnik već doneo odluku. Bez toga katalog za godinu
 * dana ima „Punjač za telefon", „punjac telefona" i „Telefonski punjač", pa `timesUsed`
 * ni na jednoj ne znači ništa.
 */
@Composable
fun CatalogueEditSheet(
    container: AppContainer,
    existing: CatalogItemEntity?,
    onDismiss: () -> Unit,
    onUseExisting: (CatalogItemEntity) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = StowTheme.state
    val photos = remember(context) { PhotoStore(context) }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var section by remember { mutableStateOf(existing?.defaultSection.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: ItemKind.ITEM) }
    var bag by remember { mutableStateOf(existing?.defaultBag ?: Bag.CHECKED) }
    var photoPath by remember { mutableStateOf(existing?.photoPath) }
    var similar by remember { mutableStateOf<List<CatalogItemEntity>>(emptyList()) }

    val rule = remember(existing) {
        existing?.let { QuantityRuleColumns(it.ruleType, it.rulePer, it.rulePlus, it.ruleCap).toRule() }
            ?: QuantityRule.Fixed(1)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) { photos.import(uri) }
            if (stored != null) {
                photos.delete(photoPath)
                photoPath = stored
            }
        }
    }

    // Predlozi se osvezavaju dok se kuca, ali samo za nove stavke — pri izmeni bi
    // stavka predlagala samu sebe.
    LaunchedEffect(name, existing) {
        similar = if (existing == null && name.length >= 3) {
            container.catalog.similarTo(name)
        } else {
            emptyList()
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.catalogue_add else R.string.action_edit,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )

            SheetTextField(stringResource(R.string.catalogue_field_name), name) { name = it }

            if (similar.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(StowShapes.card)
                        .background(c.alertSoft)
                        .padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MicroLabel(stringResource(R.string.fuzzy_title), color = c.alert)
                    Text(
                        text = stringResource(R.string.fuzzy_body, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink2,
                    )
                    ItemGroup {
                        similar.forEachIndexed { index, candidate ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onUseExisting(candidate); onDismiss() }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    ItemTitle(candidate.name, done = false)
                                    MicroLabel(
                                        stringResource(
                                            R.string.catalogue_used_times,
                                            candidate.timesUsed,
                                        ),
                                        Modifier.padding(top = 2.dp),
                                    )
                                }
                                MicroLabel(stringResource(R.string.fuzzy_use_existing), color = c.accent)
                            }
                            if (index != similar.lastIndex) RowDivider()
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MicroLabel(stringResource(R.string.catalogue_field_kind))
                SegmentedControl(
                    options = ItemKind.entries,
                    selected = kind,
                    label = {
                        stringResource(
                            if (it == ItemKind.TASK) R.string.catalogue_kind_task
                            else R.string.catalogue_kind_item,
                        )
                    },
                    onSelect = { kind = it },
                )
            }

            // Zadatak nema torbu ni kolicinu — nije predmet, pa ta polja nestaju umesto
            // da stoje onemogucena.
            if (kind == ItemKind.ITEM) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MicroLabel(stringResource(R.string.catalogue_field_bag))
                    SegmentedControl(
                        options = Bag.entries,
                        selected = bag,
                        label = {
                            stringResource(
                                when (it) {
                                    Bag.CHECKED -> R.string.bag_checked
                                    Bag.CARRY_ON -> R.string.bag_carry_on
                                    Bag.PERSONAL -> R.string.bag_personal
                                    Bag.UNASSIGNED -> R.string.bag_unassigned
                                },
                            )
                        },
                        onSelect = { bag = it },
                    )
                }
            }

            SheetTextField(stringResource(R.string.catalogue_field_section), section) { section = it }
            SheetTextField(stringResource(R.string.catalogue_field_note), note) { note = it }

            if (kind == ItemKind.ITEM) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(StowShapes.field)
                            .background(c.surface2)
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = rememberPhoto(photos, photoPath)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("+", style = MaterialTheme.typography.headlineSmall, color = c.muted)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        MicroLabel(stringResource(R.string.catalogue_field_photo))
                        Text(
                            text = stringResource(
                                if (photoPath == null) R.string.catalogue_photo_add
                                else R.string.catalogue_photo_replace,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.ink2,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (photoPath != null) {
                        MicroLabel(
                            stringResource(R.string.catalogue_photo_remove),
                            Modifier.clickable {
                                photos.delete(photoPath)
                                photoPath = null
                            },
                            color = c.alert,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StowButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    style = ButtonStyle.GHOST,
                    modifier = Modifier.weight(1f),
                )
                StowButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        if (name.isBlank()) return@StowButton
                        scope.launch {
                            if (existing == null) {
                                container.catalog.add(
                                    name = name,
                                    kind = kind,
                                    bag = bag,
                                    rule = rule,
                                    section = section.takeIf { it.isNotBlank() },
                                    note = note.takeIf { it.isNotBlank() },
                                    photoPath = photoPath,
                                )
                            } else {
                                container.catalog.update(
                                    item = existing,
                                    name = name,
                                    kind = kind,
                                    bag = bag,
                                    section = section.takeIf { it.isNotBlank() },
                                    note = note.takeIf { it.isNotBlank() },
                                    photoPath = photoPath,
                                )
                            }
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Ucitava sliku sa diska bez ijedne biblioteke.
 *
 * Coil bi ovde bio potpuno opravdan da se ucitavaju daljinske slike razlicitih velicina.
 * Ali PhotoStore vec smanjuje svaku sliku na 1024 px i cuva je lokalno, pa je posao
 * "procitaj jedan mali JPEG" — nekoliko redova naspram cele biblioteke.
 */
@Composable
private fun rememberPhoto(
    photos: PhotoStore,
    path: String?,
): androidx.compose.ui.graphics.ImageBitmap? {
    var bitmap by remember(path) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(path) {
        bitmap = path?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(photos.file(it).absolutePath)
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

@Composable
private fun SheetTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    val c = StowTheme.state
    FieldBox(label = label, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            ),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
