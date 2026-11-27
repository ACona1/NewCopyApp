package com.assem.copyapp.data

import com.assem.copyapp.domain.DetectorConfig
import com.assem.copyapp.domain.DetectorType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private fun datePackageKey(date: String, packageName: String): String = "$date|$packageName"

private fun jsonArrayOf(items: List<JSONObject>): JSONArray {
    val array = JSONArray()
    items.forEach { array.put(it) }
    return array
}

private fun ProfileEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("mode", mode)
    .put("durationMinutes", durationMinutes)
    .put("blockedCsv", blockedCsv)
    .put("strict", strict)
    .put("scheduleStart", scheduleStart)
    .put("scheduleEnd", scheduleEnd)

private fun RuleEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("packageName", packageName)
    .put("sectionId", sectionId)
    .put("action", action)
    .put("startMin", startMin)
    .put("endMin", endMin)
    .put("daysMask", daysMask)
    .put("enabled", enabled)

private fun DetectorEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("packageName", packageName)
    .put("sectionId", sectionId)
    .put("type", type)
    .put("markersCsv", markersCsv)
    .put("enabled", enabled)

private fun DailyLimitEntity.toJson(): JSONObject = JSONObject()
    .put("datePackage", datePackage)
    .put("date", date)
    .put("packageName", packageName)
    .put("limitMinutes", limitMinutes)
    .put("usedMinutes", usedMinutes)

private fun SessionEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("startWall", startWall)
    .put("endWall", endWall)
    .put("startMono", startMono)
    .put("mode", mode)
    .put("blockedCsv", blockedCsv)
    .put("strict", strict)
    .put("plannedMinutes", plannedMinutes)
    .put("actualMinutes", actualMinutes)
    .put("breaks", breaks)
    .put("completed", completed)
    .put("reason", reason)
    .put("profileId", profileId)

private fun UsageEventEntity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("packageName", packageName)
    .put("sectionId", sectionId)
    .put("kind", kind)
    .put("amount", amount)
    .put("atWall", atWall)
    .put("scoreDelta", scoreDelta)

private fun AppSettings.toJson(): JSONObject {
    val exceptions = JSONArray()
    userExceptions.sorted().forEach { exceptions.put(it) }
    return JSONObject()
        .put("onboardingDone", onboardingDone)
        .put("defaultMode", defaultMode.name)
        .put("defaultDurationMinutes", defaultDurationMinutes)
        .put("strictByDefault", strictByDefault)
        .put("userExceptions", exceptions)
        .put("friendKeyHash", friendKeyHash)
        .put("windDownEnabled", windDownEnabled)
        .put("windDownStartMin", windDownStartMin)
        .put("bedtimeMin", bedtimeMin)
        .put("pauseRequiredSeconds", pauseRequiredSeconds)
        .put("notificationsEnabled", notificationsEnabled)
        .put("digestEnabled", digestEnabled)
        .put("language", language)
        .put("lastDetectorPackage", lastDetectorPackage)
}

private fun profileFromJson(o: JSONObject): ProfileEntity = ProfileEntity(
    id = o.optLong("id", 0L),
    name = o.optString("name", ""),
    mode = o.optString("mode", com.assem.copyapp.domain.SessionMode.SOFT.name),
    durationMinutes = o.optInt("durationMinutes", 30),
    blockedCsv = o.optString("blockedCsv", ""),
    strict = o.optBoolean("strict", false),
    scheduleStart = o.optInt("scheduleStart", -1),
    scheduleEnd = o.optInt("scheduleEnd", -1)
)

private fun ruleFromJson(o: JSONObject): RuleEntity = RuleEntity(
    id = o.optLong("id", 0L),
    packageName = o.optString("packageName", ""),
    sectionId = o.optString("sectionId", ""),
    action = o.optString("action", com.assem.copyapp.domain.RuleAction.BLOCK_APP.name),
    startMin = o.optInt("startMin", -1),
    endMin = o.optInt("endMin", -1),
    daysMask = o.optInt("daysMask", 0x7F),
    enabled = o.optBoolean("enabled", true)
)

