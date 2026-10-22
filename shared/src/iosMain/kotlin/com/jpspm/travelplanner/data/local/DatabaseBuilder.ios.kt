package com.jpspm.travelplanner.data.local

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

// Não compilado nem validado no MVP (RNF-12): não há Mac disponível.
@OptIn(ExperimentalForeignApi::class)
fun createDatabase(): AppDatabase {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    val path = requireNotNull(documents?.path) + "/" + AppDatabase.FILE_NAME
    return Room.databaseBuilder<AppDatabase>(name = path).configure()
}
