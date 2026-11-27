package com.assem.copyapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun flow(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Delete
    suspend fun delete(profile: ProfileEntity)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY id ASC")
    fun flow(): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clear()
}

@Dao
interface DetectorDao {
    @Query("SELECT * FROM detectors ORDER BY id ASC")
    fun flow(): Flow<List<DetectorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detector: DetectorEntity)

    @Query("DELETE FROM detectors WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM detectors")
    suspend fun clear()
}

@Dao
interface DailyLimitDao {
    @Query("SELECT * FROM daily_limits ORDER BY date DESC, packageName ASC")
    fun flow(): Flow<List<DailyLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: DailyLimitEntity)

    @Query("UPDATE daily_limits SET usedMinutes = usedMinutes + :minutes WHERE datePackage = :datePackage")
    suspend fun addUsed(datePackage: String, minutes: Int)

    @Query("DELETE FROM daily_limits WHERE date = :date")
    suspend fun clearDate(date: String)

    @Query("DELETE FROM daily_limits")
    suspend fun clear()
}

@Dao
interface UsageEventDao {
    @Query("SELECT * FROM usage_events ORDER BY atWall DESC, id DESC LIMIT :limit")
    fun flow(limit: Int = 200): Flow<List<UsageEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: UsageEventEntity)

    @Query("DELETE FROM usage_events")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startWall DESC, id DESC LIMIT :limit")
    fun flow(limit: Int = 100): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("UPDATE sessions SET completed = 1, actualMinutes = :actualMinutes WHERE id = :id")
    suspend fun markCompleted(id: Long, actualMinutes: Int)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface BreakDao {
    @Query("SELECT * FROM breaks ORDER BY atWall ASC, id ASC")
    fun flow(): Flow<List<BreakEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BreakEntity)

    @Query("DELETE FROM breaks")
    suspend fun clear()
}
