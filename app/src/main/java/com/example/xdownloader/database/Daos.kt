package com.example.xdownloader.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE tweetId = :id")
    suspend fun getFavoriteById(id: String): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE tweetId = :id)")
    fun isFavorite(id: String): Flow<Boolean>
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM following ORDER BY authorName ASC")
    fun getAllFollowing(): Flow<List<FollowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Delete
    suspend fun deleteFollow(follow: FollowEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM following WHERE authorId = :id)")
    fun isFollowing(id: String): Flow<Boolean>
}
