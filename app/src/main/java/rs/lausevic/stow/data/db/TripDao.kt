// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import rs.lausevic.stow.data.model.PackStatus

@Dao
interface TripDao {

    @Query("SELECT * FROM trip WHERE isArchived = 0 ORDER BY startDate IS NULL, startDate, createdAt DESC")
    fun observeActive(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip ORDER BY startDate IS NULL, startDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip")
    suspend fun all(): List<TripEntity>

    @Query("SELECT * FROM trip WHERE id = :id")
    fun observeById(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun byId(id: Long): TripEntity?

    @Query("SELECT * FROM trip WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): TripEntity?

    /** Vidžet i prečice biraju "tekuće" putovanje: najbliže po datumu, pa najskorije. */
    @Query(
        """
        SELECT * FROM trip WHERE isArchived = 0 AND completedAt IS NULL
        ORDER BY startDate IS NULL, startDate, createdAt DESC LIMIT 1
        """,
    )
    suspend fun current(): TripEntity?

    @Query("SELECT COUNT(*) FROM trip WHERE isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM trip WHERE isArchived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("UPDATE trip SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip SET mode = :mode, updatedAt = :now WHERE id = :id")
    suspend fun setMode(id: Long, mode: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip SET completedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun setCompleted(id: Long, at: Long)

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun delete(id: Long)

    // --- sekcije ---

    @Query("SELECT * FROM trip_section WHERE tripId = :tripId ORDER BY sortOrder")
    fun observeSections(tripId: Long): Flow<List<TripSectionEntity>>

    @Query("SELECT * FROM trip_section WHERE tripId = :tripId ORDER BY sortOrder")
    suspend fun sections(tripId: Long): List<TripSectionEntity>

    @Query("SELECT * FROM trip_section")
    suspend fun allSections(): List<TripSectionEntity>

    @Query("SELECT * FROM trip_section WHERE id = :id")
    suspend fun sectionById(id: Long): TripSectionEntity?

    @Query("SELECT * FROM trip_section WHERE uuid = :uuid")
    suspend fun sectionByUuid(uuid: String): TripSectionEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM trip_section WHERE tripId = :tripId")
    suspend fun nextSectionOrder(tripId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSection(section: TripSectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSections(sections: List<TripSectionEntity>): List<Long>

    @Update
    suspend fun updateSection(section: TripSectionEntity)

    @Update
    suspend fun updateSections(sections: List<TripSectionEntity>)

    @Query("DELETE FROM trip_section WHERE id = :id")
    suspend fun deleteSection(id: Long)

    // --- stavke ---

    @Query(
        """
        SELECT ti.* FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        WHERE ts.tripId = :tripId
        ORDER BY ts.sortOrder, ti.sortOrder
        """,
    )
    fun observeItems(tripId: Long): Flow<List<TripItemEntity>>

    @Query(
        """
        SELECT ti.* FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        WHERE ts.tripId = :tripId
        ORDER BY ts.sortOrder, ti.sortOrder
        """,
    )
    suspend fun items(tripId: Long): List<TripItemEntity>

    @Query("SELECT * FROM trip_item")
    suspend fun allItems(): List<TripItemEntity>

    @Query("SELECT * FROM trip_item WHERE id = :id")
    suspend fun itemById(id: Long): TripItemEntity?

    @Query("SELECT * FROM trip_item WHERE uuid = :uuid")
    suspend fun itemByUuid(uuid: String): TripItemEntity?

    @Query("SELECT * FROM trip_item WHERE tripSectionId = :sectionId ORDER BY sortOrder")
    suspend fun itemsInSection(sectionId: Long): List<TripItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM trip_item WHERE tripSectionId = :sectionId")
    suspend fun nextItemOrder(sectionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: TripItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<TripItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: TripItemEntity)

    @Update
    suspend fun updateItems(items: List<TripItemEntity>)

    @Query("DELETE FROM trip_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE trip_item SET packStatus = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip_item SET isReturned = :returned, updatedAt = :now WHERE id = :id")
    suspend fun setReturned(id: Long, returned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip_item SET assigneeId = :assigneeId, updatedAt = :now WHERE id = :id")
    suspend fun assignItem(id: Long, assigneeId: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE trip_item SET assigneeId = :assigneeId, updatedAt = :now WHERE tripSectionId = :sectionId")
    suspend fun assignSection(sectionId: Long, assigneeId: Long?, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE trip_item SET packStatus = 'PACKED', updatedAt = :now
        WHERE tripSectionId = :sectionId AND packStatus = 'TO_PACK'
        """,
    )
    suspend fun completeSection(sectionId: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE trip_item SET packStatus = 'TO_PACK', updatedAt = :now
        WHERE tripSectionId IN (SELECT id FROM trip_section WHERE tripId = :tripId)
          AND packStatus = 'PACKED'
        """,
    )
    suspend fun uncheckAll(tripId: Long, now: Long = System.currentTimeMillis())

    /**
     * Napredak. `TO_BUY` NIJE u imeniocu — stavka koja se tek nabavlja nije nespakovana,
     * nego blokirana, i tiho uvlačenje u imenilac bi napredak činilo lažnim.
     */
    @Query(
        """
        SELECT
          SUM(CASE WHEN ti.packStatus = 'PACKED' THEN 1 ELSE 0 END) AS packed,
          SUM(CASE WHEN ti.packStatus IN ('PACKED','TO_PACK') THEN 1 ELSE 0 END) AS trackable,
          SUM(CASE WHEN ti.packStatus = 'TO_BUY' THEN 1 ELSE 0 END) AS toBuy,
          SUM(CASE WHEN ti.isReturned = 1 THEN 1 ELSE 0 END) AS returned,
          COUNT(*) AS total
        FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        WHERE ts.tripId = :tripId
        """,
    )
    fun observeProgress(tripId: Long): Flow<TripProgressRow?>

    @Query(
        """
        SELECT ts.tripId AS tripId,
          SUM(CASE WHEN ti.packStatus = 'PACKED' THEN 1 ELSE 0 END) AS packed,
          SUM(CASE WHEN ti.packStatus IN ('PACKED','TO_PACK') THEN 1 ELSE 0 END) AS trackable,
          SUM(CASE WHEN ti.packStatus = 'TO_BUY' THEN 1 ELSE 0 END) AS toBuy,
          SUM(CASE WHEN ti.isReturned = 1 THEN 1 ELSE 0 END) AS returned,
          COUNT(*) AS total
        FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        GROUP BY ts.tripId
        """,
    )
    fun observeAllProgress(): Flow<List<TripProgressByTrip>>

    /** Spisak za nabavku sa svih aktivnih putovanja, grupisan po sekciji — redosled obilaska radnji. */
    @Query(
        """
        SELECT ti.*, ts.title AS sectionTitle, t.name AS tripName, t.id AS tripId
        FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        JOIN trip t ON t.id = ts.tripId
        WHERE t.isArchived = 0 AND ti.packStatus = 'TO_BUY'
        ORDER BY ts.title COLLATE NOCASE, ti.title COLLATE NOCASE
        """,
    )
    fun observeShoppingList(): Flow<List<ShoppingRow>>

    @Transaction
    suspend fun deleteSectionWithItems(sectionId: Long) {
        deleteSection(sectionId)
    }

    /** Stavke koje su bile spakovane a nisu vraćene — ono što je stvarno ostalo iza tebe. */
    @Query(
        """
        SELECT ti.* FROM trip_item ti
        JOIN trip_section ts ON ts.id = ti.tripSectionId
        WHERE ts.tripId = :tripId AND ti.isReturned = 0
          AND (ti.packStatus = :packed OR ts.phase = 'RETURN')
        ORDER BY ts.sortOrder, ti.sortOrder
        """,
    )
    suspend fun leftBehind(tripId: Long, packed: String = PackStatus.PACKED.name): List<TripItemEntity>
}

data class TripProgressRow(
    val packed: Int,
    val trackable: Int,
    val toBuy: Int,
    val returned: Int,
    val total: Int,
)

data class TripProgressByTrip(
    val tripId: Long,
    val packed: Int,
    val trackable: Int,
    val toBuy: Int,
    val returned: Int,
    val total: Int,
)

data class ShoppingRow(
    val id: Long,
    val uuid: String,
    val tripSectionId: Long,
    val catalogItemId: Long?,
    val title: String,
    val note: String?,
    val quantityCount: Int?,
    val sectionTitle: String,
    val tripName: String,
    val tripId: Long,
)
