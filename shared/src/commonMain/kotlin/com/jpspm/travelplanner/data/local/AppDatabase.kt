package com.jpspm.travelplanner.data.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Migrações são versionadas (RNF-20); o schema exportado fica em `shared/schemas`. */
@Database(
    entities = [
        TripEntity::class,
        PlaceEntity::class,
        DestinationEntity::class,
        ActivityEntity::class,
        ReservationEntity::class,
        AttachmentEntity::class,
        MapSnapshotEntity::class,
        ExpenseEntity::class,
        CategoryBudgetEntity::class,
        ChecklistItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun placeDao(): PlaceDao
    abstract fun destinationDao(): DestinationDao
    abstract fun activityDao(): ActivityDao
    abstract fun reservationDao(): ReservationDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun mapSnapshotDao(): MapSnapshotDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        const val FILE_NAME = "travel_planner.db"
    }
}

// O compilador do Room gera os `actual` deste objeto para cada plataforma.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun RoomDatabase.Builder<AppDatabase>.configure(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(connection: SQLiteConnection) {
                // Garante a exclusão em cascata das tabelas filhas (RNF-04).
                connection.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        .build()
