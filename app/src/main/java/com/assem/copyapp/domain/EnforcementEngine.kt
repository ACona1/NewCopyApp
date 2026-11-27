package com.assem.copyapp.domain

class EnforcementEngine(
    private val clockGuard: ClockGuard,
    private val allowlistProvider: AllowlistProvider,
    private val detectors: DetectorRegistry
) {

    @Volatile
    private var snapshot: EnforcerSnapshot = EnforcerSnapshot()

    fun update(snapshot: EnforcerSnapshot) {
        this.snapshot = snapshot
    }

    fun current(): EnforcerSnapshot = snapshot

    fun decide(pkg: String?, className: String?, nodeInfo: NodeInfoSnapshot?): Decision {
        if (pkg.isNullOrBlank() || pkg == allowlistProvider.ownPackage()) return Decision.Allow
        if (allowlistProvider.isAllowed(pkg, snapshot.userExceptions)) return Decision.Allow

        val sectionId = detectors.detect(pkg, className, nodeInfo)?.first

        val nowWall = snapshot.nowWall
        val nowMin = clockGuard.minutesOfDay(nowWall)
        val rules = RuleEvaluator.activeRules(clockGuard, snapshot.rules, nowWall).toMutableList()
        if (snapshot.windDownEnabled) {
            rules += RuleEvaluator.deriveNightRules(
                snapshot.windDownEnabled,
                snapshot.windDownStartMin,
                snapshot.bedtimeMin,
                nowMin
            )
        }

        if (snapshot.session.active && snapshot.pauseUntilMono > snapshot.elapsedRealtime) return Decision.Allow

        if (snapshot.session.active) {
            if (snapshot.session.strict) return Decision.BlockApp(BlockReason.STRICT, pkg)
            if (pkg in snapshot.session.blocked) return Decision.BlockApp(BlockReason.SESSION_APP, pkg)
            if (sectionId != null && !rules.any { allows(it, pkg, sectionId) }) {
                return Decision.BlockSection(BlockReason.SCHEDULE, sectionId, pkg)
            }
        }

        val applicable = rules
            .filter { it.packageName == pkg || it.packageName == "*" }
            .sortedByDescending { RuleEvaluator.priority(it.action) }

        for (rule in applicable) {
            when (rule.action) {
                RuleAction.BLOCK_APP -> return Decision.BlockApp(blockReasonFor(rule.sectionId), pkg)
                RuleAction.BLOCK_SECTION -> {
                    if (sectionId != null && (rule.sectionId.isEmpty() || rule.sectionId == sectionId)) {
                        return Decision.BlockSection(
                            blockReasonFor(rule.sectionId),
                            rule.sectionId.ifEmpty { sectionId },
                            pkg
                        )
                    }
                }
                RuleAction.GATE -> {
                    val reason = if (rule.sectionId.contains("break", ignoreCase = true)) {
                        GateReason.BREAK
                    } else {
                        GateReason.BUDGET
                    }
                    return Decision.Gate(reason, rule.sectionId, pkg)
                }
                RuleAction.COUNT_ONLY -> {
                    val target = if (rule.sectionId.isNotEmpty()) rule.sectionId else sectionId
                    if (target != null) return Decision.CountOnly(BlockReason.SCHEDULE, target, pkg)
                }
                RuleAction.ALLOW -> return Decision.Allow
            }
        }

        if (BudgetTracker.isExceeded(pkg, snapshot.budgets, snapshot.usedMinutes)) {
            return Decision.Gate(GateReason.BUDGET, packageName = pkg)
        }
        return Decision.Allow
    }

    private fun allows(rule: RuleSnapshot, pkg: String, sectionId: String): Boolean =
        rule.action == RuleAction.ALLOW &&
            (rule.packageName == pkg || rule.packageName == "*") &&
            (rule.sectionId.isEmpty() || rule.sectionId == sectionId)

    private fun blockReasonFor(sectionId: String): BlockReason = when (sectionId) {
        "wind_down" -> BlockReason.WIND_DOWN
        "bedtime" -> BlockReason.BEDTIME
        else -> BlockReason.SCHEDULE
    }
}
