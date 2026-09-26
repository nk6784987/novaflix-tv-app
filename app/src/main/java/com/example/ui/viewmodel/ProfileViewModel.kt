package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.local.WatchItemEntity
import com.example.data.repository.AppPrefs
import com.example.data.repository.MediaRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.bestName
import com.example.ui.components.bestPhotoUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val uid: String? = null,
    val name: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isGuest: Boolean = true,
    val myListItems: List<WatchItemEntity> = emptyList(),
    val myListCount: Int = 0,
    val historyCount: Int = 0,
    val watchMs: Long = 0L,
    val recent: List<WatchItemEntity> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val autoPlayNext: Boolean = true,
    val quality: AppPrefs.QualityMode = AppPrefs.QualityMode.ADMIN_DEFAULT,
    val speed: Float = 1f,
    val subtitleScale: Float = 1f
)

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val authManager: AuthManager,
    private val prefs: AppPrefs
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        authManager.userFlow,
        mediaRepository.myList,
        mediaRepository.continueWatchingList,
        prefs.state,
        combine(busy, message) { b, m -> b to m }
    ) { user, myList, continueList, p, (isBusy, msg) ->
        val history = continueList.distinctBy { it.mediaId }
        ProfileUiState(
            uid = user?.uid,
            name = user?.bestName(),
            email = user?.email ?: user?.providerData?.firstOrNull { !it.email.isNullOrBlank() }?.email,
            photoUrl = user?.bestPhotoUrl(),
            isGuest = user == null || user.isAnonymous,
            myListItems = myList,
            myListCount = myList.size,
            historyCount = history.size,
            watchMs = p.totalWatchMs,
            recent = history.take(12),
            isBusy = isBusy,
            message = msg,
            autoPlayNext = p.autoPlayNext,
            quality = p.preferredQuality,
            speed = p.defaultSpeed,
            subtitleScale = p.subtitleScale
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun signInWithGoogle(activityContext: Context) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            when (val r = authManager.signInWithGoogle(activityContext)) {
                is AuthManager.SignInResult.Success ->
                    message.value = "Welcome${r.name?.let { ", $it" } ?: ""}! Your My List and watch history will now sync with the cloud."
                is AuthManager.SignInResult.Error -> message.value = r.message
                AuthManager.SignInResult.Cancelled -> {}
            }
            busy.value = false
        }
    }

    fun signOut() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            userRepository.clearLocalUserData()
            authManager.signOut()
            message.value = "Signed out successfully"
            busy.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userRepository.clearHistory()
            message.value = "Watch history cleared"
        }
    }

    fun dismissMessage() { message.value = null }

    fun setAutoPlayNext(v: Boolean) { prefs.autoPlayNext = v }
    fun setQuality(q: AppPrefs.QualityMode) { prefs.preferredQuality = q }
    fun setSpeed(s: Float) { prefs.defaultSpeed = s }
    fun setSubtitleScale(s: Float) { prefs.subtitleScale = s }
}
