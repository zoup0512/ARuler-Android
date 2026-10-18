package com.aruler.android.ar

import kotlin.math.sqrt

/**
 * Minimal 3D vector used by the measurement pipeline. Values are in ARCore world meters.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    operator fun div(scale: Float) = Vec3(x / scale, y / scale, z / scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-8f) ZERO else this / len
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/**
 * Plane represented by a unit normal and the signed distance from the world origin.
 */
data class Plane(val normal: Vec3, val d: Float)

data class ScreenRay(val origin: Vec3, val direction: Vec3)

data class ScreenPoint(val x: Float, val y: Float)
