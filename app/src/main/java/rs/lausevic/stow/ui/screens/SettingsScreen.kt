// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.BuildConfig
import rs.lausevic.stow.R
import rs.lausevic.stow.data.Settings
import rs.lausevic.stow.data.model.GroupBy
import rs.lausevic.stow.data.repo.TransferRepository
import rs.lausevic.stow.ui.components.BottomDestination
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.Pill
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.SegmentedControl
import rs.lausevic.stow.ui.components.StateBox
import rs.lausevic.stow.ui.components.StowBottomBar
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.ui.theme.StowTheme
import rs.lausevic.stow.ui.theme.ThemeChoice

/** Adresa repoa stoji na jednom mestu — koristi je i README i ovaj ekran. */
const val SOURCE_URL = "https://github.com/pavlelausevic/stow"

@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: Settings,
    destinations: List<BottomDestination>,
    route: String,
    onSelectTab: (BottomDestination) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = container.transfer.export()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.onSuccess { message = context.getString(R.string.export_data_done) }
                .onFailure { message = context.getString(R.string.export_failed, it.message.orEmpty()) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("empty")
                container.transfer.import(text)
            }.onSuccess {
                message = context.getString(R.string.import_done, it.added, it.updated)
            }.onFailure { error ->
                message = when (error) {
                    is TransferRepository.TooNew ->
                        context.getString(R.string.import_bad_version, error.schema)
                    is TransferRepository.BadFormat -> context.getString(R.string.import_bad_format)
                    else -> context.getString(R.string.import_failed, error.message.orEmpty())
                }
            }
        }
    }

    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(
                    R.string.settings_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            )
        },
        bottomBar = { StowBottomBar(destinations, route, onSelectTab) },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_group_display)) }
            item {
                ItemGroup {
                    SettingRow(stringResource(R.string.settings_theme)) {
                        Pill(
                            when (settings.theme) {
                                ThemeChoice.SYSTEM -> stringResource(R.string.settings_theme_system)
                                ThemeChoice.LIGHT -> stringResource(R.string.settings_theme_light)
                                ThemeChoice.DARK -> stringResource(R.string.settings_theme_dark)
                            },
                        )
                    }
                    RowDivider()
                    Column(Modifier.padding(vertical = 10.dp)) {
                        SegmentedControl(
                            options = ThemeChoice.entries,
                            selected = settings.theme,
                            label = {
                                when (it) {
                                    ThemeChoice.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    ThemeChoice.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeChoice.DARK -> stringResource(R.string.settings_theme_dark)
                                }
                            },
                            onSelect = { scope.launch { container.settings.setTheme(it) } },
                        )
                    }
                    RowDivider()
                    Column(Modifier.padding(vertical = 10.dp)) {
                        MicroLabel(stringResource(R.string.settings_default_grouping))
                        Column(Modifier.padding(top = 7.dp)) {
                            SegmentedControl(
                                options = GroupBy.entries,
                                selected = settings.defaultGrouping,
                                label = {
                                    when (it) {
                                        GroupBy.SECTION -> stringResource(R.string.group_by_section)
                                        GroupBy.BAG -> stringResource(R.string.group_by_bag)
                                        GroupBy.TRAVELLER -> stringResource(R.string.group_by_traveller)
                                    }
                                },
                                onSelect = { scope.launch { container.settings.setGrouping(it) } },
                            )
                        }
                    }
                    RowDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_sort_checked),
                        note = stringResource(R.string.settings_sort_checked_note),
                        onClick = {
                            scope.launch {
                                container.settings.setSortCheckedToBottom(!settings.sortCheckedToBottom)
                            }
                        },
                    ) {
                        StateBox(
                            status = if (settings.sortCheckedToBottom) PackStatus.PACKED else PackStatus.TO_PACK,
                            kind = ItemKind.ITEM,
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_group_data)) }
            item {
                ItemGroup {
                    SettingRow(
                        title = stringResource(R.string.settings_export),
                        note = stringResource(R.string.settings_export_note),
                        onClick = { exportLauncher.launch("stow-backup.json") },
                    ) { Icon(StowIcons.Share, null, tint = StowTheme.state.ink2, modifier = Modifier.size(20.dp)) }
                    RowDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_import),
                        note = stringResource(R.string.settings_import_note),
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    ) { Icon(StowIcons.Import, null, tint = StowTheme.state.ink2, modifier = Modifier.size(20.dp)) }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_group_about)) }
            item {
                ItemGroup {
                    SettingRow(
                        title = stringResource(R.string.settings_source),
                        note = stringResource(R.string.settings_source_note),
                        onClick = {
                            // ACTION_VIEW predaje link browseru. Aplikacija nema INTERNET
                            // dozvolu i nikad je ne dobija — link otvara neko drugi.
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                            } catch (e: ActivityNotFoundException) {
                                message = context.getString(R.string.settings_no_browser)
                            }
                        },
                    ) { Icon(StowIcons.Chevron, null, tint = StowTheme.state.ink2, modifier = Modifier.size(20.dp)) }
                    RowDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_licences),
                        note = stringResource(R.string.settings_licences_note),
                    ) { Icon(StowIcons.Chevron, null, tint = StowTheme.state.ink2, modifier = Modifier.size(20.dp)) }
                }
            }

            if (message != null) {
                item {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = message.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StowTheme.state.accent,
                        )
                    }
                }
            }

            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4000)
            message = null
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (note != null) MicroLabel(note, Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}
