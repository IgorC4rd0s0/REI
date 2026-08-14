package br.com.dubrasil.rei.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReportEntity::class, CachedAuthUserEntity::class, ChatSessionEntity::class, ChatMessageEntity::class, ChatSuggestionEntity::class],
    version = 4,
    exportSchema = true
)
abstract class ReiDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun authDao(): AuthDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: ReiDatabase? = null

        fun getInstance(context: Context): ReiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReiDatabase::class.java,
                "rei_database.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reports ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("ALTER TABLE reports ADD COLUMN lastSyncAttempt INTEGER")
                database.execSQL("ALTER TABLE reports ADD COLUMN syncError TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_auth_users (
                        username TEXT NOT NULL PRIMARY KEY,
                        userId INTEGER NOT NULL,
                        fullName TEXT NOT NULL,
                        role TEXT NOT NULL,
                        passwordSalt TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        token TEXT NOT NULL,
                        serverUrl TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        skillCode TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        serverConversationId TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_reportId ON chat_sessions(reportId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_updatedAt ON chat_sessions(updatedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        localIdempotencyKey TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        reportId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        createdAt INTEGER NOT NULL,
                        sentAt INTEGER,
                        receivedAt INTEGER,
                        serverResponseId TEXT NOT NULL DEFAULT '',
                        errorMessage TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_messages_localIdempotencyKey ON chat_messages(localIdempotencyKey)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId_createdAt ON chat_messages(sessionId,createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_status ON chat_messages(status)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_suggestions (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'WAITING_CONFIRMATION',
                        createdAt INTEGER NOT NULL,
                        confirmedAt INTEGER,
                        confirmedBy TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_suggestions_reportId ON chat_suggestions(reportId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_suggestions_sessionId ON chat_suggestions(sessionId)")
            }
        }
    }
}
