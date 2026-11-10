package com.rcapture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CaptureEntity::class, CaptureFtsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RcaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile
        private var INSTANCE: RcaptureDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add entitiesJson column to captures table
                db.execSQL("ALTER TABLE captures ADD COLUMN entitiesJson TEXT")
                // Create FTS table matching Room's expected schema
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `captures_fts` USING fts4(content=`captures`, `text`)")
                // Rebuild FTS index
                db.execSQL("INSERT INTO `captures_fts`(`captures_fts`) VALUES('rebuild')")
            }
        }

        fun getDatabase(context: Context): RcaptureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RcaptureDatabase::class.java,
                    "rcapture_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
