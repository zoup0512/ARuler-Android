package com.aruler.android.ar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Geometry helpers shared by the AR hit-testing pipeline. All distances are in meters.
 */
object MeasurementMath {

    const val FEATURE_CONE_ANGLE_DEGREES = 5f
    const val FEATURE_MIN_DISTANCE = 0.1f
    const val FEATURE_MAX_DISTANCE = 50f

    private const val PLANE_INTERSECTION_LIMIT = 5f

    fun averageOf(points: List<Vec3>): Vec3? {
        if (points.isEmpty()) return null
        var x = 0f
        var y = 0f
        var z = 0f
        for (point in points) {
            x += point.x
            y += point.y
            z += point.z
        }
        val count = points.size.toFloat()
        return Vec3(x / count, y / count, z / count)
    }

    /**
     * Removes points farther than three standard deviations from the centroid.
     * The distance is measured in centimeters to mirror the reference implementation.
     */
    fun filterOutliers(points: List<Vec3>): List<Vec3> {
        if (points.size < 3) return points
        val centroid = averageOf(points) ?: return points

        var squaredSum = 0.0
        for (point in points) {
            val distanceCm = point.distanceTo(centroid) * 100.0
            squaredSum += distanceCm * distanceCm
        }
        val sigma = sqrt(squaredSum / (points.size - 1))
        if (sigma < 1e-6) return points

        val threshold = 3.0 * sigma
        return points.filter { point -> point.distanceTo(centroid) * 100.0 < threshold }
    }

    /**
     * Least-squares plane fit: the normal is the eigenvector of the covariance matrix
     * with the smallest eigenvalue.
     */
    fun fitPlane(points: List<Vec3>): Plane? {
        if (points.size < 3) return null
        val centroid = averageOf(points) ?: return null

        var xx = 0.0
        var xy = 0.0
        var xz = 0.0
        var yy = 0.0
        var yz = 0.0
        var zz = 0.0
        for (point in points) {
            val dx = (point.x - centroid.x).toDouble()
            val dy = (point.y - centroid.y).toDouble()
            val dz = (point.z - centroid.z).toDouble()
            xx += dx * dx
            xy += dx * dy
            xz += dx * dz
            yy += dy * dy
            yz += dy * dz
            zz += dz * dz
        }

        val covariance = arrayOf(
            doubleArrayOf(xx, xy, xz),
            doubleArrayOf(xy, yy, yz),
            doubleArrayOf(xz, yz, zz),
        )
        val (eigenvalues, eigenvectors) = eigenSymmetric3(covariance)

        val sortedIndices = eigenvalues.indices.sortedBy { eigenvalues[it] }
        val smallest = sortedIndices[0]
        val middle = sortedIndices[1]
        if (eigenvalues[middle] < 1e-12) return null

        val normal = Vec3(
            eigenvectors[0][smallest].toFloat(),
            eigenvectors[1][smallest].toFloat(),
            eigenvectors[2][smallest].toFloat(),
        ).normalized()
        if (normal.length() < 0.5f) return null

        return Plane(normal, normal.dot(centroid))
    }

    fun rayPlaneIntersection(
        plane: Plane,
        rayOrigin: Vec3,
        rayDirection: Vec3,
        maxDistance: Float = PLANE_INTERSECTION_LIMIT,
    ): Vec3? {
        val denominator = plane.normal.dot(rayDirection)
        if (abs(denominator) < 1e-6f) return null

        val t = (plane.d - plane.normal.dot(rayOrigin)) / denominator
        if (t <= 0f || t >= maxDistance) return null
        return rayOrigin + rayDirection * t
    }

    /**
     * Intersects a ray with the horizontal plane at [planeY]. Only downward rays hit,
     * matching the fallback used by the original iOS implementation.
     */
    fun infinitePlaneIntersection(rayOrigin: Vec3, rayDirection: Vec3, planeY: Float): Vec3? {
        if (rayDirection.y > -0.03f) return null
        val t = (planeY - rayOrigin.y) / rayDirection.y
        if (t <= 0f) return null
        return rayOrigin + rayDirection * t
    }

