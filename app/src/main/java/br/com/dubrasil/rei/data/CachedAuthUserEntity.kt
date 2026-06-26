package br.com.dubrasil.rei.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_auth_users")
data class CachedAuthUserEntity(
    @PrimaryKey val username: String,
    val userId: Int,
    val fullName: String,
    val role: String,
    val passwordSalt: String,
    val passwordHash: String,
    val token: String,
    val serverUrl: String,
    val updatedAt: Long
)
