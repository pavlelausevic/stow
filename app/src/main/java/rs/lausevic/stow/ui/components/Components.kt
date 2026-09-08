// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.ui.theme.StowShapes
import rs.lausevic.stow.ui.theme.StowTheme

/*
 * Komponente dizajn sistema.
 *
 * Dva pravila koja se lako prekrše i zato stoje napisana:
 *   1. Nijedan oštar ugao. Svaki oblik dolazi iz StowShapes.
 *   2. HIGHLIGHT PRATI RADIJUS SVOG NOSIOCA. Izabrani segment je pilula unutar pilule,
 *      aktivna navigacija je pilula iza ikone, fokus polja je unutrašnji prsten.
 */

/** Verzalna mikro-oznaka: RUČNI, PREDATI, naziv sekcije. Jedino mesto gde verzal ostaje. */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = StowTheme.state.muted,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pilula. Status je i boja i tekst, nikad samo boja. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.NEUTRAL,
) {
    val c = StowTheme.state
    val (fg, bg, border) = when (tone) {
        PillTone.NEUTRAL -> Triple(c.ink2, c.surface2, c.line2)
        PillTone.ACCENT -> Triple(c.accent, c.accentSoft, c.accent)
        PillTone.ALERT -> Triple(c.alert, c.alertSoft, c.alert)
        PillTone.SOLID -> Triple(c.onInk, c.ink, c.ink)
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(StowShapes.pill)
            .background(bg)
            .border(1.dp, border, StowShapes.pill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

enum class PillTone { NEUTRAL, ACCENT, ALERT, SOLID }

/**
 * Zaglavlje sekcije: oznaka, meka linija, brojač.
 *
 * U režimu povratka linija postaje tačkasta — isti signal koji nose kutije stanja,
 * primenjen na strukturu.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    counter: String? = null,
    dotted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = StowTheme.state
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MicroLabel(title, color = c.ink2)
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .clip(StowShapes.pill)
                .background(if (dotted) Color.Transparent else c.line),
        ) {
            if (dotted) DottedRule()
        }
        if (counter != null) MicroLabel(counter, color = c.muted)
        trailing?.invoke()
    }
}

@Composable
private fun DottedRule() {
    val c = StowTheme.state
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(40) {
            Box(
                Modifier
                    .size(2.dp)
                    .clip(StowShapes.pill)
                    .background(c.line2),
            )
        }
    }
}

/**
 * Grupa stavki je zaobljena ploča, a ne niz redova sa crtama preko celog ekrana.
 * Unutar ploče ostaje tanka uvučena crta između redova.
 */
@Composable
fun ItemGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = StowTheme.state
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(StowShapes.group)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, c.line, StowShapes.group)
            .padding(horizontal = 13.dp),
    ) {
        content()
    }
}

/** Uvučena crta između redova u ploči. Poslednji red je ne dobija. */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(StowTheme.state.line),
    )
}

/** Ravna kartica: površina + linija 1 dp, bez senke. Senke na listi prave sivu izmaglicu. */
@Composable
fun StowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val c = StowTheme.state
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(StowShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(if (borderColor != null) 1.5.dp else 1.dp, borderColor ?: c.line),
                StowShapes.card,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

/**
 * Traka napretka — segmenti, kao perforacija na ulaznici.
 *
 * Crveni segmenti su stavke za nabavku: prikazane, ali NE u imeniocu. Stavka koja se tek
 * nabavlja nije nespakovana nego blokirana, i tiho uvlačenje u imenilac bi napredak lagalo.
 */
@Composable
fun ProgressStrip(
    done: Int,
    trackable: Int,
    blocked: Int = 0,
    modifier: Modifier = Modifier,
    segments: Int = 18,
) {
    val c = StowTheme.state
    val total = (trackable + blocked).coerceAtLeast(1)
    val doneSegments = (done.toFloat() / total * segments).toInt().coerceIn(0, segments)
    val blockedSegments = (blocked.toFloat() / total * segments).toInt().coerceIn(0, segments - doneSegments)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(segments) { index ->
            val color = when {
                index < doneSegments -> c.ink
                index >= segments - blockedSegments -> c.alert
                else -> c.line
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(StowShapes.pill)
                    .background(color),
            )
        }
    }
}

