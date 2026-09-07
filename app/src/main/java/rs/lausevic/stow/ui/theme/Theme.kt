// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Composable
fun StowTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    val assets = LocalContext.current.assets
    val display = remember(assets) { rubikFamily(assets) }
    val body = remember(assets) { nunitoFamily(assets) }
    val typography = remember(display, body) { stowTypography(display, body) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalStateColors provides if (dark) DarkStateColors else LightStateColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) StowDarkColors else StowLightColors,
            typography = typography,
            shapes = StowMaterialShapes,
            content = content,
        )
    }
}

/** Kratica: `StowTheme.state.alert` čita se bolje od `LocalStateColors.current.alert`. */
object StowTheme {
    val state: StateColors
        @Composable get() = LocalStateColors.current
}
