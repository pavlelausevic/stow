// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.lausevic.stow.data.db.CatalogItemEntity
import rs.lausevic.stow.data.db.StowDatabase
import rs.lausevic.stow.data.db.TemplateEntity
import rs.lausevic.stow.data.db.TemplateEntryEntity
import rs.lausevic.stow.data.db.TemplateSectionEntity
import rs.lausevic.stow.data.db.TripItemEntity
import rs.lausevic.stow.data.db.TripSectionEntity
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.repo.CatalogRepository
import rs.lausevic.stow.data.repo.TripRepository
import rs.lausevic.stow.domain.TextMatching
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {

    private lateinit var db: StowDatabase
    private lateinit var trips: TripRepository
    private lateinit var catalog: CatalogRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trips = TripRepository(db.tripDao(), db.templateDao(), db.catalogDao())
        catalog = CatalogRepository(db.catalogDao(), db.travellerDao())
    }

    @After
    fun tearDown() = db.close()

    // --- pomocne ---

    private suspend fun catalogItem(
        name: String,
        bag: Bag = Bag.CHECKED,
        ruleType: RuleType = RuleType.FIXED,
        per: Int? = 1,
        plus: Int? = null,
        cap: Int? = null,
        kind: ItemKind = ItemKind.ITEM,
        section: String = "Odeća",
    ): Long = db.catalogDao().insert(
        CatalogItemEntity(
            name = name,
            normalizedName = TextMatching.normalize(name),
            kind = kind,
            defaultBag = bag,
            ruleType = ruleType,
            rulePer = per,
            rulePlus = plus,
            ruleCap = cap,
            defaultSection = section,
        ),
    )

    /** Šablon sa jednom sekcijom i zadatim stavkama kataloga. */
    private suspend fun template(vararg catalogIds: Long): Long {
        val templateId = db.templateDao().insert(TemplateEntity(name = "Test"))
        val sectionId = db.templateDao().insertSection(
            TemplateSectionEntity(templateId = templateId, title = "Odeća", phase = SectionPhase.PACKING),
        )
        catalogIds.forEachIndexed { index, id ->
            db.templateDao().insertEntry(
                TemplateEntryEntity(sectionId = sectionId, catalogItemId = id, sortOrder = index),
            )
        }
        return templateId
    }

    private suspend fun tripFromTemplate(templateId: Long, nights: Int?): Long {
        val start = LocalDate.of(2026, 9, 12).toEpochDay()
        return trips.createFromTemplate(
            templateId = templateId,
            name = "Split",
            destination = "Split",
            startDate = nights?.let { start },
            endDate = nights?.let { start + it },
            accommodation = Accommodation.APARTMENT,
        )
    }

    // --- duboko kopiranje ---

    @Test
    fun `a trip is a snapshot — editing the catalogue afterwards does not touch it`() = runBlocking {
        val socksId = catalogItem("Čarape", ruleType = RuleType.PER_NIGHTS, per = 1, plus = 1, cap = 10)
        val tripId = tripFromTemplate(template(socksId), nights = 7)

        assertEquals("Čarape", trips.items(tripId).single().title)

        catalog.update(db.catalogDao().byId(socksId)!!, name = "Vunene čarape")

        assertEquals(
            "izmena kataloga ne sme da promeni postojeće putovanje",
            "Čarape",
            trips.items(tripId).single().title,
        )
    }

    @Test
    fun `quantities are resolved once, at creation`() = runBlocking {
        val socksId = catalogItem("Čarape", ruleType = RuleType.PER_NIGHTS, per = 1, plus = 1, cap = 10)
        val templateId = template(socksId)

        assertEquals(8, trips.items(tripFromTemplate(templateId, nights = 7)).single().quantityCount)
        assertEquals(10, trips.items(tripFromTemplate(templateId, nights = 14)).single().quantityCount)
        assertNull(
            "bez datuma nema broja",
            trips.items(tripFromTemplate(templateId, nights = null)).single().quantityCount,
        )
    }

    @Test
    fun `the frozen rule travels with the item so its label can be rebuilt in any language`() =
        runBlocking {
            val socksId = catalogItem("Čarape", ruleType = RuleType.PER_NIGHTS, per = 1, plus = 1, cap = 10)
            val item = trips.items(tripFromTemplate(template(socksId), nights = 7)).single()
            assertEquals(RuleType.PER_NIGHTS, item.ruleType)
            assertEquals(1, item.rulePer)
            assertEquals(1, item.rulePlus)
            assertEquals(10, item.ruleCap)
        }

    @Test
    fun `tasks arrive without a bag or a quantity`() = runBlocking {
        val taskId = catalogItem("Zaliti biljke", kind = ItemKind.TASK, ruleType = RuleType.FIXED, per = 3)
        val item = trips.items(tripFromTemplate(template(taskId), nights = 7)).single()
        assertEquals(ItemKind.TASK, item.kind)
        assertEquals(Bag.UNASSIGNED, item.bag)
        assertNull("zadatak nema količinu", item.quantityCount)
    }

    @Test
    fun `creating a trip records catalogue use`() = runBlocking {
        val id = catalogItem("Pasoš")
        assertEquals(0, db.catalogDao().byId(id)!!.timesUsed)
        tripFromTemplate(template(id), nights = 3)
        val used = db.catalogDao().byId(id)!!
        assertEquals(1, used.timesUsed)
        assertNotNull(used.lastUsedAt)
    }

    // --- arhiviranje ---

    @Test
    fun `archiving a catalogue item leaves live trips intact`() = runBlocking {
        val id = catalogItem("Pasoš")
        val tripId = tripFromTemplate(template(id), nights = 3)

        catalog.setArchived(id, true)

        val item = trips.items(tripId).single()
        assertEquals("Pasoš", item.title)
        assertEquals("poreklo se čuva", id, item.catalogItemId)
        assertTrue(db.catalogDao().byId(id)!!.isArchived)
    }

    // --- stanja ---

    @Test
    fun `tapping moves between to-pack and packed, and never into to-buy`() = runBlocking {
        val tripId = tripFromTemplate(template(catalogItem("Pasoš")), nights = 3)
        var item = trips.items(tripId).single()
        assertEquals(PackStatus.TO_PACK, item.packStatus)

        trips.toggle(item, returning = false)
        item = trips.items(tripId).single()
        assertEquals(PackStatus.PACKED, item.packStatus)

        trips.toggle(item, returning = false)
        assertEquals(PackStatus.TO_PACK, trips.items(tripId).single().packStatus)
    }

    @Test
    fun `tapping a to-buy item means bought, not packed`() = runBlocking {
        val tripId = tripFromTemplate(template(catalogItem("Krema")), nights = 3)
        val item = trips.items(tripId).single()
        trips.setStatus(item.id, PackStatus.TO_BUY)

        trips.toggle(trips.items(tripId).single(), returning = false)
        assertEquals(PackStatus.TO_PACK, trips.items(tripId).single().packStatus)
    }

    @Test
    fun `the two axes move independently`() = runBlocking {
        val tripId = tripFromTemplate(template(catalogItem("Pasoš")), nights = 3)
        val item = trips.items(tripId).single()

        trips.setStatus(item.id, PackStatus.PACKED)
        trips.setReturned(item.id, true)
        var reloaded = trips.items(tripId).single()
        assertEquals(PackStatus.PACKED, reloaded.packStatus)
        assertTrue(reloaded.isReturned)

        trips.setStatus(item.id, PackStatus.TO_PACK)
        reloaded = trips.items(tripId).single()
        assertTrue("vraćanje ne sme da se poništi promenom pakovanja", reloaded.isReturned)
    }

    @Test
    fun `to-buy items are blocking, not part of the denominator`() = runBlocking {
        val ids = List(3) { catalogItem("Stavka $it") }
        val tripId = tripFromTemplate(template(*ids.toLongArray()), nights = 3)
        val items = trips.items(tripId)

        trips.setStatus(items[0].id, PackStatus.PACKED)
        trips.setStatus(items[1].id, PackStatus.TO_BUY)

        val progress = db.tripDao().observeProgress(tripId).first()!!
        assertEquals(1, progress.packed)
        assertEquals("nabavka nije u imeniocu", 2, progress.trackable)
        assertEquals(1, progress.toBuy)
    }

    // --- kraj putovanja ---

    @Test
    fun `finishing a trip counts what was packed but never returned`() = runBlocking {
        val chargerId = catalogItem("Punjač")
        val passportId = catalogItem("Pasoš")
        val tripId = tripFromTemplate(template(chargerId, passportId), nights = 3)
        val items = trips.items(tripId)

        items.forEach { trips.setStatus(it.id, PackStatus.PACKED) }
        trips.setReturned(items.first { it.title == "Pasoš" }.id, true)

        val leftBehind = trips.finishTrip(tripId)

        assertEquals(listOf("Punjač"), leftBehind.map { it.title })
        assertEquals(1, db.catalogDao().byId(chargerId)!!.timesLeftBehind)
        assertEquals("vraćeno se ne broji", 0, db.catalogDao().byId(passportId)!!.timesLeftBehind)
        assertNotNull(db.tripDao().byId(tripId)!!.completedAt)
        assertTrue(db.tripDao().byId(tripId)!!.isArchived)
    }

    @Test
    fun `an item that was never packed is not counted as left behind`() = runBlocking {
        val id = catalogItem("Krema")
        val tripId = tripFromTemplate(template(id), nights = 3)
        trips.setStatus(trips.items(tripId).single().id, PackStatus.TO_BUY)

        assertTrue(trips.finishTrip(tripId).isEmpty())
        assertEquals(0, db.catalogDao().byId(id)!!.timesLeftBehind)
    }


    @Test
    fun `an item added later lands at the bottom and brings its catalogue defaults`() =
        runBlocking {
            val ids = List(3) { catalogItem("Stavka $it") }
            val tripId = tripFromTemplate(template(*ids.toLongArray()), nights = 4)
            val sectionId = trips.sections(tripId).single().id

            val charger = catalogItem(
                name = "Punjač",
                bag = Bag.CARRY_ON,
                ruleType = RuleType.PER_NIGHTS,
                per = 1,
                plus = 1,
            )
            val before = db.catalogDao().byId(charger)!!.timesUsed

            trips.addItem(
                sectionId = sectionId,
                catalogItem = db.catalogDao().byId(charger),
                title = "ignorisano",
                kind = ItemKind.ITEM,
                nights = 4,
            )

            val rows = db.tripDao().itemsInSection(sectionId)
            val added = rows.last()
            assertEquals("nova stavka ide na dno", "Punjač", added.title)
            assertEquals("redni broj se nastavlja", 3, added.sortOrder)
            assertEquals("torba dolazi iz kataloga", Bag.CARRY_ON, added.bag)
            assertEquals("naziv iz kataloga pobeđuje slobodan unos", charger, added.catalogItemId)
            // Pravilo se razrešava sada, prema dužini putovanja, pa se zamrzne: 4 + 1.
            assertEquals("količina je razrešena i zamrznuta", 5, added.quantityCount)
            assertEquals("upotreba se broji", before + 1, db.catalogDao().byId(charger)!!.timesUsed)
        }

    @Test
    fun `a free text item carries no catalogue link and no rule`() = runBlocking {
        val ids = List(2) { catalogItem("Stavka $it") }
        val tripId = tripFromTemplate(template(*ids.toLongArray()), nights = 7)
        val sectionId = trips.sections(tripId).single().id

        trips.addItem(
            sectionId = sectionId,
            catalogItem = null,
            title = "  Rezervni ključ  ",
            kind = ItemKind.TASK,
            nights = 7,
        )

        val added = db.tripDao().itemsInSection(sectionId).last()
        assertEquals("Rezervni ključ", added.title)
        assertNull("slobodan unos nema katalošku vezu", added.catalogItemId)
        assertEquals(ItemKind.TASK, added.kind)
        assertNull("zadatak nema količinu", added.quantityCount)
        assertEquals("zadatak nema torbu", Bag.UNASSIGNED, added.bag)
    }
    // --- redosled ---

    @Test
    fun `reordering leaves no gaps in sort order`() = runBlocking {
        val ids = List(4) { catalogItem("Stavka $it") }
        val tripId = tripFromTemplate(template(*ids.toLongArray()), nights = 3)
        val sectionId = trips.sections(tripId).single().id
        val items = trips.items(tripId)

        trips.reorderItems(sectionId, items.map { it.id }.reversed())

        val reordered = db.tripDao().itemsInSection(sectionId)
        assertEquals(listOf(0, 1, 2, 3), reordered.map { it.sortOrder })
        assertEquals(items.map { it.id }.reversed(), reordered.map { it.id })
    }

    @Test
    fun `tidy moves packed items to the bottom without losing any`() = runBlocking {
        val ids = List(4) { catalogItem("Stavka $it") }
        val tripId = tripFromTemplate(template(*ids.toLongArray()), nights = 3)
        val sectionId = trips.sections(tripId).single().id
        val items = trips.items(tripId)

        trips.setStatus(items[0].id, PackStatus.PACKED)
        trips.setStatus(items[2].id, PackStatus.PACKED)
        trips.tidy(tripId)

        val after = db.tripDao().itemsInSection(sectionId)
        assertEquals(4, after.size)
        assertEquals(
            listOf(PackStatus.TO_PACK, PackStatus.TO_PACK, PackStatus.PACKED, PackStatus.PACKED),
            after.map { it.packStatus },
        )
    }

    // --- integritet ---

    @Test
    fun `deleting a section takes its items with it and nothing else`() = runBlocking {
        val tripId = trips.createEmpty("Prazno", null, null, null, Accommodation.HOTEL)
        val keepId = db.tripDao().insertSection(TripSectionEntity(tripId = tripId, title = "Ostaje"))
        val dropId = db.tripDao().insertSection(TripSectionEntity(tripId = tripId, title = "Nestaje"))
        db.tripDao().insertItem(TripItemEntity(tripSectionId = keepId, title = "A"))
        db.tripDao().insertItem(TripItemEntity(tripSectionId = dropId, title = "B"))

        trips.deleteSection(dropId)

        assertEquals(listOf("A"), trips.items(tripId).map { it.title })
    }

    @Test
    fun `every row gets a uuid, and they are unique`() = runBlocking {
        val ids = List(5) { catalogItem("Stavka $it") }
        tripFromTemplate(template(*ids.toLongArray()), nights = 3)

        val uuids = db.catalogDao().all().map { it.uuid } +
            db.tripDao().allItems().map { it.uuid } +
            db.tripDao().allSections().map { it.uuid }

        assertTrue("nijedan uuid ne sme biti prazan", uuids.none { it.isBlank() })
        assertEquals("uuid-i moraju biti jedinstveni", uuids.size, uuids.toSet().size)
    }
}
