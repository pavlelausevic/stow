// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Paleta je fiksna. Material You je isključen namerno: boja ovde nosi značenje —
 * rđa je "treba nabaviti", mastilo je "gotovo". Da paleta prati tapet, "nabaviti"
 * bi jednog dana bilo zeleno.
 *
 * Pravac je putni dokument, ali mek: privezak za prtljag i ulaznica. Papir je hladan
 * sivo-zelen, ne topla krem — daje mirnu podlogu da zaobljeni oblici ne postanu slatkasti.
 */

private val PaperLight = Color(0xFFE9EAE4)
private val SurfaceLight = Color(0xFFFAFAF8)
private val Surface2Light = Color(0xFFEFF0EB)
private val InkLight = Color(0xFF141719)
private val Ink2Light = Color(0xFF474F53)
private val MutedLight = Color(0xFF7B8580)
private val LineLight = Color(0xFFD3D7CF)
private val Line2Light = Color(0xFFB6BCB3)
private val AccentLight = Color(0xFF26428F)
private val AccentSoftLight = Color(0xFFDEE3F2)
private val AlertLight = Color(0xFFA5402A)
private val AlertSoftLight = Color(0xFFF4E1DC)

private val PaperDark = Color(0xFF101211)
private val SurfaceDark = Color(0xFF191C1B)
private val Surface2Dark = Color(0xFF232725)
private val InkDark = Color(0xFFE9EBE6)
private val Ink2Dark = Color(0xFFB4BBB6)
private val MutedDark = Color(0xFF828C87)
private val LineDark = Color(0xFF2C312F)
private val Line2Dark = Color(0xFF3F4642)
private val AccentDark = Color(0xFF93AAEE)
private val AccentSoftDark = Color(0xFF212A4B)
private val AlertDark = Color(0xFFEE9078)
private val AlertSoftDark = Color(0xFF39211B)

val StowLightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    secondary = InkLight,
    onSecondary = PaperLight,
    error = AlertLight,
    onError = Color.White,
    errorContainer = AlertSoftLight,
    onErrorContainer = AlertLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = Ink2Light,
    outline = Line2Light,
    outlineVariant = LineLight,
)

val StowDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0E1120),
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = InkDark,
    onSecondary = PaperDark,
    error = AlertDark,
    onError = Color(0xFF39211B),
    errorContainer = AlertSoftDark,
    onErrorContainer = AlertDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = Ink2Dark,
    outline = Line2Dark,
    outlineVariant = LineDark,
)

/**
 * Semantičke boje su namerno IZVAN Material šeme.
 *
 * Da su unutra, promena Material palete bi ih povukla sa sobom, a one nose značenje
 * koje ne sme da se pomeri. `ink` je posebno važan: "spakovano" nema boju — to je
 * popunjen mastilni kvadrat, kao štiklirano perom. Time je zelena oslobođena i
 * "gotovo" nikad ne konkuriše akcentu.
 */
@Immutable
data class StateColors(
    val paper: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentSoft: Color,
    val alert: Color,
    val alertSoft: Color,
    /** Tekst preko `ink` površine — ploča režima povratka i popunjena kutija. */
    val onInk: Color,
)

val LightStateColors = StateColors(
    paper = PaperLight,
    surface2 = Surface2Light,
    ink = InkLight,
    ink2 = Ink2Light,
    muted = MutedLight,
    line = LineLight,
    line2 = Line2Light,
    accent = AccentLight,
    accentSoft = AccentSoftLight,
    alert = AlertLight,
    alertSoft = AlertSoftLight,
    onInk = PaperLight,
)

/*
 * U tamnoj temi "mastilo" je svetlo, pa se popunjena kutija i ploča režima crtaju
 * tonom `ink2` — puna svetlina bi na crnom papiru bila reflektor. Smer inverzije se
 * okrenuo, princip nije.
 */
val DarkStateColors = StateColors(
    paper = PaperDark,
    surface2 = Surface2Dark,
    ink = Ink2Dark,
    ink2 = Ink2Dark,
    muted = MutedDark,
    line = LineDark,
    line2 = Line2Dark,
    accent = AccentDark,
    accentSoft = AccentSoftDark,
    alert = AlertDark,
    alertSoft = AlertSoftDark,
    onInk = PaperDark,
)

val LocalStateColors = staticCompositionLocalOf { LightStateColors }
