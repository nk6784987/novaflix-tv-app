package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_items ORDER BY createdAtTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY createdAtTimestamp DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM download_items WHERE mediaId = :mediaId AND seasonNumber = :season AND episodeNumber = :episode LIMIT 1")
    suspend fun getDownloadByEpisode(mediaId: String, season: Int, episode: Int): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("UPDATE download_items SET status = :status, progressPercent = :progress, downloadedBytes = :downloadedBytes, fileSizeBytes = :totalBytes, localFilePath = :filePath WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: String, progress: Int, downloadedBytes: Long, totalBytes: Long, filePath: String?)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_items")
    suspend fun deleteAll()
}
