package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchItemDao {

    @Query("SELECT * FROM watch_items WHERE progressMillis > 1000 ORDER BY lastWatchedTimestamp DESC")
    fun getContinueWatchingItems(): Flow<List<WatchItemEntity>>

    @Query("SELECT * FROM watch_items WHERE isInMyList = 1 ORDER BY lastWatchedTimestamp DESC")
    fun getMyListItems(): Flow<List<WatchItemEntity>>

    @Query("SELECT * FROM watch_items WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getWatchItemByMediaId(mediaId: String): WatchItemEntity?

    @Query("SELECT * FROM watch_items WHERE id = :id LIMIT 1")
    suspend fun getWatchItemById(id: String): WatchItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(watchItem: WatchItemEntity)

    @Query("UPDATE watch_items SET isInMyList = :inList WHERE mediaId = :mediaId")
    suspend fun updateMyListStatus(mediaId: String, inList: Boolean)

    @Query("DELETE FROM watch_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM watch_items")
    suspend fun getAll(): List<WatchItemEntity>

    @Query("SELECT * FROM watch_items WHERE mediaId = :mediaId")
    suspend fun getAllByMediaId(mediaId: String): List<WatchItemEntity>

    @Query("DELETE FROM watch_items")
    suspend fun clearAll()

    @Query("SELECT * FROM watch_items WHERE progressMillis > 1000 ORDER BY lastWatchedTimestamp DESC")
    suspend fun getContinueWatchingOnce(): List<WatchItemEntity>

    @Query("DELETE FROM watch_items WHERE progressMillis > 0 AND isInMyList = 0")
    suspend fun deleteAllHistoryNotInList()

    @Query("UPDATE watch_items SET progressMillis = 0 WHERE isInMyList = 1")
    suspend fun resetProgressForListItems()
}
