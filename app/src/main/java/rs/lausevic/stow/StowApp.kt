// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow

import android.app.Application
import android.content.Context
import rs.lausevic.stow.data.SettingsStore
import rs.lausevic.stow.data.db.StowDatabase
import rs.lausevic.stow.data.repo.CatalogRepository
import rs.lausevic.stow.data.repo.SeedRepository
import rs.lausevic.stow.data.repo.TransferRepository
import rs.lausevic.stow.data.repo.TripComposer
import rs.lausevic.stow.data.repo.TripRepository

/**
 * Ručna injekcija, bez Hilt-a.
 *
 * Graf je jedna baza, jedan DataStore i četiri repozitorijuma — stane u ovaj fajl.
 * Hilt bi doneo KSP prolaz i sloj anotacija oko nečega što se ovde pročita za minut,
 * a jedina stvar koju bi dodao je odvezivanje testova od `Application`, što ionako
 * dobijamo time što repozitorijumi primaju DAO-e kroz konstruktor.
 */
class AppContainer(context: Context) {

    val database: StowDatabase by lazy { StowDatabase.build(context) }
    val settings: SettingsStore by lazy { SettingsStore(context) }

    val catalog: CatalogRepository by lazy {
        CatalogRepository(database.catalogDao(), database.travellerDao())
    }

    val trips: TripRepository by lazy {
        TripRepository(database.tripDao(), database.templateDao(), database.catalogDao())
    }

    val seed: SeedRepository by lazy {
        SeedRepository(
            assets = context.assets,
            catalogDao = database.catalogDao(),
            travellerDao = database.travellerDao(),
            templateDao = database.templateDao(),
            settings = settings,
        )
    }

    val composer: TripComposer by lazy {
        TripComposer(database.tripDao(), database.catalogDao(), seed, trips)
    }

    val transfer: TransferRepository by lazy {
        TransferRepository(
            catalogDao = database.catalogDao(),
            travellerDao = database.travellerDao(),
            templateDao = database.templateDao(),
            tripDao = database.tripDao(),
            database = database,
        )
    }
}

class StowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as StowApp).container
