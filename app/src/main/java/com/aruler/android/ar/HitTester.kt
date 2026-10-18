package com.aruler.android.ar

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlin.math.abs

/**
 * Finds a world-space point under the center of the screen.
 *
 * The order mirrors the iOS ARuler implementation:
 * 1. hit an existing horizontal plane,
 * 2. fit a plane through nearby feature points and intersect the camera ray,
 * 3. fall back to a horizontal plane at the last known height,
 * 4. give up.
 */
class HitTester {

    data class WorldHit(val position: Vec3, val fromPlane: Boolean)

    fun hitTest(
        frame: Frame,
        viewportWidth: Int,
        viewportHeight: Int,
        pointCloud: FloatArray,
        pointCount: Int,
        objectPlaneY: Float? = null,
    ): WorldHit? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null

        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f

        for (hit in frame.hitTest(centerX, centerY)) {
            val trackable = hit.trackable
            if (trackable !is Plane) continue
            if (trackable.trackingState != TrackingState.TRACKING) continue

            val type = trackable.type
            if (type != Plane.Type.HORIZONTAL_UPWARD_FACING &&
                type != Plane.Type.HORIZONTAL_DOWNWARD_FACING
            ) {
                continue
            }

            val translation = hit.hitPose.translation
            return WorldHit(
                Vec3(translation[0], translation[1], translation[2]),
                fromPlane = true,
            )
        }

        val ray = screenRay(
            frame.camera,
            centerX,
            centerY,
            viewportWidth,
            viewportHeight,
        ) ?: return null

        val featurePoints = MeasurementMath.coneFeaturePoints(
            pointCloud,
            pointCount,
            ray.origin,
            ray.direction,
        )

        if (featurePoints.isNotEmpty()) {
            val filtered = MeasurementMath.filterOutliers(featurePoints)

            if (filtered.size >= 3) {
                val plane = MeasurementMath.fitPlane(filtered)
                if (plane != null) {
                    val intersection = MeasurementMath.rayPlaneIntersection(
                        plane,
                        ray.origin,
                        ray.direction,
                    )
                    if (intersection != null) {
                        return WorldHit(intersection, fromPlane = false)
                    }
                }
            }

            MeasurementMath.averageOf(filtered)?.let { average ->
                return WorldHit(average, fromPlane = false)
            }
        }

        val planeY = objectPlaneY ?: 0f
        MeasurementMath.infinitePlaneIntersection(ray.origin, ray.direction, planeY)?.let { point ->
            return WorldHit(point, fromPlane = false)
        }

        return null
    }

    private fun screenRay(
        camera: Camera,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
    ): ScreenRay? {
        val projection = FloatArray(16)
        camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
        if (abs(projection[0]) < 1e-6f || abs(projection[5]) < 1e-6f) return null

        val ndcX = 2f * screenX / viewportWidth - 1f
        val ndcY = 1f - 2f * screenY / viewportHeight
        val directionCamera = Vec3(
            ndcX / projection[0],
            ndcY / projection[5],
            -1f,
        )

        val quaternion = FloatArray(4)
        camera.pose.getRotationQuaternion(quaternion, 0)
        val direction = MeasurementMath.rotate(quaternion, directionCamera).normalized()

        val translation = camera.pose.translation
        val origin = Vec3(translation[0], translation[1], translation[2])
        return ScreenRay(origin, direction)
    }
}
