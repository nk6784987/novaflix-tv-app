package com.example.util

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Thread-safe singleton for ExoPlayer SimpleCache to avoid directory lock exceptions
 * (IllegalStateException: Another SimpleCache instance has already locked directory).
 */
@OptIn(UnstableApi::class)
object MediaCacheSingleton {
    @Volatile
    private var instance: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache? {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    runCatching {
                        val cacheDir = File(context.applicationContext.cacheDir, "media_cache")
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs()
                        }
                        val evictor = LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024)
                        val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                        SimpleCache(cacheDir, evictor, dbProvider)
                    }.onSuccess {
                        instance = it
                    }.onFailure { e ->
                        Log.e("MediaCacheSingleton", "Cache initialization bypassed safely: ${e.message}")
                    }
                }
            }
        }
        return instance
    }
}
