package com.aruler.android.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementControllerTest {

    @Test
    fun cannotPlaceWithoutAHit() {
        val controller = MeasurementController()
        assertFalse(controller.place())
        assertFalse(controller.hasActive())
    }

    @Test
    fun placeStartAndEndCreatesACompletedLine() {
        val controller = MeasurementController()
        val start = Vec3(0f, 0f, 2f)
        controller.update(start)
        assertTrue(controller.place())
        assertTrue(controller.hasActive())

        repeat(8) { controller.update(Vec3(0.1f, 0f, 2.1f)) }
        assertTrue(controller.place())
        assertFalse(controller.hasActive())

        val snapshot = controller.snapshot()
        assertEquals(1, snapshot.completed.size)
        assertEquals(0.1f, snapshot.completed[0].end.x, 0.0001f)
        assertEquals(2.1f, snapshot.completed[0].end.z, 0.0001f)
        assertEquals(0f, snapshot.completed[0].start.x, 0.0001f)
    }

    @Test
    fun invalidFrameKeepsTheLastActiveEndpoint() {
        val controller = MeasurementController()
        controller.update(Vec3(0f, 0f, 2f))
        controller.place()
        controller.update(Vec3(0f, 0f, 2.2f))
        controller.update(null)

        val snapshot = controller.snapshot()
        assertEquals(2.2f, snapshot.active!!.end.z, 0.0001f)
    }

    @Test
    fun deletePrefersActiveLineThenLastCompletedLine() {
        val controller = MeasurementController()
        controller.update(Vec3(0f, 0f, 2f))
        controller.place()
        controller.update(Vec3(0f, 0f, 3f))
        controller.place()
        assertEquals(1, controller.snapshot().completed.size)

        controller.update(Vec3(0f, 0f, 2f))
        controller.place()
        assertTrue(controller.hasActive())
        assertTrue(controller.delete())
        assertFalse(controller.hasActive())
        assertEquals(1, controller.snapshot().completed.size)

        assertTrue(controller.delete())
        assertEquals(0, controller.snapshot().completed.size)
        assertFalse(controller.delete())
    }

    @Test
    fun clearRemovesEverything() {
        val controller = MeasurementController()
        controller.update(Vec3(0f, 0f, 2f))
        controller.place()
        controller.update(Vec3(0f, 0f, 3f))
        controller.place()
        controller.update(Vec3(1f, 1f, 3f))
        controller.place()

        controller.clear()

        val snapshot = controller.snapshot()
        assertNull(snapshot.active)
        assertTrue(snapshot.completed.isEmpty())
        assertNull(controller.lastRawHit())
    }
}
