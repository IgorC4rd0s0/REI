package br.com.dubrasil.rei.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reports",
    indices = [
        Index(value = ["status"]),
        Index(value = ["reportId"]),
        Index(value = ["client"]),
        Index(value = ["completedAt"])
    ]
)
data class ReportEntity(
    @PrimaryKey val dbId: String,
    val reportId: String,
    val status: String,
    val client: String,
    val consultant: String,
    val deliveryStatus: String,
    val checkedItems: Int,
    val completedAt: Long?,
    val updatedAt: Long,
    val payloadJson: String,
    val syncStatus: String = SYNC_PENDING,
    val lastSyncAttempt: Long? = null,
    val syncError: String? = null
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_COMPLETED = "COMPLETED"
        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
        const val SYNC_ERROR = "ERROR"
    }
}
