package br.com.dubrasil.rei.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuthDao {
    @Query("SELECT * FROM cached_auth_users WHERE lower(username) = lower(:username) LIMIT 1")
    fun findByUsername(username: String): CachedAuthUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(user: CachedAuthUserEntity)
}