/**
 * Putnik se prikazuje inicijalima u okviru, nikad bojom — paleta ima jedan akcenat.
 *
 * `name == null` je zajedničko, i okvir je tada **tačkast**. Ista razlika kao kod kutije
 * stanja: značenje nosi oblik, pa se vidi i u sivim tonovima.
 */
@Composable
fun TravellerChip(
    name: String?,
    modifier: Modifier = Modifier,
) {
    val c = StowTheme.state
    val initials = name?.let(::initialsOf) ?: "—"
    Box(
        modifier = modifier
            .heightIn(min = 20.dp)
            .width(28.dp)
            .clip(StowShapes.pill)
            .background(c.surface2)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val radius = size.height / 2
                drawRoundRect(
                    color = c.line2,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = stroke,
                        // Tačke, ne crtice — isto pravilo kao kutija stanja.
                        pathEffect = if (name == null) {
                            PathEffect.dashPathEffect(floatArrayOf(0.1f, stroke * 2.6f))
                        } else {
                            null
                        },
                        cap = StrokeCap.Round,
                    ),
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            color = c.ink2,
            maxLines = 1,
        )
    }
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "—"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Red filter-pilula. Izabrana je popunjena pilula — highlight prati oblik nosioca. */
@Composable
fun <T> FilterRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StowTheme.state
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp),
    ) {
        items(options) { option ->
            val on = option == selected
            Text(
                text = label(option).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (on) c.onInk else c.ink2,
                maxLines = 1,
                modifier = Modifier
                    .clip(StowShapes.pill)
                    .background(if (on) c.ink else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) c.ink else c.line2, StowShapes.pill)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Segmentovana kontrola: zaobljeni trag, pa zaobljena pilula uvučena 3 dp unutar njega.
 * Nikad dve polovine razdvojene linijom.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StowTheme.state
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(StowShapes.pill)
            .background(c.surface2)
            .border(1.dp, c.line, StowShapes.pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(StowShapes.pill)
                    .background(if (on) c.ink else Color.Transparent)
                    .clickable { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) c.onInk else c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Naslov i vrednost u zaobljenom polju. Polje je udubljenje, ne još jedna kartica. */
@Composable
fun FieldBox(
    label: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = StowTheme.state
    Column(
        modifier = modifier
            .clip(StowShapes.field)
            .background(MaterialTheme.colorScheme.surface)
            // Fokus je unutrašnji prsten, ne outline: outline ne poštuje oblik.
            .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.line2, StowShapes.field)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        MicroLabel(label)
        content()
    }
}

@Composable
fun StowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.INK,
    enabled: Boolean = true,
) {
    val c = StowTheme.state
    val (bg, fg, border) = when (style) {
        ButtonStyle.INK -> Triple(c.ink, c.onInk, c.ink)
        ButtonStyle.ACCENT -> Triple(c.accent, MaterialTheme.colorScheme.onPrimary, c.accent)
        ButtonStyle.GHOST -> Triple(Color.Transparent, c.ink2, c.line2)
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(StowShapes.pill)
            .background(if (enabled) bg else c.surface2)
            .border(1.dp, if (enabled) border else c.line, StowShapes.pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) fg else c.muted,
            maxLines = 1,
        )
    }
}

enum class ButtonStyle { INK, ACCENT, GHOST }

/**
 * Prazno stanje. Svako je napisano i svako kaže nešto korisno — nikad centrirano sivo
 * "nema stavki", koje samo konstatuje odsustvo.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    mark: String = "▤",
    action: (@Composable () -> Unit)? = null,
    secondary: String? = null,
) {
    val c = StowTheme.state
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(StowShapes.panel)
                .border(2.dp, c.line2, StowShapes.panel),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, style = MaterialTheme.typography.headlineMedium, color = c.muted)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = LocalContentColor.current,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = c.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
        if (secondary != null) {
            MicroLabel(secondary)
        }
    }
}

/** Naziv stavke; prigušen kada je gotova, ali nikad precrtan — precrtano se teško čita. */
@Composable
fun ItemTitle(text: String, done: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
        ),
        color = if (done) StowTheme.state.muted else LocalContentColor.current,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
