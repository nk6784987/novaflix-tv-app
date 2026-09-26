package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.MediaType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class StorageInfo(
    val totalSpaceBytes: Long,
    val freeSpaceBytes: Long,
    val appDownloadsBytes: Long
)

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient
) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()

    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val downloadsDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "CineStreamDownloads")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun startOrResumeDownload(
        mediaId: String,
        tmdbId: Long?,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        mediaType: MediaType,
        season: Int = 1,
        episode: Int = 1,
        episodeTitle: String? = null,
        streamUrl: String
    ) {
        val downloadId = "dl_${mediaType.name.lowercase()}_${mediaId}_s${season}_e$episode"

        var existing = downloadDao.getDownloadById(downloadId)
        if (existing == null) {
            existing = DownloadEntity(
                id = downloadId,
                mediaId = mediaId,
                tmdbId = tmdbId,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                mediaType = mediaType.name,
                seasonNumber = season,
                episodeNumber = episode,
                episodeTitle = episodeTitle,
                streamUrl = streamUrl,
                status = "QUEUED",
                progressPercent = 0
            )
            downloadDao.insertOrUpdate(existing)
        } else {
            downloadDao.updateDownloadProgress(
                id = downloadId,
                status = "QUEUED",
                progress = existing.progressPercent,
                downloadedBytes = existing.downloadedBytes,
                totalBytes = existing.fileSizeBytes,
                filePath = existing.localFilePath
            )
        }

        activeDownloadJobs[downloadId]?.cancel()

        val job = scope.launch {
            executeDownload(downloadId, streamUrl, title, mediaType, season, episode)
        }
        activeDownloadJobs[downloadId] = job
    }

    private suspend fun executeDownload(
        downloadId: String,
        streamUrl: String,
        title: String,
        mediaType: MediaType,
        season: Int,
        episode: Int
    ) = withContext(Dispatchers.IO) {
        try {
            downloadDao.updateDownloadProgress(
                id = downloadId,
                status = "DOWNLOADING",
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                filePath = null
            )

            // Direct file check: HLS streams (.m3u8) or empty URLs cannot be directly downloaded as single MP4 files.
            if (streamUrl.isEmpty() || streamUrl.contains(".m3u8")) {
                downloadDao.updateDownloadProgress(
                    id = downloadId,
                    status = "FAILED",
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    filePath = null
                )
                return@withContext
            }

            val fileName = "${mediaType.name.lowercase()}_${downloadId}.mp4"
            val outputFile = File(downloadsDir, fileName)

            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                downloadDao.updateDownloadProgress(
                    id = downloadId,
                    status = "FAILED",
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    filePath = null
                )
                return@withContext
            }

            val body = response.body!!
            val totalBytes = body.contentLength().let { if (it <= 0) 10500000L else it }
            val inputStream = body.byteStream()
            val outputStream = outputFile.outputStream()

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalDownloaded = 0L
            var lastUpdatePercent = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!isActive) {
                    outputStream.close()
                    inputStream.close()
                    downloadDao.updateDownloadProgress(
                        id = downloadId,
                        status = "PAUSED",
                        progress = lastUpdatePercent,
                        downloadedBytes = totalDownloaded,
                        totalBytes = totalBytes,
                        filePath = outputFile.absolutePath
                    )
                    return@withContext
                }

                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead

                val currentPercent = ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                if (currentPercent >= lastUpdatePercent + 5 || currentPercent == 100) {
                    lastUpdatePercent = currentPercent
                    downloadDao.updateDownloadProgress(
                        id = downloadId,
                        status = "DOWNLOADING",
                        progress = currentPercent,
                        downloadedBytes = totalDownloaded,
                        totalBytes = totalBytes,
                        filePath = outputFile.absolutePath
                    )
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            downloadDao.updateDownloadProgress(
                id = downloadId,
                status = "COMPLETED",
                progress = 100,
                downloadedBytes = totalDownloaded,
                totalBytes = totalBytes,
                filePath = outputFile.absolutePath
            )

        } catch (e: Exception) {
            e.printStackTrace()
            downloadDao.updateDownloadProgress(
                id = downloadId,
                status = "FAILED",
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                filePath = null
            )
        }
    }

    fun pauseDownload(id: String) {
        activeDownloadJobs[id]?.cancel()
        activeDownloadJobs.remove(id)
        scope.launch {
            val item = downloadDao.getDownloadById(id)
            if (item != null) {
                downloadDao.updateDownloadProgress(
                    id = id,
                    status = "PAUSED",
                    progress = item.progressPercent,
                    downloadedBytes = item.downloadedBytes,
                    totalBytes = item.fileSizeBytes,
                    filePath = item.localFilePath
                )
            }
        }
    }

    fun deleteDownload(id: String) {
        activeDownloadJobs[id]?.cancel()
        activeDownloadJobs.remove(id)
        scope.launch {
            val item = downloadDao.getDownloadById(id)
            if (item?.localFilePath != null) {
                try {
                    val file = File(item.localFilePath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            downloadDao.deleteById(id)
        }
    }

    fun getStorageInfo(): StorageInfo {
        val stat = StatFs(downloadsDir.path)
        val totalSpace = stat.totalBytes
        val freeSpace = stat.availableBytes

        var appDownloadsBytes = 0L
        downloadsDir.listFiles()?.forEach { file ->
            if (file.isFile) appDownloadsBytes += file.length()
        }

        return StorageInfo(
            totalSpaceBytes = totalSpace,
            freeSpaceBytes = freeSpace,
            appDownloadsBytes = appDownloadsBytes
        )
    }
}
