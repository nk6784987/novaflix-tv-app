package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles ORDER BY isCurrentActive DESC, joinedTimestamp ASC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isCurrentActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileById(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isCurrentActive = 0")
    suspend fun clearActiveProfiles()

    @Query("UPDATE user_profiles SET isCurrentActive = 1 WHERE userId = :userId")
    suspend fun setActiveProfile(userId: String)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)
}
