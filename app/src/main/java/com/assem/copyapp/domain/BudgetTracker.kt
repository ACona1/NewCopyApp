package com.assem.copyapp.domain

object BudgetTracker {

    fun isExceeded(pkg: String, budgets: Map<String, Int>, used: Map<String, Int>): Boolean {
        val limit = budgets[pkg] ?: return false
        if (limit <= 0) return false
        return (used[pkg] ?: 0) >= limit
    }

    fun remaining(pkg: String, budgets: Map<String, Int>, used: Map<String, Int>): Int {
        val limit = budgets[pkg] ?: return -1
        if (limit <= 0) return -1
        return (limit - (used[pkg] ?: 0)).coerceAtLeast(0)
    }
}
