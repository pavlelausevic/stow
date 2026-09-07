// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable

/**
 * Bez Hilt-a: ViewModel dobija zavisnosti kroz konstruktor, a ovo je jedini komad
 * lepka koji za to treba. Ključ je uključen da dva ekrana istog tipa (dva putovanja)
 * ne dele instancu.
 */
@Composable
inline fun <reified T : ViewModel> stowViewModel(
    key: String? = null,
    crossinline create: () -> T,
): T = viewModel(
    key = key,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    },
)
