package com.aruler.android.ar

/**
 * Holds the active and completed measurements. The renderer updates the current hit every
 * frame, while the UI thread places, deletes, and clears measurements.
 */
class MeasurementController {

    data class Line(val start: Vec3, val end: Vec3) {
        fun length(): Float = start.distanceTo(end)
    }

    data class Snapshot(
        val completed: List<Line>,
        val active: Line?,
        val hasActive: Boolean,
        val activeLength: Float?,
    )

    private val completed = ArrayList<Line>()
    private val recentHits = ArrayDeque<Vec3>()

    private var activeStart: Vec3? = null
    private var activeEnd: Vec3? = null
    private var rawHit: Vec3? = null

    @Synchronized
    fun update(hit: Vec3?) {
        rawHit = hit
        if (hit == null) return

        recentHits.addLast(hit)
        while (recentHits.size > SMOOTHING_WINDOW) {
            recentHits.removeFirst()
        }

        if (activeStart != null) {
            activeEnd = MeasurementMath.averageOf(recentHits.toList()) ?: hit
        }
    }

    /**
     * Starts a measurement at the current raw hit, or finishes the active measurement.
     */
    @Synchronized
    fun place(): Boolean {
        val start = activeStart
        if (start == null) {
            val point = rawHit ?: return false
            activeStart = point
            activeEnd = point
            recentHits.clear()
            return true
        }

        val end = activeEnd ?: rawHit ?: start
        completed.add(Line(start, end))
        activeStart = null
        activeEnd = null
        recentHits.clear()
        return true
    }

    @Synchronized
    fun delete(): Boolean {
        if (activeStart != null) {
            activeStart = null
            activeEnd = null
            recentHits.clear()
            return true
        }
        if (completed.isNotEmpty()) {
            completed.removeAt(completed.lastIndex)
            return true
        }
        return false
    }

    @Synchronized
    fun clear() {
        completed.clear()
        activeStart = null
        activeEnd = null
        recentHits.clear()
        rawHit = null
    }

    @Synchronized
    fun hasActive(): Boolean = activeStart != null

    @Synchronized
    fun lastRawHit(): Vec3? = rawHit

    @Synchronized
    fun snapshot(): Snapshot {
        val active = activeStart?.let { Line(it, activeEnd ?: it) }
        return Snapshot(
            completed = completed.toList(),
            active = active,
            hasActive = active != null,
            activeLength = active?.length(),
        )
    }

    companion object {
        private const val SMOOTHING_WINDOW = 8
    }
}
