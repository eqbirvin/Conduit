package com.conduit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HubNotification::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_notificationKey` ON `notifications` (`notificationKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isArchived_timestamp` ON `notifications` (`isArchived`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `detachedAt` INTEGER DEFAULT NULL")
                db.execSQL("""
                    UPDATE `notifications`
                    SET `kind` = CASE 
                        WHEN UPPER(`channel`) IN ('GOOGLE MESSAGES', 'SMS', 'GMAIL', 'SPARK', 'OUTLOOK', 'EMAIL', 'SNAPCHAT', 'TELEGRAM', 'TELEGRAM X', 'MESSENGER', 'MICROSOFT TEAMS', 'STEAM CHAT') THEN 'MESSAGE'
                        WHEN UPPER(`channel`) IN ('PHONE (GOOGLE DIALER)', 'PHONE', 'TRUECALLER') THEN 'CALL'
                        ELSE 'OTHER'
                    END
                """)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "conduit_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

