// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters

/**
 * Šema je kompletna od prve verzije — katalog, putnici, obe ose stanja i faze sekcija.
 * Naknadno dodavanje kataloga značilo bi prepisivanje svakog ekrana, pa se plaća odmah.
 *
 * `exportSchema = true`, a `schemas/` se commit-uje: bez toga migracioni testovi nemaju
 * na šta da se oslone.
 */
@Database(
    entities = [
        CatalogItemEntity::class,
        TravellerEntity::class,
        TemplateEntity::class,
        TemplateSectionEntity::class,
        TemplateEntryEntity::class,
        TripEntity::class,
        TripSectionEntity::class,
        TripItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StowDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun travellerDao(): TravellerDao
    abstract fun templateDao(): TemplateDao
    abstract fun tripDao(): TripDao

    companion object {
        const val NAME = "stow.db"

        /**
         * 1 → 2: `seedKey` na sekcijama i stavkama putovanja.
         *
         * Bez njega je putovanje zauvek ostajalo na jeziku na kom je napravljeno, i
         * prebacivanje aplikacije na drugi jezik je menjalo katalog a ne i listu.
         *
         * Postojeća putovanja se popunjavaju unazad: stavka preko `catalogItemId`, jer
         * kataloška stavka već nosi ključ; sekcija po naslovu, jer veze sa šablonom
         * nema. Poklapanje po naslovu radi dok jezik nije menjan od pravljenja putovanja
         * — najbolje što se može, a ništa se ne kvari ako promaši: takva sekcija samo
         * ostane neprevedena, kao i do sada.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_item ADD COLUMN seedKey TEXT")
                db.execSQL("ALTER TABLE trip_section ADD COLUMN seedKey TEXT")
                db.execSQL(
                    """
                    UPDATE trip_item SET seedKey = (
                        SELECT c.seedKey FROM catalog_item c WHERE c.id = trip_item.catalogItemId
                    ) WHERE catalogItemId IS NOT NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE trip_section SET seedKey = (
                        SELECT s.seedKey FROM template_section s
                        WHERE s.title = trip_section.title AND s.seedKey IS NOT NULL
                        LIMIT 1
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): StowDatabase =
            Room.databaseBuilder(context.applicationContext, StowDatabase::class.java, NAME)
                // Strani ključevi nisu ukras: TripItem.catalogItemId je SET_NULL, pa
                // arhivirana stavka kataloga ne sme da odnese red putovanja sa sobom.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(ForeignKeysOn)
                .addMigrations(MIGRATION_1_2)
                .build()

        private val ForeignKeysOn = object : Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
