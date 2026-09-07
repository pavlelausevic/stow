// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.lausevic.stow.data.SettingsStore
import rs.lausevic.stow.data.db.StowDatabase
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.TripActivity
import rs.lausevic.stow.data.repo.SeedRepository
import rs.lausevic.stow.data.repo.TransferRepository
import rs.lausevic.stow.data.repo.TripComposer
import rs.lausevic.stow.data.repo.TripRepository
import rs.lausevic.stow.domain.WizardRules
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedAndTransferTest {

    private lateinit var context: Context
    private lateinit var db: StowDatabase
    private lateinit var seed: SeedRepository
    private lateinit var transfer: TransferRepository
    private lateinit var trips: TripRepository
    private lateinit var composer: TripComposer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seed = SeedRepository(
            assets = context.assets,
            catalogDao = db.catalogDao(),
            travellerDao = db.travellerDao(),
            templateDao = db.templateDao(),
            settings = SettingsStore(context),
        )
        transfer = TransferRepository(
            db.catalogDao(), db.travellerDao(), db.templateDao(), db.tripDao(), db,
        )
        trips = TripRepository(db.tripDao(), db.templateDao(), db.catalogDao())
        composer = TripComposer(db.tripDao(), db.catalogDao(), seed, trips)
    }

    @After
    fun tearDown() = db.close()

    // `applyIfNeeded` cita seedVersion iz DataStore-a, koji je procesno globalan, pa bi
    // drugi test krenuo od vec primenjenog seed-a. Zato se primenjuje direktno.
    private suspend fun applySeed() = seed.apply(seed.read())

    // --- seed ---

    @Test
    fun `the seed file parses and covers every section its templates reference`() {
        val file = seed.read()
        assertTrue("seed mora imati stavke", file.items.size > 100)
        assertTrue("seed mora imati oba šablona", file.templates.size == 2)

        val sectionKeys = file.sections.map { it.key }.toSet()
        file.templates.forEach { template ->
            template.sections.forEach { key ->
                assertTrue("šablon ${template.key} traži nepostojeću sekciju $key", key in sectionKeys)
            }
        }
        file.items.forEach { item ->
            assertTrue("stavka ${item.key} je u nepostojećoj sekciji ${item.section}", item.section in sectionKeys)
        }
    }

    @Test
    fun `every item key the wizard rules mention actually exists`() {
        // Pravilo koje pokazuje na nepostojeci kljuc tiho ne uradi nista, pa bi
        // "plaza" prosla bez kupaceg i niko ne bi primetio dok ne bude kasno.
        val file = seed.read()
        val itemKeys = file.items.map { it.key }.toSet()
        val sectionKeys = file.sections.map { it.key }.toSet()

        val allRules = Accommodation.entries.flatMap { accommodation ->
            listOf(null, 3, 7, 14, 30).flatMap { nights ->
                WizardRules.rulesFor(
                    WizardRules.Answers(accommodation, nights, TripActivity.entries.toSet()),
                )
            }
        }

        allRules.forEach { rule ->
            rule.items.forEach { key ->
                assertTrue("pravilo ${rule.id} traži nepostojeću stavku $key", key in itemKeys)
            }
            rule.sections.forEach { key ->
                assertTrue("pravilo ${rule.id} traži nepostojeću sekciju $key", key in sectionKeys)
            }
            rule.quantityOverrides.keys.forEach { key ->
                assertTrue("pravilo ${rule.id} menja količinu nepostojećoj stavci $key", key in itemKeys)
            }
        }
    }

    @Test
    fun `seeding is idempotent — running it twice changes nothing`() = runBlocking {
        applySeed()
        val afterFirst = db.catalogDao().count()
        val templatesAfterFirst = db.templateDao().count()
        assertTrue(afterFirst > 100)

        applySeed()

        assertEquals("drugi prolaz ne sme da duplira katalog", afterFirst, db.catalogDao().count())
        assertEquals(templatesAfterFirst, db.templateDao().count())
        assertEquals(2, db.travellerDao().count())
    }

    @Test
    fun `only core items enter a template, so a swimsuit does not join a December business trip`() =
        runBlocking {
            applySeed()
            val hotel = db.templateDao().bySeedKey("hotel")!!
            val entries = db.templateDao().resolvedEntries(hotel.id)
            assertTrue(entries.isNotEmpty())
            assertTrue(
                "kupaći kostim nije core stavka",
                entries.none { it.catalogSeedKey == "swimsuit" },
            )
            assertTrue("pasoš jeste", entries.any { it.catalogSeedKey == "passport" })
        }

    // --- carobnjak nad seed-om ---

    @Test
    fun `the wizard places an item once, however many rules asked for it`() = runBlocking {
        applySeed()
        val start = LocalDate.of(2026, 9, 12).toEpochDay()
        val tripId = composer.compose(
            TripComposer.Request(
                name = "Split",
                destination = "Split",
                startDate = start,
                endDate = start + 14,
                accommodation = Accommodation.APARTMENT,
                // Obe aktivnosti traze "nicer_shoes".
                activities = setOf(TripActivity.BUSINESS, TripActivity.FORMAL, TripActivity.BEACH),
            ),
        )

        // Naslovi se SMEJU ponoviti: "Kupaći kostim" je i stvar koju pakuješ i podsetnik
        // da ga pokupiš sa terase. Ono što se ne sme ponoviti je ista stavka kataloga.
        val items = trips.items(tripId)
        val catalogIds = items.mapNotNull { it.catalogItemId }
        assertEquals(
            "ista stavka kataloga se ne sme pojaviti dvaput",
            catalogIds.size,
            catalogIds.toSet().size,
        )

        val sections = trips.sections(tripId).map { it.title }
        assertEquals("nijedna sekcija se ne sme pojaviti dvaput", sections.size, sections.toSet().size)
    }

    @Test
    fun `a fourteen night trip switches socks to a fixed ten and records the rule`() = runBlocking {
        applySeed()
        val start = LocalDate.of(2026, 9, 12).toEpochDay()
        val tripId = composer.compose(
            TripComposer.Request("Dugo", null, start, start + 14, Accommodation.APARTMENT, emptySet()),
        )

        val socks = trips.items(tripId).first { it.catalogItemId == db.catalogDao().bySeedKey("socks")!!.id }
        assertEquals("preko dve nedelje se pere, ne pakuje", 10, socks.quantityCount)

        val rotation = trips.items(tripId).firstOrNull {
            it.ruleId == WizardRules.RULE_NIGHTS_GTE5
        }
        assertNotNull("stavke iz pravila moraju da nose svoj ruleId", rotation)
    }

    @Test
    fun `every generated item can say which rule put it there`() = runBlocking {
        applySeed()
        val tripId = composer.compose(
            TripComposer.Request("Bez datuma", null, null, null, Accommodation.HOTEL, emptySet()),
        )
        val items = trips.items(tripId)
        assertTrue(items.isNotEmpty())
        assertTrue("svaka generisana stavka nosi ruleId", items.all { !it.ruleId.isNullOrBlank() })
        assertTrue(
            "ruleId je mašinski identifikator, ne rečenica",
            items.all { ' ' !in it.ruleId!! },
        )
    }

    // --- izvoz i uvoz ---

    @Test
    fun `export and import round trip losslessly`() = runBlocking {
        applySeed()
        val start = LocalDate.of(2026, 9, 12).toEpochDay()
        val tripId = composer.compose(
            TripComposer.Request("Split", "Split", start, start + 7, Accommodation.APARTMENT, setOf(TripActivity.BEACH)),
        )
        val items = trips.items(tripId)
        trips.setStatus(items[0].id, PackStatus.PACKED)
        trips.setStatus(items[1].id, PackStatus.TO_BUY)
        trips.setReturned(items[2].id, true)

        val first = transfer.export()
        val result = transfer.import(first)

        assertEquals("uvoz u istu bazu ne sme da doda ništa", 0, result.added)

        val second = transfer.export()
        assertEquals(
            "izvoz posle uvoza mora biti identičan",
            first.lines().filterNot { it.contains("exportedAt") },
            second.lines().filterNot { it.contains("exportedAt") },
        )
    }

    @Test
    fun `import into an empty database restores everything`() = runBlocking {
        applySeed()
        val tripId = composer.compose(
            TripComposer.Request("Split", "Split", null, null, Accommodation.APARTMENT, emptySet()),
        )
        val expectedItems = trips.items(tripId).size
        val backup = transfer.export()

        val fresh = Room.inMemoryDatabaseBuilder(context, StowDatabase::class.java)
            .allowMainThreadQueries().build()
        val freshTransfer = TransferRepository(
            fresh.catalogDao(), fresh.travellerDao(), fresh.templateDao(), fresh.tripDao(), fresh,
        )

        freshTransfer.import(backup)

        assertEquals(db.catalogDao().count(), fresh.catalogDao().count())
        assertEquals(db.travellerDao().count(), fresh.travellerDao().count())
        assertEquals(1, fresh.tripDao().all().size)
        assertEquals(expectedItems, fresh.tripDao().allItems().size)
        assertEquals(
            "stanja stavki moraju da prežive",
            db.tripDao().allItems().map { it.uuid to it.packStatus }.toSet(),
            fresh.tripDao().allItems().map { it.uuid to it.packStatus }.toSet(),
        )
        fresh.close()
    }

    @Test
    fun `a row matched by natural key adopts the uuid from the backup`() = runBlocking {
        // Ista stavka nastala nezavisno na dva uredjaja: isti naziv, razliciti uuid.
        // Posle uvoza to je jedan red zauvek, ne dva bliska duplikata.
        applySeed()
        val backup = transfer.export()

        val fresh = Room.inMemoryDatabaseBuilder(context, StowDatabase::class.java)
            .allowMainThreadQueries().build()
        val freshSeed = SeedRepository(
            context.assets, fresh.catalogDao(), fresh.travellerDao(), fresh.templateDao(),
            SettingsStore(context),
        )
        val freshTransfer = TransferRepository(
            fresh.catalogDao(), fresh.travellerDao(), fresh.templateDao(), fresh.tripDao(), fresh,
        )
        freshSeed.apply(freshSeed.read())

        val before = fresh.catalogDao().count()
        val localUuid = fresh.catalogDao().bySeedKey("passport")!!.uuid
        val backupUuid = db.catalogDao().bySeedKey("passport")!!.uuid
        assertNotEquals("uuid-i moraju biti različiti da bi test nešto značio", localUuid, backupUuid)

        freshTransfer.import(backup)

        assertEquals("uvoz ne sme da napravi duplikat", before, fresh.catalogDao().count())
        assertEquals(
            "lokalni red preuzima uuid iz kopije",
            backupUuid,
            fresh.catalogDao().bySeedKey("passport")!!.uuid,
        )
        fresh.close()
    }

    @Test
    fun `a backup from a newer schema is refused rather than half-applied`() = runBlocking {
        val text = transfer.export().replace("\"schema\": 1", "\"schema\": 99")
        try {
            transfer.import(text)
            throw AssertionError("uvoz je trebalo da odbije noviju šemu")
        } catch (e: TransferRepository.TooNew) {
            assertEquals(99, e.schema)
        }
    }

    @Test
    fun `something that is not a stow backup is refused`() = runBlocking {
        try {
            transfer.import("""{"hello":"world"}""")
            throw AssertionError("uvoz je trebalo da odbije tuđi fajl")
        } catch (e: TransferRepository.BadFormat) {
            assertNotNull(e.message)
        }
    }
}
