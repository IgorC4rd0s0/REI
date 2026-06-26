package br.com.dubrasil.rei.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReportEntity::class, CachedAuthUserEntity::class], version = 3, exportSchema = true)
abstract class ReiDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun authDao(): AuthDao

    companion object {
        @Volatile private var instance: ReiDatabase? = null

        fun getInstance(context: Context): ReiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReiDatabase::class.java,
                "rei_database.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
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
    }
}
