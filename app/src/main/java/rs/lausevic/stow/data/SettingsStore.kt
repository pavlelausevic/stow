// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import rs.lausevic.stow.data.model.GroupBy
import rs.lausevic.stow.ui.theme.ThemeChoice

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("stow_settings")

/** Sistem, srpski (latinica) ili engleski. */
enum class LanguageChoice(val tag: String?) { SYSTEM(null), SERBIAN("sr-Latn"), ENGLISH("en") }

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val language: LanguageChoice = LanguageChoice.SYSTEM,
    val defaultGrouping: GroupBy = GroupBy.SECTION,
    /**
     * Namerno `false`. Štikliranje ne sme da pomera red pod prstom — sortiranje na dno
     * se primenjuje samo na eksplicitnu radnju "Sredi". Ovo podešavanje bira da li ta
     * radnja uopšte postoji, ne da li se dešava u hodu.
     */
    val sortCheckedToBottom: Boolean = false,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = prefs[KeyTheme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.SYSTEM,
            language = prefs[KeyLanguage]?.let { runCatching { LanguageChoice.valueOf(it) }.getOrNull() }
                ?: LanguageChoice.SYSTEM,
            defaultGrouping = prefs[KeyGrouping]?.let { runCatching { GroupBy.valueOf(it) }.getOrNull() }
                ?: GroupBy.SECTION,
            sortCheckedToBottom = prefs[KeySortChecked] ?: false,
        )
    }

    /** Verzija seed-a koja je već primenjena. Kasnija izdanja ga proširuju bez diranja podataka. */
    val seedVersion: Flow<Int> = context.dataStore.data.map { it[KeySeedVersion] ?: 0 }

    suspend fun currentSeedVersion(): Int = seedVersion.first()

    suspend fun setSeedVersion(version: Int) {
        context.dataStore.edit { it[KeySeedVersion] = version }
    }

    suspend fun setLanguage(choice: LanguageChoice) {
        context.dataStore.edit { it[KeyLanguage] = choice.name }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[KeyTheme] = choice.name }
    }

    suspend fun setGrouping(groupBy: GroupBy) {
        context.dataStore.edit { it[KeyGrouping] = groupBy.name }
    }

    suspend fun setSortCheckedToBottom(enabled: Boolean) {
        context.dataStore.edit { it[KeySortChecked] = enabled }
    }

    private companion object {
        val KeyTheme = stringPreferencesKey("theme")
        val KeyLanguage = stringPreferencesKey("language")
        val KeyGrouping = stringPreferencesKey("default_grouping")
        val KeySortChecked = booleanPreferencesKey("sort_checked_to_bottom")
        val KeySeedVersion = intPreferencesKey("seed_version")
    }
}
