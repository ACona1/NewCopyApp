package com.assem.copyapp.domain

data class DetectorConfig(
    val id: Long,
    val packageName: String,
    val sectionId: String,
    val type: DetectorType,
    val markers: Set<String>,
    val enabled: Boolean = true
)

enum class DetectorType { CLASS_NAME, VIEW_ID, TEXT, CONTENT_DESC }

class DetectorRegistry {

    @Volatile
    private var detectors: List<DetectorConfig> = emptyList()

    fun update(list: List<DetectorConfig>) {
        detectors = list
    }

    fun detect(pkg: String, className: String?, node: NodeInfoSnapshot?): Pair<String, DetectorType>? {
        if (pkg.isBlank() || node == null) return null
        for (detector in detectors) {
            if (!detector.enabled || !detector.packageName.equals(pkg, ignoreCase = true)) continue
            val matched = when (detector.type) {
                DetectorType.CLASS_NAME ->
                    className?.matchesAny(detector.markers) == true || node.className?.matchesAny(detector.markers) == true
                DetectorType.VIEW_ID -> node.viewIdResourceName?.matchesAny(detector.markers) == true
                DetectorType.TEXT ->
                    node.text?.matchesAny(detector.markers) == true || node.childTexts.any { it.matchesAny(detector.markers) }
                DetectorType.CONTENT_DESC -> node.contentDescription?.matchesAny(detector.markers) == true
            }
            if (matched) return detector.sectionId to detector.type
        }
        return null
    }

    private fun String.matchesAny(markers: Set<String>): Boolean =
        markers.any { marker -> marker.isNotEmpty() && contains(marker, ignoreCase = true) }
}
