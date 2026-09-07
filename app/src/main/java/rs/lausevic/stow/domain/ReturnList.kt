// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.domain

import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.SectionPhase

/**
 * Šta ulazi u listu povratka.
 *
 * Prvobitni zahtev je rekao da lista "počinje potpuno neoznačena", ali ne i šta je puni.
 * Ako je puni ceo spisak, u njemu su i stavke koje su ostale `TO_BUY` ili `TO_PACK` — a
 * njih fizički nema sa tobom. **Ne možeš zaboraviti ono što nisi ni poneo.**
 *
 * Pravilo:
 *   - iz sekcija faze PACKING i BOTH ulaze samo stavke sa `packStatus == PACKED`
 *   - iz sekcija faze RETURN ulazi SVE (to su podsetnici tipa "kablovi iza TV-a",
 *     koji se nikad i ne pakuju)
 *   - zadaci iz PACKING sekcija se skrivaju: "zaliti biljke" nije nešto što se donosi kući
 *   - nespakovano ne nestaje nego pada u prigušenu grupu "nije poneto", van imenioca
 */
object ReturnList {

    data class Result(
        /** Stavke koje se prate u režimu povratka. */
        val included: List<TripItemEntity>,
        /** Prikazane, ali se ne broje — nisu ni bile na putovanju. */
        val notTaken: List<TripItemEntity>,
    )

    fun build(items: List<TripItemEntity>, phaseOf: (TripItemEntity) -> SectionPhase): Result {
        val included = mutableListOf<TripItemEntity>()
        val notTaken = mutableListOf<TripItemEntity>()

        items.forEach { item ->
            when (phaseOf(item)) {
                SectionPhase.RETURN -> included += item
                SectionPhase.PACKING, SectionPhase.BOTH -> when {
                    item.kind == ItemKind.TASK -> Unit
                    item.packStatus == PackStatus.PACKED -> included += item
                    else -> notTaken += item
                }
            }
        }

        return Result(included, notTaken)
    }
}
