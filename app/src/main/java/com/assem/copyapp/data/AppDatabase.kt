package com.assem.copyapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        RuleEntity::class,
        DetectorEntity::class,
        DailyLimitEntity::class,
        UsageEventEntity::class,
        SessionEntity::class,
        BreakEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun ruleDao(): RuleDao
    abstract fun detectorDao(): DetectorDao
    abstract fun dailyLimitDao(): DailyLimitDao
    abstract fun usageEventDao(): UsageEventDao
    abstract fun sessionDao(): SessionDao
    abstract fun breakDao(): BreakDao

    companion object {
        @Volatile
        var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "new_copy_app.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
