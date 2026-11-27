package com.assem.copyapp.domain

enum class SessionMode { SOFT, HARD }

enum class BlockReason { SESSION_APP, STRICT, BUDGET, SCHEDULE, WIND_DOWN, BEDTIME }

enum class GateReason { PAUSE, BREAK, FRIEND_KEY, BUDGET, EMERGENCY }

enum class SectionKind { REELS, SHORTS, FEED, SEARCH, UNKNOWN }

enum class RuleAction { BLOCK_APP, BLOCK_SECTION, GATE, COUNT_ONLY, ALLOW }

data class NodeInfoSnapshot(
    val packageName: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val childTexts: List<String> = emptyList()
)

data class SessionSnapshot(
    val active: Boolean = false,
    val startWall: Long = 0L,
    val endWall: Long = 0L,
    val startMono: Long = 0L,
    val mode: SessionMode = SessionMode.SOFT,
    val blocked: Set<String> = emptySet(),
    val strict: Boolean = false,
    val breaks: Int = 0
)

data class RuleSnapshot(
    val packageName: String,
    val sectionId: String = "",
    val action: RuleAction = RuleAction.BLOCK_APP,
    val startMin: Int = -1,
    val endMin: Int = -1,
    val daysMask: Int = 0x7F,
    val enabled: Boolean = true
)

data class EnforcerSnapshot(
    val session: SessionSnapshot = SessionSnapshot(),
    val allowlist: Set<String> = emptySet(),
    val userExceptions: Set<String> = emptySet(),
    val rules: List<RuleSnapshot> = emptyList(),
    val budgets: Map<String, Int> = emptyMap(),
    val usedMinutes: Map<String, Int> = emptyMap(),
    val pauseUntilMono: Long = 0L,
    val elapsedRealtime: Long = 0L,
    val nowWall: Long = 0L,
    val windDownEnabled: Boolean = false,
    val windDownStartMin: Int = -1,
    val bedtimeMin: Int = -1
)
