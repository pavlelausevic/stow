// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalog_item WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_item ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_item WHERE isArchived = 0 ORDER BY timesUsed DESC, name COLLATE NOCASE")
    fun observeByUsage(): Flow<List<CatalogItemEntity>>

    /**
     * "Nosim svuda, a nikad ne diram" — stavke koje nisu bile ni na jednom putovanju
     * u poslednjih godinu dana. Nikad korišćene se broje ovde, jer su isti problem.
     */
    @Query(
        """
        SELECT * FROM catalog_item
        WHERE isArchived = 0 AND (lastUsedAt IS NULL OR lastUsedAt < :cutoff)
        ORDER BY lastUsedAt IS NOT NULL, lastUsedAt ASC, name COLLATE NOCASE
        """,
    )
    fun observeUnusedSince(cutoff: Long): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_item WHERE id = :id")
    fun observeById(id: Long): Flow<CatalogItemEntity?>

    @Query("SELECT * FROM catalog_item WHERE id = :id")
    suspend fun byId(id: Long): CatalogItemEntity?

    @Query("SELECT * FROM catalog_item WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_item WHERE seedKey = :seedKey LIMIT 1")
    suspend fun bySeedKey(seedKey: String): CatalogItemEntity?

    /**
     * SVI redovi sa datim normalizovanim nazivom, ne samo prvi.
     *
     * Naziv nije jedinstven i ne moze da bude: "Kupaci kostim" je i stvar koju pakujes i
     * podsetnik da ga pokupis sa terase pre odlaska. To su dve razlicite stavke sa istim
     * imenom, pa poklapanje po prirodnom kljucu mora da gleda i `seedKey`.
     */
    @Query("SELECT * FROM catalog_item WHERE normalizedName = :normalized")
    suspend fun allByNormalizedName(normalized: String): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_item")
    suspend fun all(): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_item WHERE isArchived = 0")
    suspend fun allActive(): List<CatalogItemEntity>

    @Query("SELECT COUNT(*) FROM catalog_item WHERE kind = 'ITEM' AND isArchived = 0")
    fun observeItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_item WHERE kind = 'TASK' AND isArchived = 0")
    fun observeTaskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_item")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CatalogItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<CatalogItemEntity>): List<Long>

    @Update
    suspend fun update(item: CatalogItemEntity)

    /** Arhiviranje, nikad brisanje: putovanja i šabloni je referenciraju. */
    @Query("UPDATE catalog_item SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE catalog_item
        SET timesUsed = timesUsed + 1, lastUsedAt = :now, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun recordUse(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE catalog_item
        SET timesLeftBehind = timesLeftBehind + 1, lastLeftBehindAt = :now, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun recordLeftBehind(id: Long, now: Long = System.currentTimeMillis())

    /**
     * Predlozi za putovanje: najkorišćenije stavke koje na ovom putovanju još nisu.
     * Rangiranje je po prostom `timesUsed` — uslov "za isti tip smeštaja" bi tražio
     * zasebnu tabelu upotrebe, a jedna rečenica ne opravdava tabelu.
     */
    @Query(
        """
        SELECT c.* FROM catalog_item c
        WHERE c.isArchived = 0 AND c.kind = 'ITEM' AND c.timesUsed > 0
          AND c.id NOT IN (
            SELECT ti.catalogItemId FROM trip_item ti
            JOIN trip_section ts ON ts.id = ti.tripSectionId
            WHERE ts.tripId = :tripId AND ti.catalogItemId IS NOT NULL
          )
        ORDER BY c.timesUsed DESC, c.lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeSuggestions(tripId: Long, limit: Int = 8): Flow<List<CatalogItemEntity>>
}

@Dao
interface TravellerDao {

    @Query("SELECT * FROM traveller ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TravellerEntity>>

    @Query("SELECT * FROM traveller ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<TravellerEntity>

    @Query("SELECT * FROM traveller WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): TravellerEntity?

    @Query("SELECT * FROM traveller WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TravellerEntity?

    @Query("SELECT COUNT(*) FROM traveller")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(traveller: TravellerEntity): Long

    @Update
    suspend fun update(traveller: TravellerEntity)

    @Query("DELETE FROM traveller WHERE id = :id")
    suspend fun delete(id: Long)
}
