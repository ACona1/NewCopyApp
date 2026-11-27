package com.assem.copyapp.domain

sealed interface Decision {
    data object Allow : Decision

    data class BlockApp(val reason: BlockReason, val packageName: String) : Decision

    data class BlockSection(val reason: BlockReason, val sectionId: String, val packageName: String) : Decision

    data class Gate(val reason: GateReason, val sectionId: String = "", val packageName: String = "") : Decision

    data class CountOnly(val reason: BlockReason, val sectionId: String, val packageName: String) : Decision
}