private fun detectorFromJson(o: JSONObject): DetectorEntity = DetectorEntity(
    id = o.optLong("id", 0L),
    packageName = o.optString("packageName", ""),
    sectionId = o.optString("sectionId", ""),
    type = o.optString("type", DetectorType.CLASS_NAME.name),
    markersCsv = o.optString("markersCsv", ""),
    enabled = o.optBoolean("enabled", true)
)

private fun limitFromJson(o: JSONObject): DailyLimitEntity = DailyLimitEntity(
    datePackage = o.optString("datePackage", ""),
    date = o.optString("date", ""),
    packageName = o.optString("packageName", ""),
    limitMinutes = o.optInt("limitMinutes", 0),
    usedMinutes = o.optInt("usedMinutes", 0)
)

private fun sessionFromJson(o: JSONObject): SessionEntity = SessionEntity(
    id = o.optLong("id", 0L),
    startWall = o.optLong("startWall", 0L),
    endWall = o.optLong("endWall", 0L),
    startMono = o.optLong("startMono", 0L),
    mode = o.optString("mode", com.assem.copyapp.domain.SessionMode.SOFT.name),
    blockedCsv = o.optString("blockedCsv", ""),
    strict = o.optBoolean("strict", false),
    plannedMinutes = o.optInt("plannedMinutes", 0),
    actualMinutes = o.optInt("actualMinutes", 0),
    breaks = o.optInt("breaks", 0),
    completed = o.optBoolean("completed", false),
    reason = o.optString("reason", ""),
    profileId = o.optLong("profileId", 0L)
)

private fun eventFromJson(o: JSONObject): UsageEventEntity = UsageEventEntity(
    id = o.optLong("id", 0L),
    packageName = o.optString("packageName", ""),
    sectionId = o.optString("sectionId", ""),
    kind = o.optString("kind", ""),
    amount = o.optInt("amount", 0),
    atWall = o.optLong("atWall", 0L),
    scoreDelta = o.optInt("scoreDelta", 0)
)

