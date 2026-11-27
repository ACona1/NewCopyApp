package com.assem.copyapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.assem.copyapp.domain.DetectorType
import com.assem.copyapp.domain.RuleAction
import com.assem.copyapp.domain.SessionMode

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String = SessionMode.SOFT.name,
    val durationMinutes: Int = 30,
    val blockedCsv: String = "",
    val strict: Boolean = false,
    val scheduleStart: Int = -1,
    val scheduleEnd: Int = -1
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sectionId: String = "",
    val action: String = RuleAction.BLOCK_APP.name,
    val startMin: Int = -1,
    val endMin: Int = -1,
    val daysMask: Int = 0x7F,
    val enabled: Boolean = true
)

@Entity(tableName = "detectors")
data class DetectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sectionId: String,
    val type: String = DetectorType.CLASS_NAME.name,
    val markersCsv: String = "",
    val enabled: Boolean = true
)

@Entity(tableName = "daily_limits")
data class DailyLimitEntity(
    @PrimaryKey val datePackage: String,
    val date: String,
    val packageName: String,
    val limitMinutes: Int,
    val usedMinutes: Int = 0
)

@Entity(tableName = "usage_events")
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sectionId: String = "",
    val kind: String,
    val amount: Int = 0,
    val atWall: Long,
    val scoreDelta: Int = 0
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startWall: Long,
    val endWall: Long,
    val startMono: Long,
    val mode: String,
    val blockedCsv: String = "",
    val strict: Boolean = false,
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val breaks: Int = 0,
    val completed: Boolean = false,
    val reason: String = "",
    val profileId: Long = 0L
)

@Entity(tableName = "breaks")
data class BreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val atWall: Long,
    val reason: String,
    val minutesCost: Int = 0
)
