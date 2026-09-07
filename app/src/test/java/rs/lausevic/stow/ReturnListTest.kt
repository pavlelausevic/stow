// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.domain.ReturnList

/**
 * Odluka B: ne možeš zaboraviti ono što nisi ni poneo.
 */
class ReturnListTest {

    private var nextId = 1L

    private fun item(
        title: String,
        status: PackStatus = PackStatus.PACKED,
        kind: ItemKind = ItemKind.ITEM,
        sectionId: Long = 1,
    ) = TripItemEntity(
        id = nextId++,
        tripSectionId = sectionId,
        title = title,
        kind = kind,
        packStatus = status,
    )

    /** Sekcija 1 je faza pakovanja, sekcija 2 je faza povratka. */
    private val phaseOf: (TripItemEntity) -> SectionPhase = { entity ->
        if (entity.tripSectionId == 2L) SectionPhase.RETURN else SectionPhase.PACKING
    }

    @Test
    fun `only what was actually packed goes on the return list`() {
        val result = ReturnList.build(
            listOf(
                item("Pasoš", PackStatus.PACKED),
                item("Krema za sunce", PackStatus.TO_BUY),
                item("Adapter", PackStatus.TO_PACK),
            ),
            phaseOf,
        )
        assertEquals(listOf("Pasoš"), result.included.map { it.title })
    }

    @Test
    fun `what was never taken is shown, not silently dropped`() {
        val result = ReturnList.build(
            listOf(
                item("Pasoš", PackStatus.PACKED),
                item("Krema za sunce", PackStatus.TO_BUY),
                item("Adapter", PackStatus.TO_PACK),
            ),
            phaseOf,
        )
        assertEquals(
            listOf("Krema za sunce", "Adapter"),
            result.notTaken.map { it.title },
        )
    }

    @Test
    fun `return-phase sections come along whatever their status`() {
        // "Kablovi iza TV-a" se nikad i ne pakuju — to je podsetnik, ne stavka prtljaga.
        val result = ReturnList.build(
            listOf(
                item("Kablovi iza TV-a", PackStatus.TO_PACK, sectionId = 2),
                item("Punjač iz utičnice", PackStatus.TO_PACK, sectionId = 2),
            ),
            phaseOf,
        )
        assertEquals(2, result.included.size)
        assertTrue(result.notTaken.isEmpty())
    }

    @Test
    fun `tasks from packing sections are hidden on the way home`() {
        val result = ReturnList.build(
            listOf(
                item("Zaliti biljke", PackStatus.PACKED, ItemKind.TASK),
                item("Zatvoriti vodu", PackStatus.TO_PACK, ItemKind.TASK),
                item("Pasoš", PackStatus.PACKED),
            ),
            phaseOf,
        )
        assertEquals(listOf("Pasoš"), result.included.map { it.title })
        assertTrue("zadatak se ne broji ni kao neponet", result.notTaken.isEmpty())
    }

    @Test
    fun `a task inside a return section stays, because it is a collection prompt`() {
        val result = ReturnList.build(
            listOf(item("Proveriti sef", PackStatus.TO_PACK, ItemKind.TASK, sectionId = 2)),
            phaseOf,
        )
        assertEquals(1, result.included.size)
    }

    @Test
    fun `an empty trip produces an empty return list rather than an error`() {
        val result = ReturnList.build(emptyList(), phaseOf)
        assertTrue(result.included.isEmpty())
        assertTrue(result.notTaken.isEmpty())
    }

    @Test
    fun `nothing is lost — every item lands in exactly one bucket`() {
        val items = listOf(
            item("Pasoš", PackStatus.PACKED),
            item("Krema", PackStatus.TO_BUY),
            item("Adapter", PackStatus.TO_PACK),
            item("Kablovi", PackStatus.TO_PACK, sectionId = 2),
        )
        val result = ReturnList.build(items, phaseOf)
        val accounted = result.included.size + result.notTaken.size
        assertEquals("nijedna stavka ne sme da nestane bez objašnjenja", items.size, accounted)
    }
}