private fun stringSetFromJson(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    return (0 until array.length())
        .map { array.optString(it) }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun settingsFromJson(o: JSONObject): AppSettings {
    val storedMode = o.optString("defaultMode", com.assem.copyapp.domain.SessionMode.SOFT.name)
    return AppSettings(
        onboardingDone = o.optBoolean("onboardingDone", false),
        defaultMode = runCatching { com.assem.copyapp.domain.SessionMode.valueOf(storedMode) }
            .getOrDefault(com.assem.copyapp.domain.SessionMode.SOFT),
        defaultDurationMinutes = o.optInt("defaultDurationMinutes", 30),
        strictByDefault = o.optBoolean("strictByDefault", false),
        userExceptions = stringSetFromJson(o.optJSONArray("userExceptions")),
        friendKeyHash = o.optString("friendKeyHash", ""),
        windDownEnabled = o.optBoolean("windDownEnabled", false),
        windDownStartMin = o.optInt("windDownStartMin", 1380),
        bedtimeMin = o.optInt("bedtimeMin", 1410),
        pauseRequiredSeconds = o.optInt("pauseRequiredSeconds", 20),
        notificationsEnabled = o.optBoolean("notificationsEnabled", true),
        digestEnabled = o.optBoolean("digestEnabled", false),
        language = o.optString("language", "system"),
        lastDetectorPackage = o.optString("lastDetectorPackage", "")
    )
}

class AppRepository(
    val db: AppDatabase,
    val settings: SettingsStore,
    val sessionStore: SessionStore
) {

    fun settingsFlow(): Flow<AppSettings> = settings.flow

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    fun sessionFlow(): Flow<SessionState> = sessionStore.flow

    suspend fun startSession(state: SessionState) = sessionStore.start(state)

    suspend fun updateSession(transform: (SessionState) -> SessionState) = sessionStore.update(transform)

    suspend fun clearSession() = sessionStore.clear()

    fun profiles() = db.profileDao().flow()

    fun rules() = db.ruleDao().flow()

    fun detectors(): Flow<List<DetectorConfig>> = db.detectorDao().flow().map { list ->
        list.map {
            DetectorConfig(
                it.id,
                it.packageName,
                it.sectionId,
                runCatching { DetectorType.valueOf(it.type) }.getOrDefault(DetectorType.CLASS_NAME),
                it.markersCsv.split(',').filter { marker -> marker.isNotBlank() }.toSet(),
                it.enabled
            )
        }
    }

    suspend fun saveDetector(d: DetectorConfig) {
        db.detectorDao().upsert(
            DetectorEntity(
                id = d.id,
                packageName = d.packageName,
                sectionId = d.sectionId,
                type = d.type.name,
                markersCsv = d.markers.joinToString(","),
                enabled = d.enabled
            )
        )
    }

    suspend fun deleteDetector(id: Long) = db.detectorDao().delete(id)

    suspend fun saveRule(r: RuleEntity) = db.ruleDao().upsert(r)

    suspend fun deleteRule(id: Long) = db.ruleDao().delete(id)

    suspend fun saveProfile(p: ProfileEntity) = db.profileDao().upsert(p)

    suspend fun deleteProfile(id: Long) = db.profileDao().delete(id)

    suspend fun addUsage(event: UsageEventEntity) = db.usageEventDao().insert(event)

    suspend fun addBreak(b: BreakEntity) = db.breakDao().insert(b)

    suspend fun addSession(s: SessionEntity): Long = db.sessionDao().insert(s)

    suspend fun markSessionCompleted(id: Long, actualMinutes: Int) =
        db.sessionDao().markCompleted(id, actualMinutes)

    suspend fun setDailyLimit(date: String, pkg: String, limit: Int) {
        db.dailyLimitDao().upsert(
            DailyLimitEntity(
                datePackage = datePackageKey(date, pkg),
                date = date,
                packageName = pkg,
                limitMinutes = limit
            )
        )
    }

    suspend fun addUsedMinutes(date: String, pkg: String, minutes: Int) {
        db.dailyLimitDao().addUsed(datePackageKey(date, pkg), minutes)
    }

    fun dailyLimits(): Flow<List<DailyLimitEntity>> = db.dailyLimitDao().flow()

    fun sessions(): Flow<List<SessionEntity>> = db.sessionDao().flow()

    fun usageEvents(): Flow<List<UsageEventEntity>> = db.usageEventDao().flow()

    fun breaks(): Flow<List<BreakEntity>> = db.breakDao().flow()

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("profiles", jsonArrayOf(profiles().first().map { it.toJson() }))
        root.put("rules", jsonArrayOf(rules().first().map { it.toJson() }))
        root.put("detectors", jsonArrayOf(db.detectorDao().flow().first().map { it.toJson() }))
        root.put("limits", jsonArrayOf(dailyLimits().first().map { it.toJson() }))
        root.put("sessions", jsonArrayOf(sessions().first().map { it.toJson() }))
        root.put("events", jsonArrayOf(usageEvents().first().map { it.toJson() }))
        root.put("settings", settings.flow.first().toJson())
        return root.toString()
    }

    suspend fun importJson(json: String): Result<Unit> = runCatching {
        val root = JSONObject(json)

        db.ruleDao().clear()
        db.detectorDao().clear()
        db.dailyLimitDao().clear()

        root.optJSONArray("profiles")?.let { array ->
            for (i in 0 until array.length()) {
                db.profileDao().upsert(profileFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONArray("rules")?.let { array ->
            for (i in 0 until array.length()) {
                db.ruleDao().upsert(ruleFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONArray("detectors")?.let { array ->
            for (i in 0 until array.length()) {
                db.detectorDao().upsert(detectorFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONArray("limits")?.let { array ->
            for (i in 0 until array.length()) {
                db.dailyLimitDao().upsert(limitFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONArray("sessions")?.let { array ->
            for (i in 0 until array.length()) {
                db.sessionDao().insert(sessionFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONArray("events")?.let { array ->
            for (i in 0 until array.length()) {
                db.usageEventDao().insert(eventFromJson(array.getJSONObject(i)))
            }
        }
        root.optJSONObject("settings")?.let { obj -> settings.update { settingsFromJson(obj) } }

        Unit
    }
}
