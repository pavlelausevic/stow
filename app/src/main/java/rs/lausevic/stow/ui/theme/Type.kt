// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/*
 * Fontovi žive u assets/fonts i ugrađuju se u APK. Aplikacija nema mrežu pa se ne skidaju,
 * a iz assets-a ih čitaju i Compose i PDF izvoz — jedna kopija, dva potrošača.
 *
 * Rubik nosi naslove, količine, datume i verzalne mikro-oznake: njegov projektni zadatak
 * su blago zaobljeni uglovi, pa nijedan potez ne završava oštro. Nunito nosi tekući tekst,
 * sa zaobljenim završecima poteza.
 *
 * Monospace ne postoji u sistemu. Bio je najtehničkiji element, a poravnanje cifara u
 * koloni radi i bez njega — preko "tnum", koje Rubik podržava. Ovo je svesno odstupanje
 * od "monospaced numerals" iz originalnog zahteva.
 */

fun rubikFamily(assets: AssetManager) = FontFamily(
    Font("fonts/rubik_regular.ttf", assets, FontWeight.Normal),
    Font("fonts/rubik_medium.ttf", assets, FontWeight.Medium),
    Font("fonts/rubik_semibold.ttf", assets, FontWeight.SemiBold),
    Font("fonts/rubik_bold.ttf", assets, FontWeight.Bold),
)

fun nunitoFamily(assets: AssetManager) = FontFamily(
    Font("fonts/nunito_regular.ttf", assets, FontWeight.Normal),
    Font("fonts/nunito_semibold.ttf", assets, FontWeight.SemiBold),
    Font("fonts/nunito_bold.ttf", assets, FontWeight.Bold),
)

/** Tabularne cifre — sve što stoji u koloni mora da se poklopi. */
val TabularFigures = FontVariation.Settings()

private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Tipografska skala. Naslovi su u rečeničnom slogu — verzal je ostao samo na
 * mikro-oznakama (`labelSmall`), gde radi kao etiketa, a ne kao stil.
 */
fun stowTypography(display: FontFamily, body: FontFamily) = Typography(
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5f.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Mikro-oznaka: verzal sa razmakom. RUČNI, PREDATI, nazivi sekcija.
    labelSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
)
