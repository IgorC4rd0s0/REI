package br.com.dubrasil.rei.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE reportId = :reportId ORDER BY updatedAt DESC LIMIT 1")
    fun latestSession(reportId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    fun session(sessionId: String): ChatSessionEntity?

    @Query("UPDATE chat_sessions SET serverConversationId = :serverId, updatedAt = :updatedAt, syncStatus = 'SYNCED' WHERE id = :sessionId")
    fun markRemoteSession(sessionId: String, serverId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun messages(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    fun pendingMessages(limit: Int = 20): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE localIdempotencyKey = :key LIMIT 1")
    fun messageByIdempotency(key: String): ChatMessageEntity?

    @Query("UPDATE chat_messages SET status = :status, sentAt = :sentAt, receivedAt = :receivedAt, serverResponseId = :serverResponseId, errorMessage = :errorMessage WHERE id = :id")
    fun updateStatus(id: String, status: String, sentAt: Long?, receivedAt: Long?, serverResponseId: String, errorMessage: String?)

    @Query("UPDATE chat_messages SET status = 'PENDING', errorMessage = NULL WHERE id = :id")
    fun retry(id: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE status = 'PENDING' OR status = 'SENDING'")
    fun countPending(): Int
}
