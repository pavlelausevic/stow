// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

        fun build(context: Context): StowDatabase =
            Room.databaseBuilder(context.applicationContext, StowDatabase::class.java, NAME)
                // Strani ključevi nisu ukras: TripItem.catalogItemId je SET_NULL, pa
                // arhivirana stavka kataloga ne sme da odnese red putovanja sa sobom.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(ForeignKeysOn)
                .build()

        private val ForeignKeysOn = object : Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
