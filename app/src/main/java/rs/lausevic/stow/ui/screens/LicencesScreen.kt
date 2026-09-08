// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.R
import rs.lausevic.stow.ui.components.ItemGroup
import rs.lausevic.stow.ui.components.ItemTitle
import rs.lausevic.stow.ui.components.MicroLabel
import rs.lausevic.stow.ui.components.RowDivider
import rs.lausevic.stow.ui.components.SectionHeader
import rs.lausevic.stow.ui.components.StowScreen
import rs.lausevic.stow.ui.components.StowTopBar
import rs.lausevic.stow.ui.theme.StowTheme

/**
 * Treće lice u ovoj aplikaciji.
 *
 * Spisak je kratak jer je i lista zavisnosti kratka, i pisan je ručno umesto generisan:
 * generator bi ovde ispisao trideset tranzitivnih AndroidX artefakata, a pitanje na koje
 * ovaj ekran odgovara je „šta je tuđe i pod kojom licencom", ne „šta je sve u classpath-u".
 */
private data class Licence(val name: String, val licence: String, val holder: String)

private val libraries = listOf(
    Licence("Android Jetpack (Compose, Room, DataStore, Navigation)", "Apache-2.0", "The Android Open Source Project"),
    Licence("Kotlin, kotlinx.serialization, kotlinx.coroutines", "Apache-2.0", "JetBrains s.r.o. and contributors"),
)

private val fonts = listOf(
    Licence("Rubik", "SIL Open Font License 1.1", "The Rubik Project Authors"),
    Licence("Nunito", "SIL Open Font License 1.1", "The Nunito Project Authors"),
)

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    StowScreen(
        topBar = {
            StowTopBar(
                title = stringResource(R.string.settings_licences),
                subtitle = stringResource(R.string.licences_subtitle),
                onBack = onBack,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = stringResource(R.string.licences_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StowTheme.state.ink2,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.licences_app)) }
            item {
                ItemGroup {
                    LicenceRow(Licence("Stow", "Apache-2.0", "Pavle Laušević"))
                }
            }

            item { SectionHeader(stringResource(R.string.licences_libraries), counter = libraries.size.toString()) }
            item {
                ItemGroup {
                    libraries.forEachIndexed { index, entry ->
                        LicenceRow(entry)
                        if (index != libraries.lastIndex) RowDivider()
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.licences_fonts), counter = fonts.size.toString()) }
            item {
                ItemGroup {
                    fonts.forEachIndexed { index, entry ->
                        LicenceRow(entry)
                        if (index != fonts.lastIndex) RowDivider()
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        text = stringResource(R.string.licences_fonts_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = StowTheme.state.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenceRow(entry: Licence) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ItemTitle(entry.name, done = false)
        MicroLabel("${entry.licence} · ${entry.holder}")
    }
}