    /**
     * Collects feature points that fall inside the cone around the camera ray.
     * [points] is a tightly packed xyz array.
     */
    fun coneFeaturePoints(
        points: FloatArray,
        count: Int,
        rayOrigin: Vec3,
        rayDirection: Vec3,
        coneAngleDegrees: Float = FEATURE_CONE_ANGLE_DEGREES,
        minDistance: Float = FEATURE_MIN_DISTANCE,
        maxDistance: Float = FEATURE_MAX_DISTANCE,
    ): List<Vec3> {
        if (count <= 0) return emptyList()
        val direction = rayDirection.normalized()
        val halfAngleRadians = Math.toRadians((coneAngleDegrees / 2f).toDouble())
        val cosLimit = cos(halfAngleRadians).toFloat()
        val results = ArrayList<Vec3>(minOf(count, 256))

        for (index in 0 until count) {
            val offset = index * 3
            val point = Vec3(points[offset], points[offset + 1], points[offset + 2])
            val delta = point - rayOrigin
            val alongRay = delta.dot(direction)
            if (alongRay < minDistance || alongRay > maxDistance) continue

            val distance = delta.length()
            if (distance < 1e-6f) continue
            if (alongRay / distance < cosLimit) continue

            results.add(point)
        }
        return results
    }

    /**
     * Projects a world point through a column-major view-projection matrix.
     */
    fun project(point: Vec3, mvp: FloatArray, width: Int, height: Int): ScreenPoint? {
        val clipX = mvp[0] * point.x + mvp[4] * point.y + mvp[8] * point.z + mvp[12]
        val clipY = mvp[1] * point.x + mvp[5] * point.y + mvp[9] * point.z + mvp[13]
        val clipZ = mvp[2] * point.x + mvp[6] * point.y + mvp[10] * point.z + mvp[14]
        val clipW = mvp[3] * point.x + mvp[7] * point.y + mvp[11] * point.z + mvp[15]
        if (clipW <= 1e-4f) return null

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        val ndcZ = clipZ / clipW
        if (ndcZ < -1f || ndcZ > 1f) return null

        return ScreenPoint(
            (ndcX * 0.5f + 0.5f) * width,
            (1f - (ndcY * 0.5f + 0.5f)) * height,
        )
    }

    /**
     * Multiplies two column-major 4x4 matrices.
     */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[column * 4 + k]
                }
                out[column * 4 + row] = sum
            }
        }
        return out
    }

    /**
     * Rotates a vector by an ARCore quaternion laid out as [x, y, z, w].
     */
    fun rotate(quaternion: FloatArray, vector: Vec3): Vec3 {
        val qx = quaternion[0]
        val qy = quaternion[1]
        val qz = quaternion[2]
        val qw = quaternion[3]

        val tx = 2f * (qy * vector.z - qz * vector.y)
        val ty = 2f * (qz * vector.x - qx * vector.z)
        val tz = 2f * (qx * vector.y - qy * vector.x)

        return Vec3(
            vector.x + qw * tx + (qy * tz - qz * ty),
            vector.y + qw * ty + (qz * tx - qx * tz),
            vector.z + qw * tz + (qx * ty - qy * tx),
        )
    }

    /**
     * Cyclic Jacobi eigen decomposition for a symmetric 3x3 matrix.
     * Eigenvectors are returned as columns.
     */
    internal fun eigenSymmetric3(matrix: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val a = Array(3) { matrix[it].copyOf() }
        val vectors = Array(3) { row -> DoubleArray(3) { column -> if (row == column) 1.0 else 0.0 } }

        for (iteration in 0 until 64) {
            val offDiagonal = a[0][1] * a[0][1] + a[0][2] * a[0][2] + a[1][2] * a[1][2]
            if (offDiagonal < 1e-24) break

            for (p in 0 until 2) {
                for (q in p + 1 until 3) {
                    val apq = a[p][q]
                    if (abs(apq) < 1e-18) continue

                    val app = a[p][p]
                    val aqq = a[q][q]
                    val theta = (aqq - app) / (2.0 * apq)
                    val t = if (theta >= 0.0) {
                        1.0 / (theta + sqrt(theta * theta + 1.0))
                    } else {
                        -1.0 / (-theta + sqrt(theta * theta + 1.0))
                    }
                    val c = 1.0 / sqrt(t * t + 1.0)
                    val s = t * c

                    a[p][p] = app - t * apq
                    a[q][q] = aqq + t * apq
                    a[p][q] = 0.0
                    a[q][p] = 0.0

                    for (r in 0 until 3) {
                        if (r != p && r != q) {
                            val arp = a[r][p]
                            val arq = a[r][q]
                            a[r][p] = c * arp - s * arq
                            a[p][r] = a[r][p]
                            a[r][q] = s * arp + c * arq
                            a[q][r] = a[r][q]
                        }
                    }

                    for (r in 0 until 3) {
                        val vrp = vectors[r][p]
                        val vrq = vectors[r][q]
                        vectors[r][p] = c * vrp - s * vrq
                        vectors[r][q] = s * vrp + c * vrq
                    }
                }
            }
        }

        return doubleArrayOf(a[0][0], a[1][1], a[2][2]) to vectors
    }
}
