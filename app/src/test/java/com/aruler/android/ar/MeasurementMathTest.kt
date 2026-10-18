package com.aruler.android.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MeasurementMathTest {

    @Test
    fun fitHorizontalPlaneAndIntersectRay() {
        val points = ArrayList<Vec3>()
        for (x in -5..5) {
            for (z in -5..5) {
                points.add(Vec3(x * 0.02f, 2f, z * 0.02f))
            }
        }

        val plane = MeasurementMath.fitPlane(points)
        assertNotNull(plane)
        assertEquals(1f, abs(plane!!.normal.y), 0.001f)

        val intersection = MeasurementMath.rayPlaneIntersection(
            plane,
            Vec3(0.02f, 0f, 0.01f),
            Vec3(0f, 1f, 0f),
        )
        assertNotNull(intersection)
        assertEquals(2f, intersection!!.y, 0.001f)
    }

    @Test
    fun fitVerticalPlaneAndIntersectRay() {
        val points = ArrayList<Vec3>()
        for (y in -5..5) {
            for (z in -5..5) {
                points.add(Vec3(1f, y * 0.02f, z * 0.02f))
            }
        }

        val plane = MeasurementMath.fitPlane(points)
        assertNotNull(plane)
        assertEquals(1f, abs(plane!!.normal.x), 0.001f)

        val intersection = MeasurementMath.rayPlaneIntersection(
            plane,
            Vec3.ZERO,
            Vec3(1f, 0f, 0f),
        )
        assertNotNull(intersection)
        assertEquals(1f, intersection!!.x, 0.001f)
    }

    @Test
    fun rayPlaneIntersectionRejectsDistantHit() {
        val plane = Plane(Vec3(0f, 0f, 1f), 10f)
        assertNull(
            MeasurementMath.rayPlaneIntersection(
                plane,
                Vec3.ZERO,
                Vec3(0f, 0f, 1f),
            ),
        )
    }

    @Test
    fun filterRemovesFarOutlier() {
        val points = (0 until 21).map { Vec3(it * 0.01f, 0f, 2f) }.toMutableList()
        val outlier = Vec3(10f, 10f, 10f)
        points.add(outlier)

        val filtered = MeasurementMath.filterOutliers(points)
        assertFalse(filtered.contains(outlier))
        assertTrue(filtered.size >= 3)
    }

    @Test
    fun coneKeepsOnlyPointsInsideAngleAndRange() {
        val cloud = floatArrayOf(
            0f, 0f, 2f,
            0f, 0.2f, 2f,
            0f, 0f, 0.05f,
            0f, 0f, -2f,
        )

        val result = MeasurementMath.coneFeaturePoints(
            cloud,
            4,
            Vec3.ZERO,
            Vec3(0f, 0f, 1f),
        )

        assertEquals(1, result.size)
        assertEquals(2f, result[0].z, 0.001f)
    }

    @Test
    fun infinitePlaneRequiresDownwardRay() {
        assertNull(
            MeasurementMath.infinitePlaneIntersection(
                Vec3(0f, 1f, 0f),
                Vec3(0f, 0f, 1f),
                0f,
            ),
        )

        val hit = MeasurementMath.infinitePlaneIntersection(
            Vec3(0f, 1f, 0f),
            Vec3(0f, -1f, 1f).normalized(),
            0f,
        )
        assertNotNull(hit)
        assertEquals(0f, hit!!.y, 0.0001f)
    }

    @Test
    fun projectIdentityMatrixMapsToScreenCenter() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val point = MeasurementMath.project(Vec3.ZERO, identity, 200, 100)
        assertNotNull(point)
        assertEquals(100f, point!!.x, 0.001f)
        assertEquals(50f, point.y, 0.001f)
    }

    @Test
    fun multiplyByIdentityKeepsMatrix() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val matrix = floatArrayOf(
            1f, 2f, 3f, 4f,
            5f, 6f, 7f, 8f,
            9f, 10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )

        val result = MeasurementMath.multiply(identity, matrix)
        assertArrayEquals(matrix, result, 0.0001f)
    }

    @Test
    fun rotateUsesArCoreQuaternionLayout() {
        val identity = floatArrayOf(0f, 0f, 0f, 1f)
        val unchanged = MeasurementMath.rotate(identity, Vec3(1f, 2f, 3f))
        assertEquals(1f, unchanged.x, 0.0001f)
        assertEquals(2f, unchanged.y, 0.0001f)
        assertEquals(3f, unchanged.z, 0.0001f)

        val halfAngle = Math.PI.toFloat() / 4f
        val rotate90AboutY = floatArrayOf(0f, sin(halfAngle), 0f, cos(halfAngle))
        val rotated = MeasurementMath.rotate(rotate90AboutY, Vec3(1f, 0f, 0f))
        assertEquals(0f, rotated.x, 0.001f)
        assertEquals(0f, rotated.y, 0.001f)
        assertEquals(-1f, rotated.z, 0.001f)
    }

    @Test
    fun jacobiEigenDecompositionSatisfiesDefinition() {
        val matrix = arrayOf(
            doubleArrayOf(2.0, 1.0, 0.0),
            doubleArrayOf(1.0, 2.0, 0.0),
            doubleArrayOf(0.0, 0.0, 5.0),
        )
        val (values, vectors) = MeasurementMath.eigenSymmetric3(matrix)

        for (column in 0 until 3) {
            for (row in 0 until 3) {
                var actual = 0.0
                for (k in 0 until 3) {
                    actual += matrix[row][k] * vectors[k][column]
                }
                val expected = values[column] * vectors[row][column]
                assertTrue(abs(actual - expected) < 1e-8)
            }
        }
    }

    @Test
    fun collinearPointsDoNotProduceAPlane() {
        val points = (0 until 10).map { Vec3(it * 0.01f, 0f, 0f) }
        assertNull(MeasurementMath.fitPlane(points))
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            assertEquals(expected[index], actual[index], delta)
        }
    }
}
