package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WatchItemEntity::class, DownloadEntity::class, UserProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CineStreamDatabase : RoomDatabase() {

    abstract fun watchItemDao(): WatchItemDao
    abstract fun downloadDao(): DownloadDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: CineStreamDatabase? = null

        fun getDatabase(context: Context): CineStreamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CineStreamDatabase::class.java,
                    "cinestream_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
