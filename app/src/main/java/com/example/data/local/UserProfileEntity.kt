package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String,
    val avatarIcon: String = "avatar_1", // avatar_1, avatar_2, avatar_3, avatar_4, avatar_5
    val preferredGenre: String = "Action",
    val favoriteMediaType: String = "MOVIE",
    val totalWatchTimeMinutes: Long = 0L,
    val isCurrentActive: Boolean = false,
    val joinedTimestamp: Long = System.currentTimeMillis()
)
