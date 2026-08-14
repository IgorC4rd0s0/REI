package br.com.dubrasil.rei.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Conversa local vinculada a um levantamento; não contém token nem segredo de IA. */
@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["reportId"]), Index(value = ["updatedAt"])]
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val skillCode: String,
    val status: String = STATUS_ACTIVE,
    val serverConversationId: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = SYNCED
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_CLOSED = "CLOSED"
        const val SYNCED = "SYNCED"
        const val PENDING = "PENDING"
    }
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["localIdempotencyKey"], unique = true),
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["status"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val localIdempotencyKey: String,
    val sessionId: String,
    val reportId: String,
    val role: String,
    val content: String,
    val status: String = STATUS_PENDING,
    val createdAt: Long,
    val sentAt: Long? = null,
    val receivedAt: Long? = null,
    val serverResponseId: String = "",
    val errorMessage: String? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_RECEIVED = "RECEIVED"
        const val STATUS_FAILED = "FAILED"
    }
}

@Entity(
    tableName = "chat_suggestions",
    indices = [Index(value = ["reportId"]), Index(value = ["sessionId"])]
)
data class ChatSuggestionEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val sessionId: String,
    val type: String,
    val payloadJson: String,
    val status: String = STATUS_WAITING,
    val createdAt: Long,
    val confirmedAt: Long? = null,
    val confirmedBy: String? = null
) {
    companion object {
        const val STATUS_WAITING = "WAITING_CONFIRMATION"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
