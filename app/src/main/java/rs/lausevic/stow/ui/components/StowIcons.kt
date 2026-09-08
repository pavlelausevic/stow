// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * Sopstveni set ikona, ne Material stock.
 *
 * Dva razloga. Prvi je identitet: stock ikone uz sopstvenu paletu i tipografiju odaju
 * da je dizajn stao na pola. Drugi je zavisnost — `material-icons-extended` je nekoliko
 * hiljada vektora zbog devet koje ovde trebaju.
 *
 * Sve su crtane istim perom: potez 2 na mreži 24, okrugli krajevi i spojevi, bez ispune.
 * Isti jezik kao kutija stanja i ikona aplikacije.
 */
object StowIcons {

    val Back: ImageVector by lazy { stroked("back", "M15,5 L8,12 L15,19") }

    val Check: ImageVector by lazy { stroked("check", "M5,12.5 L9.5,17 L19,7") }

    val Add: ImageVector by lazy { stroked("add", "M12,5 L12,19 M5,12 L19,12") }

    /** Privezak za prtljag — putovanja. */
    val Trips: ImageVector by lazy {
        stroked(
            "trips",
            "M9,4 L17,4 A3,3 0 0,1 20,7 L20,17 A3,3 0 0,1 17,20 L9,20 " +
                "A3,3 0 0,1 6,17 L6,7 A3,3 0 0,1 9,4 Z M10.5,8 L10.5,8.01",
        )
    }

    /** Police sa stvarima — katalog. */
    val Catalogue: ImageVector by lazy {
        stroked(
            "catalogue",
            "M4,6 L20,6 M4,12 L20,12 M4,18 L20,18 M8,4 L8,8 M15,10 L15,14 M10,16 L10,20",
        )
    }

    /** Ceger sa plusom — nabavka. */
    val Shopping: ImageVector by lazy {
        stroked(
            "shopping",
            "M6,8 L18,8 L17,19 A2,2 0 0,1 15,20.5 L9,20.5 A2,2 0 0,1 7,19 Z " +
                "M9,8 A3,3 0 0,1 15,8 M12,12 L12,17 M9.5,14.5 L14.5,14.5",
        )
    }

    /** Klizači — podešavanja. */
    val Settings: ImageVector by lazy {
        stroked(
            "settings",
            "M4,8 L20,8 M4,16 L20,16 M9,5.5 L9,10.5 M15,13.5 L15,18.5",
        )
    }

    /** Strelica u krug — prelazak između pakovanja i povratka. */
    val SwapMode: ImageVector by lazy {
        stroked(
            "swap",
            "M4,9 L16,9 A4,4 0 0,1 16,17 L8,17 M7,6 L4,9 L7,12",
        )
    }

    /** Strelica koja izlazi iz kutije — deljenje i izvoz. */
    val Share: ImageVector by lazy {
        stroked(
            "share",
            "M12,4 L12,15 M8.5,7.5 L12,4 L15.5,7.5 M5,13 L5,19 L19,19 L19,13",
        )
    }

    /** Strelica koja ulazi u kutiju — uvoz. */
    val Import: ImageVector by lazy {
        stroked(
            "import",
            "M12,15 L12,4 M8.5,11.5 L12,15 L15.5,11.5 M5,13 L5,19 L19,19 L19,13",
        )
    }

    /** Strelica nazad u kolonu — vraćeno sa putovanja. */
    val Returned: ImageVector by lazy {
        stroked("returned", "M20,7 L20,12 A4,4 0 0,1 16,16 L5,16 M9,12 L5,16 L9,20")
    }

    val Search: ImageVector by lazy {
        stroked("search", "M11,4 A7,7 0 1,0 11,18 A7,7 0 1,0 11,4 M16,16 L20,20")
    }

    val More: ImageVector by lazy {
        stroked("more", "M12,5 L12,5.01 M12,12 L12,12.01 M12,19 L12,19.01")
    }

    val Chevron: ImageVector by lazy { stroked("chevron", "M9,5 L16,12 L9,19") }

    /** Hvataljka za prevlačenje: dve pune crte, isto pero kao ostatak seta. */
    val Reorder: ImageVector by lazy { stroked("reorder", "M6,9.5 L18,9.5 M6,14.5 L18,14.5") }

    private fun stroked(name: String, pathData: String, strokeWidth: Float = 2f): ImageVector =
        ImageVector.Builder(
            name = "stow_$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
