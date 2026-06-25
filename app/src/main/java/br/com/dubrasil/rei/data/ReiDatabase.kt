package br.com.dubrasil.rei.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReportEntity::class], version = 2, exportSchema = true)
abstract class ReiDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile private var instance: ReiDatabase? = null

        fun getInstance(context: Context): ReiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReiDatabase::class.java,
                "rei_database.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reports ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("ALTER TABLE reports ADD COLUMN lastSyncAttempt INTEGER")
                database.execSQL("ALTER TABLE reports ADD COLUMN syncError TEXT")
            }
        }
    }
}
