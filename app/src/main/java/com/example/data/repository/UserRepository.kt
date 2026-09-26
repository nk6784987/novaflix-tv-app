package com.example.data.repository

import android.util.Log
import com.example.data.local.WatchItemDao
import com.example.data.local.WatchItemEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the local Room cache and the user's cloud data (users/{uid}) in sync.
 *
 *  - A "session" starts for every Firebase uid (guest or Google).
 *  - Session start: pull cloud -> push anything that only exists on this phone -> apply.
 *  - After that a realtime listener keeps this phone up to date (another phone adds
 *    something to My List -> it shows up here).
 *  - Sign out wipes the local cache so the next account never sees the previous one's data.
 */
class UserRepository(
    private val watchItemDao: WatchItemDao,
    private val firebaseRepository: FirebaseRepository,
    private val prefs: AppPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private var sessionUid: String? = null

    fun currentUser(): FirebaseUser? = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }

    /** Call whenever the auth user changes (also on app start). Safe to call repeatedly. */
    fun startSession(user: FirebaseUser?) {
        val uid = user?.uid
        if (uid == null) {
            sessionJob?.cancel()
            sessionUid = null
            return
        }
        if (uid == sessionUid) {
            // same uid but maybe a guest -> Google upgrade: refresh the account info
            saveAccountInfo(user)
            return
        }
        sessionJob?.cancel()
        sessionUid = uid
        sessionJob = scope.launch {
            try {
                saveAccountInfo(user)
                initialSync(uid)
                firebaseRepository.observeUserCloud(uid).collect { cloud ->
                    applyCloud(cloud, authoritativeMyList = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("UserRepository", "session error: ${e.message}")
            }
        }
    }

    private fun saveAccountInfo(user: FirebaseUser) {
        val google = user.providerData.firstOrNull { it.providerId == "google.com" }
        firebaseRepository.saveAccountInfo(
            uid = user.uid,
            name = user.displayName ?: google?.displayName,
            email = user.email ?: google?.email,
            photoUrl = (user.photoUrl ?: google?.photoUrl)?.toString(),
            isAnonymous = user.isAnonymous
        )
    }

    private suspend fun initialSync(uid: String) {
        val cloud = firebaseRepository.fetchUserCloud(uid) ?: return

        // 1) things that exist only on this phone (guest data / offline changes) go up
        val cloudMyIds = cloud.myList.map { it.mediaId }.toSet()
        val cloudContinueIds = cloud.continueItems.map { it.id }.toSet()
        val local = watchItemDao.getAll()
        for (item in local) {
            if (item.isInMyList && item.mediaId !in cloudMyIds) firebaseRepository.saveMyListItem(uid, item)
            if (item.progressMillis > 1000L && item.id !in cloudContinueIds) {
                firebaseRepository.saveContinueWatchingToFirestore(
                    userId = uid, itemId = item.id, mediaId = item.mediaId, tmdbId = item.tmdbId,
                    title = item.title, posterPath = item.posterPath, backdropPath = item.backdropPath,
                    mediaType = item.mediaType, seasonNumber = item.seasonNumber, episodeNumber = item.episodeNumber,
                    episodeTitle = item.episodeTitle, progressMillis = item.progressMillis, durationMillis = item.durationMillis
                )
            }
        }

        // 2) cloud -> phone. Not authoritative yet: local-only items were only just uploaded.
        applyCloud(cloud, authoritativeMyList = false)
    }

    private suspend fun applyCloud(cloud: CloudUserData, authoritativeMyList: Boolean) {
        // watched time: cloud is the truth once it exists
        cloud.watchTimeMs?.let { prefs.setTotalWatchMs(it) }

        // continue watching: newest timestamp wins, never overwrite newer local progress
        for (c in cloud.continueItems) {
            val local = watchItemDao.getWatchItemById(c.id)
            if (local == null || c.lastWatchedTimestamp > local.lastWatchedTimestamp) {
                val inList = local?.isInMyList ?: watchItemDao.getWatchItemByMediaId(c.mediaId)?.isInMyList ?: false
                watchItemDao.insertOrUpdate(c.copy(isInMyList = inList))
            }
        }

        // my list
        val cloudIds = cloud.myList.map { it.mediaId }.toSet()
        for (m in cloud.myList) {
            val rows = watchItemDao.getAllByMediaId(m.mediaId)
            if (rows.isEmpty()) {
                watchItemDao.insertOrUpdate(m.copy(id = m.mediaId, isInMyList = true))
            } else {
                watchItemDao.updateMyListStatus(m.mediaId, true)
            }
        }
        if (authoritativeMyList) {
            val flagged = watchItemDao.getAll().filter { it.isInMyList }.map { it.mediaId }.toSet()
            for (id in flagged - cloudIds) watchItemDao.updateMyListStatus(id, false)
        }
    }

    /** Called on explicit sign-out. */
    suspend fun clearLocalUserData() {
        sessionJob?.cancel()
        sessionUid = null
        watchItemDao.clearAll()
        prefs.resetUserData()
    }

    suspend fun clearHistory() {
        val uid = currentUser()?.uid
        watchItemDao.deleteAllHistoryNotInList()
        watchItemDao.resetProgressForListItems()
        prefs.setTotalWatchMs(0L)
        if (uid != null) firebaseRepository.clearUserHistory(uid)
    }
}
