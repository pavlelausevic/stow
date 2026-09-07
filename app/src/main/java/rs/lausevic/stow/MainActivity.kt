// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.lausevic.stow.data.Settings
import rs.lausevic.stow.ui.StowNavHost
import rs.lausevic.stow.ui.theme.StowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = container

        // Seed ide pre prvog frejma sadržaja, ali ne blokira crtanje: prazan katalog
        // se popuni za desetinku sekunde, a ekran do tada prikaže svoje prazno stanje.
        lifecycleScope.launch { container.seed.applyIfNeeded() }

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
}
