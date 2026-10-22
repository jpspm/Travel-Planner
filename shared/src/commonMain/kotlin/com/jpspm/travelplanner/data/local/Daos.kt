package com.jpspm.travelplanner.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate ASC, name ASC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripEntity?

    @Query("SELECT * FROM trips")
    suspend fun getAll(): List<TripEntity>

    /** Última alteração em qualquer tabela (epoch ms), para o lembrete de backup. */
    @Query(
        """
        SELECT MAX(u) FROM (
          SELECT MAX(updatedAt) AS u FROM trips
          UNION ALL SELECT MAX(updatedAt) FROM places
          UNION ALL SELECT MAX(updatedAt) FROM destinations
          UNION ALL SELECT MAX(updatedAt) FROM activities
          UNION ALL SELECT MAX(updatedAt) FROM reservations
          UNION ALL SELECT MAX(updatedAt) FROM attachments
          UNION ALL SELECT MAX(updatedAt) FROM expenses
          UNION ALL SELECT MAX(updatedAt) FROM category_budgets
          UNION ALL SELECT MAX(updatedAt) FROM checklist_items
        )
        """
    )
    fun observeLastChange(): Flow<Long?>

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places WHERE tripId = :tripId")
    fun observeByTrip(tripId: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT * FROM places WHERE tripId = :tripId
          AND id NOT IN (SELECT placeId FROM destinations WHERE placeId IS NOT NULL)
          AND id NOT IN (SELECT placeId FROM activities WHERE placeId IS NOT NULL)
          AND id NOT IN (SELECT placeId FROM reservations WHERE placeId IS NOT NULL)
        """
    )
    suspend fun getUnused(tripId: String): List<PlaceEntity>

    /** Remove locais da viagem que não são mais usados por nenhum destino, atividade ou reserva. */
    @Query(
        """
        DELETE FROM places WHERE tripId = :tripId
          AND id NOT IN (SELECT placeId FROM destinations WHERE placeId IS NOT NULL)
          AND id NOT IN (SELECT placeId FROM activities WHERE placeId IS NOT NULL)
          AND id NOT IN (SELECT placeId FROM reservations WHERE placeId IS NOT NULL)
        """
    )
    suspend fun deleteUnused(tripId: String)
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations WHERE tripId = :tripId ORDER BY arrivalDate ASC, sortOrder ASC")
    fun observeByTrip(tripId: String): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<DestinationEntity>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun get(id: String): DestinationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM destinations WHERE tripId = :tripId")
    suspend fun maxSortOrder(tripId: String): Int

    @Upsert
    suspend fun upsert(destination: DestinationEntity)

    @Query("DELETE FROM destinations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE tripId = :tripId")
    fun observeByTrip(tripId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE tripId = :tripId AND date = :date AND startTime IS NULL ORDER BY sortOrder ASC")
    suspend fun getUntimedOfDay(tripId: String, date: String): List<ActivityEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM activities WHERE tripId = :tripId AND date = :date")
    suspend fun maxSortOrder(tripId: String, date: String): Int

    @Upsert
    suspend fun upsert(activity: ActivityEntity)

    @Upsert
    suspend fun upsertAll(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations WHERE tripId = :tripId ORDER BY startEpochMs IS NULL, startEpochMs ASC, title ASC")
    fun observeByTrip(tripId: String): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<ReservationEntity>

    @Query("SELECT * FROM reservations WHERE id = :id")
    fun observe(id: String): Flow<ReservationEntity?>

    @Query("SELECT * FROM reservations WHERE id = :id")
    suspend fun get(id: String): ReservationEntity?

    @Upsert
    suspend fun upsert(reservation: ReservationEntity)

    @Query("DELETE FROM reservations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AttachmentDao {
    @Query(
        """
        SELECT a.* FROM attachments a JOIN reservations r ON r.id = a.reservationId
        WHERE r.tripId = :tripId ORDER BY a.createdAt ASC
        """
    )
    fun observeByTrip(tripId: String): Flow<List<AttachmentEntity>>

    @Query(
        """
        SELECT a.* FROM attachments a JOIN reservations r ON r.id = a.reservationId
        WHERE r.tripId = :tripId
        """
    )
    suspend fun getByTrip(tripId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE reservationId = :reservationId ORDER BY createdAt ASC")
    fun observeByReservation(reservationId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE reservationId = :reservationId")
    suspend fun getByReservation(reservationId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MapSnapshotDao {
    @Query("SELECT * FROM map_snapshots WHERE tripId = :tripId")
    fun observeByTrip(tripId: String): Flow<List<MapSnapshotEntity>>

    @Query("SELECT * FROM map_snapshots WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<MapSnapshotEntity>

    @Query("SELECT * FROM map_snapshots WHERE id = :id")
    suspend fun get(id: String): MapSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: MapSnapshotEntity)

    @Query("DELETE FROM map_snapshots WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, createdAt DESC")
    fun observeByTrip(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets WHERE tripId = :tripId")
    fun observeByTrip(tripId: String): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets WHERE tripId = :tripId AND category = :category")
    suspend fun get(tripId: String, category: String): CategoryBudgetEntity?

    @Upsert
    suspend fun upsert(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: String)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY sortOrder ASC")
    fun observeByTrip(tripId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun get(id: String): ChecklistItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM checklist_items WHERE tripId = :tripId")
    suspend fun maxSortOrder(tripId: String): Int

    @Upsert
    suspend fun upsert(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun delete(id: String)
}
