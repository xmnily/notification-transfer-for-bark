package dev.yakitori.barkforwarder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppRuleEntity::class,
        DedupeEntryEntity::class,
        NotificationHistoryEntity::class,
        NotificationRuleEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun dedupeDao(): DedupeDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun notificationRuleDao(): NotificationRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_history` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `renderedTitle` TEXT NOT NULL,
                        `renderedBody` TEXT NOT NULL,
                        `postedAt` INTEGER NOT NULL,
                        `forwardedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_rules` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `packageLabelAtCreation` TEXT NOT NULL,
                        `appNamePattern` TEXT,
                        `titlePattern` TEXT,
                        `bodyPattern` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_notification_rules_packageName`
                    ON `notification_rules` (`packageName`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_notification_history_forwardedAt`
                    ON `notification_history` (`forwardedAt`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notification_history` ADD COLUMN `log` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notification_history` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'SUCCESS'",
                )
                db.execSQL(
                    "ALTER TABLE `notification_history` ADD COLUMN `filterReason` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "bark_forwarder.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
