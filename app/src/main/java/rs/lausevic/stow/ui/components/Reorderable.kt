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
 * Redovi nisu iste visine — stavka sa opisom i napomenom je viša od gole. Zato se prag
 * ne računa iz jedne konstante nego iz izmerene visine suseda preko koga se prelazi;
 * kad se pređe, isti taj broj se oduzme od pomeraja, pa red ostane pod prstom.
 *
 * Redosled se drži lokalno dok traje prevlačenje i predaje se tek na kraju, jednim
 * pozivom [onCommit]. Upis u bazu po svakoj zameni bi značio N transakcija za jedno
 * prevlačenje i treperenje liste iz `Flow`-a.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onCommit: (List<T>) -> Unit,
    row: @Composable (item: T, index: Int, dragHandle: Modifier) -> Unit,
) {
    val keys = items.map(key)
    var order by remember(keys) { mutableStateOf(items) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dragged by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    Column {
        order.forEachIndexed { index, item ->
            val itemKey = key(item)
            val isDragged = itemKey == dragged
            Column(
                Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else 0f }
                    .onSizeChanged { heights[itemKey] = it.height },
            ) {
                row(
                    item,
                    index,
                    Modifier.pointerInput(itemKey) {
                        detectDragGestures(
                            onDragStart = {
                                dragged = itemKey
                                offset = 0f
                            },
                            onDragEnd = {
                                dragged = null
                                offset = 0f
                                onCommit(order)
                            },
                            onDragCancel = {
                                dragged = null
                                offset = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            offset += amount.y

                            val at = order.indexOfFirst { key(it) == itemKey }
                            if (at < 0) return@detectDragGestures

                            val over = when {
                                offset > 0 && at < order.lastIndex -> at + 1
                                offset < 0 && at > 0 -> at - 1
                                else -> return@detectDragGestures
                            }
                            val span = heights[key(order[over])]?.toFloat() ?: return@detectDragGestures
                            if (kotlin.math.abs(offset) < span) return@detectDragGestures

                            order = order.toMutableList().apply { add(over, removeAt(at)) }
                            offset -= if (offset > 0) span else -span
                        }
                    },
                )
            }
        }
    }
}
