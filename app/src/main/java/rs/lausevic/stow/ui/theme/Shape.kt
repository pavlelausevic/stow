// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Skala zaobljenja. Nijedan oštar ugao nigde u aplikaciji.
 *
 * Pravilo koje se lako prekrši: HIGHLIGHT PRATI RADIJUS SVOG NOSIOCA. Izabrani segment
 * je pilula unutar pilule, aktivna stavka navigacije nosi pilulu iza ikone (ne crtu
 * ispod nje), a fokus polja je unutrašnji prsten — nikad `outline`, koji ne poštuje oblik.
 */
object StowShapes {
    /** Kutija stanja. 7 na 19 dp je ~37 % — dovoljno zaobljeno, a i dalje očigledno kvadrat. */
    val stateBox = RoundedCornerShape(7.dp)
    val field = RoundedCornerShape(16.dp)
    val group = RoundedCornerShape(18.dp)
    val card = RoundedCornerShape(18.dp)
    val panel = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val fab = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

val StowMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = StowShapes.field,
    large = StowShapes.card,
    extraLarge = StowShapes.panel,
)
