// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.lausevic.stow.R
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.ui.theme.StowTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface

/**
 * Kutija stanja — jedina kontrola koja se u ovoj aplikaciji dodiruje stotinu puta.
 *
 * Zahtev je da četiri stanja budu razlučiva **bez oslanjanja na boju**. Rešenje nije
 * ikonica pored boje, nego da OBLIK nosi značenje:
 *
 *   kvadrat        = predmet          krug            = zadatak
 *   tačkasti okvir = treba nabaviti   popunjeno       = gotovo
 *
 * Na crno-beloj štampi i u sivim tonovima sve četiri ostaju različite. Boja samo pojačava.
 */
@Composable
fun StateBox(
    status: PackStatus,
    kind: ItemKind,
    modifier: Modifier = Modifier,
    returning: Boolean = false,
    isReturned: Boolean = false,
) {
    val colors = StowTheme.state
    val filled = if (returning) isReturned else status == PackStatus.PACKED
    val toBuy = !returning && status == PackStatus.TO_BUY

    val fillAlpha by animateFloatAsState(if (filled) 1f else 0f, label = "fill")

    val outline = when {
        toBuy -> colors.alert
        filled -> colors.ink
        else -> colors.ink2
    }

    val icon: ImageVector? = when {
        returning && isReturned -> StowIcons.Returned
        filled -> StowIcons.Check
        toBuy -> StowIcons.Add
        else -> null
    }

    Box(
        modifier = modifier
            .size(22.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2f
                val corner = if (kind == ItemKind.TASK) size.minDimension / 2f else 7.dp.toPx()
                if (fillAlpha > 0f) {
                    drawRoundRect(
                        color = colors.ink.copy(alpha = fillAlpha),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                }
                drawRoundRect(
                    color = outline,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner - inset, corner - inset),
                    style = Stroke(
                        width = stroke,
                        // Tačke, ne crtice: crtica ima oštre krajeve, a ovde ništa nema.
                        pathEffect = if (toBuy) {
                            PathEffect.dashPathEffect(floatArrayOf(0.1f, stroke * 2.2f))
                        } else {
                            null
                        },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) colors.onInk else outline,
                modifier = Modifier
                    .size(if (returning && isReturned) 13.dp else 14.dp)
                    .alpha(if (filled) fillAlpha else 1f),
            )
        }
    }
    // Značenje se čita iz opisa reda, ne iz kutije — inače TalkBack dvaput izgovori isto.
}

/** Tekstualni naziv stanja, za opise pristupačnosti i za pilule. */
@Composable
fun stateLabel(status: PackStatus, returning: Boolean, isReturned: Boolean): String = when {
    returning && isReturned -> stringResource(R.string.state_returned)
    returning -> stringResource(R.string.state_to_pack)
    status == PackStatus.TO_BUY -> stringResource(R.string.state_to_buy)
    status == PackStatus.PACKED -> stringResource(R.string.state_packed)
    else -> stringResource(R.string.state_to_pack)
}

@Preview(name = "Stanja · svetla", showBackground = true, backgroundColor = 0xFFE9EAE4)
@Composable
private fun StateBoxPreviewLight() = StateBoxPreviewBody(dark = false)

@Preview(name = "Stanja · tamna", showBackground = true, backgroundColor = 0xFF101211)
@Composable
private fun StateBoxPreviewDark() = StateBoxPreviewBody(dark = true)

@Composable
private fun StateBoxPreviewBody(dark: Boolean) {
    rs.lausevic.stow.ui.theme.StowTheme(
        choice = if (dark) rs.lausevic.stow.ui.theme.ThemeChoice.DARK
        else rs.lausevic.stow.ui.theme.ThemeChoice.LIGHT,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateBox(PackStatus.TO_PACK, ItemKind.ITEM)
                StateBox(PackStatus.PACKED, ItemKind.ITEM)
                StateBox(PackStatus.TO_BUY, ItemKind.ITEM)
                StateBox(PackStatus.TO_PACK, ItemKind.TASK)
                StateBox(PackStatus.PACKED, ItemKind.TASK)
                StateBox(PackStatus.PACKED, ItemKind.ITEM, returning = true, isReturned = true)
            }
        }
    }
}
