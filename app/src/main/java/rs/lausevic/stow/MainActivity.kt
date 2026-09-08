// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rs.lausevic.stow.data.LanguageChoice
import rs.lausevic.stow.data.Settings
import rs.lausevic.stow.ui.StowNavHost
import rs.lausevic.stow.ui.theme.StowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = container

        // Seed ide pre prvog frejma sadržaja, ali ne blokira crtanje: prazan katalog se
        // popuni za desetinku sekunde, a ekran do tada prikaže svoje prazno stanje.
        //
        // `relocalise` posle njega prevodi nazive koje korisnik nije dirao. Bez toga bi
        // katalog zasejan na engleskom ostao engleski i posle prebacivanja na srpski.
        lifecycleScope.launch {
            container.seed.applyIfNeeded()
            container.seed.relocalise()
        }

        lifecycleScope.launch {
            container.settings.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect(::applyLanguage)
        }

        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            StowTheme(choice = settings.theme) {
                StowNavHost(
                    container = container,
                    settings = settings,
                    startDeepLink = intent?.data?.toString(),
                )
            }
        }
    }

    /**
     * Jezik po aplikaciji, nezavisno od podešavanja telefona.
     *
     * `LocaleManager` postoji od API 33. Ispod toga bi trebao AppCompat samo zbog ovoga,
     * a aplikacija ga inače nema — pa na starijim uređajima jezik prati sistem, što je
     * ionako podrazumevano ponašanje.
     */
    private fun applyLanguage(choice: LanguageChoice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = getSystemService(LocaleManager::class.java) ?: return
        val wanted = choice.tag?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
        // Postavljanje pokreće ponovno kreiranje aktivnosti, pa se ne sme raditi u prazno.
        if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
    }
}
