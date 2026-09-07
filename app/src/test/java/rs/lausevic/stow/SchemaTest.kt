// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.lausevic.stow.data.db.StowDatabase
import java.io.File

/**
 * Šema je na verziji 1, pa migracija još nema — ali je infrastruktura koja ih testira
 * ono što se plaća unapred.
 *
 * Ovo tvrdi tri stvari koje su na verziji 1 proverljive i koje bi, da se propuste,
 * migracije kasnije učinile nemogućim:
 *
 *  1. Izvezena šema je **commit-ovana**. Bez fajla u repou `MigrationTestHelper` nema
 *     od čega da kreće i prvi `MIGRATION_1_2` se ne može ni napisati.
 *  2. Commit-ovana šema **odgovara kodu**. Ako se entitet promeni a šema ne re-generiše,
 *     migracija bi se pisala prema fajlu koji laže.
 *  3. Svaka tabela ima `uuid` i `updatedAt`. To je odluka koja mora da važi od prve
 *     verzije, jer je naknadno dodavanje migracija kroz sve tabele.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaTest {

    private val schemaFile = File("schemas/${StowDatabase::class.java.canonicalName}/1.json")

    @Test
    fun `the exported schema is committed`() {
        assertTrue(
            "šema nije nađena na ${schemaFile.absolutePath} — bez nje migracioni testovi " +
                "nemaju od čega da krenu",
            schemaFile.exists(),
        )
    }

    @Test
    fun `the committed schema matches what the code creates`() {
        val committed = JSONObject(schemaFile.readText()).getJSONObject("database")
        assertEquals(1, committed.getInt("version"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StowDatabase::class.java)
            .allowMainThreadQueries().build()
        val live = db.openHelper.writableDatabase

        val liveTables = mutableSetOf<String>()
        live.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) liveTables += cursor.getString(0)
        }

        val entities = committed.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            assertTrue("tabela $table je u šemi ali je baza nema", table in liveTables)

            val liveColumns = mutableSetOf<String>()
            live.query("PRAGMA table_info(`$table`)").use { cursor ->
                while (cursor.moveToNext()) liveColumns += cursor.getString(1)
            }

            val fields = entity.getJSONArray("fields")
            for (f in 0 until fields.length()) {
                val column = fields.getJSONObject(f).getString("columnName")
                assertTrue(
                    "kolona $table.$column je u commit-ovanoj šemi ali je baza nema — " +
                        "šema je zastarela, re-generiši je",
                    column in liveColumns,
                )
            }
        }
        db.close()
    }

    @Test
    fun `every table carries uuid and updatedAt`() {
        val committed = JSONObject(schemaFile.readText()).getJSONObject("database")
        val entities = committed.getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length())
                .map { fields.getJSONObject(it).getString("columnName") }
                .toSet()

            assertTrue("$table nema uuid — aditivni uvoz bez njega ne postoji", "uuid" in columns)
            assertTrue("$table nema updatedAt — spajanje ne bi znalo koja je strana novija", "updatedAt" in columns)
        }
    }
}
