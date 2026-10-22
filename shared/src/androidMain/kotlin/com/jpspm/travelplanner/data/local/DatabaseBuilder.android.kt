package com.jpspm.travelplanner.data.local

import android.content.Context
import androidx.room.Room

fun createDatabase(context: Context): AppDatabase {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(AppDatabase.FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(context = appContext, name = dbFile.absolutePath).configure()
}
