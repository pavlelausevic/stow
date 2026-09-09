// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * Kolona koja se preuređuje prevlačenjem — bez biblioteke i bez `LazyColumn`.
 *
 * Prevlači se **samo za hvataljku**, nikad za sam red. Tap i dugi pritisak na redu već
 * nose značenje (štikliranje i „nabaviti"), a i cela aplikacija stoji na tome da se red
 * ne pomera pod prstom slučajno. Hvataljka postoji samo u režimu preuređivanja.
 *
 * **Redosled se ne dira dok prevlačenje traje.** Prva verzija je zamenjivala mesta u
 * listi u hodu i time obarala samu sebe: kad dete promeni mesto u kompoziciji, njegov
 * `pointerInput` dobije drugi ključ, restartuje se i **gest se prekine pre `onDragEnd`**
 * — pomeranje se vidi na ekranu, ali se nikad ne upiše. Zato ovde kompozicija ostaje u
 * zatečenom redosledu, a pomeranje je čisto crtanje: vučeni red ide za prstom, redovi
 * preko kojih je prešao se pomere za njegovu visinu. Nova lista se sklapa tek na kraju,
 * jednim pozivom [onCommit].
 *
 * Redovi nisu iste visine — stavka sa opisom i napomenom je viša od gole — pa se prag
 * ne računa iz konstante nego iz izmerene visine suseda preko koga se prelazi.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onCommit: (List<T>) -> Unit,
    row: @Composable (item: T, index: Int, dragHandle: Modifier) -> Unit,
) {
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    // `pointerInput` se ne restartuje kad se lista promeni, pa bi zatečena kopija
    // ostala zauvek u zatvorenju. Ove dve prate tekuće vrednosti.
    val currentItems by rememberUpdatedState(items)
    val currentCommit by rememberUpdatedState(onCommit)

    val dragFrom = items.indexOfFirst { keyOf(it) == draggedKey }

    Column {
        items.forEachIndexed { index, item ->
            val itemKey = keyOf(item)
            Column(
                Modifier
                    .zIndex(if (index == dragFrom) 1f else 0f)
                    // Pomeraj se računa ovde, a ne u kompoziciji: čitanje `offset` u
                    // sloju crtanja znači da prst pomera sliku bez rekompozicije reda.
                    .graphicsLayer {
                        val from = currentItems.indexOfFirst { keyOf(it) == draggedKey }
                        translationY = if (from < 0) {
                            0f
                        } else {
                            val to = targetIndex(currentItems, keyOf, heights, from, offset)
                            val span = (heights[keyOf(currentItems[from])] ?: 0).toFloat()
                            when {
                                index == from -> offset
                                to > from && index in (from + 1)..to -> -span
                                to < from && index in to..(from - 1) -> span
                                else -> 0f
                            }
                        }
                    }
                    .onSizeChanged { heights[itemKey] = it.height },
            ) {
                row(
                    item,
                    index,
                    Modifier.pointerInput(itemKey) {
                        detectDragGestures(
                            onDragStart = {
                                draggedKey = itemKey
                                offset = 0f
                            },
                            onDragEnd = {
                                val list = currentItems
                                val from = list.indexOfFirst { keyOf(it) == itemKey }
                                val to = targetIndex(list, keyOf, heights, from, offset)
                                draggedKey = null
                                offset = 0f
                                if (from >= 0 && to != from) {
                                    currentCommit(
                                        list.toMutableList().apply { add(to, removeAt(from)) },
                                    )
                                }
                            },
                            onDragCancel = {
                                draggedKey = null
                                offset = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            offset += amount.y
                        }
                    },
                )
            }
        }
    }
}

/**
 * Gde bi red završio da se prst sada podigne.
 *
 * Prelazi se onoliko suseda koliko stane u pomeraj, mereno njihovim stvarnim visinama.
 * Ako suseda još nismo izmerili, tu se staje — bolje ne pomeriti nego pogoditi.
 */
private fun <T> targetIndex(
    items: List<T>,
    keyOf: (T) -> Any,
    heights: Map<Any, Int>,
    from: Int,
    offset: Float,
): Int {
    if (from < 0) return from
    var to = from
    var remaining = offset
    if (offset > 0) {
        while (to < items.lastIndex) {
            val span = heights[keyOf(items[to + 1])] ?: break
            if (remaining < span) break
            remaining -= span
            to++
        }
    } else {
        while (to > 0) {
            val span = heights[keyOf(items[to - 1])] ?: break
            if (-remaining < span) break
            remaining += span
            to--
        }
    }
    return to
}
