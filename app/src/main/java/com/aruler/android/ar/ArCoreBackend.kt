package com.aruler.android.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.PointCloud
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.core.TrackingState as CoreTrackingState
import java.nio.FloatBuffer

class ArCoreBackend : TrackingBackend {

    override val name: String = "ARCore"

    override fun prepare(activity: Activity, userRequestedInstall: Boolean): BackendPrepareResult {
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> BackendPrepareResult.INSTALL_REQUESTED
                ArCoreApk.InstallStatus.INSTALLED -> BackendPrepareResult.READY
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            BackendPrepareResult.DECLINED
        } catch (_: Exception) {
            BackendPrepareResult.UNSUPPORTED
        }
    }

    override fun createSession(context: Context): TrackingSession {
        val session = Session(context)
        try {
            session.configure(
                Config(session).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                },
            )
            return ArCoreTrackingSession(session)
        } catch (error: Throwable) {
            runCatching { session.close() }
            throw error
        }
    }
}

private class ArCoreTrackingSession(
    private val session: Session,
) : TrackingSession {

    override val backendName: String = "ARCore"

    override fun setCameraTextureName(textureId: Int) {
        session.setCameraTextureName(textureId)
    }

    override fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        session.setDisplayGeometry(rotation, width, height)
    }

    override fun update(): TrackingFrame? {
        val frame = session.update()
        return ArCoreTrackingFrame(frame, session)
    }

    override fun resume() {
        session.resume()
    }

    override fun pause() {
        session.pause()
    }

    override fun close() {
        session.close()
    }
}

private class ArCoreTrackingFrame(
    private val frame: Frame,
    private val session: Session,
) : TrackingFrame {

    private val camera: Camera = frame.camera
    private var rotationQuaternion: FloatArray? = null

    override val cameraTranslation: Vec3
        get() {
            val translation = camera.pose.translation
            return Vec3(translation[0], translation[1], translation[2])
        }

    override val cameraRotationQuaternion: FloatArray
        get() = rotationQuaternion ?: FloatArray(4).also { quaternion ->
            camera.pose.getRotationQuaternion(quaternion, 0)
            rotationQuaternion = quaternion
        }

    override val trackingState: TrackingState
        get() = when (camera.trackingState) {
            CoreTrackingState.TRACKING -> TrackingState.TRACKING
            CoreTrackingState.PAUSED -> TrackingState.PAUSED
            CoreTrackingState.STOPPED -> TrackingState.STOPPED
        }

    override val trackingIssue: TrackingIssue
        get() {
            if (camera.trackingState == CoreTrackingState.TRACKING) return TrackingIssue.NONE

            val reason = runCatching { camera.trackingFailureReason }.getOrNull()
            return when (reason) {
                TrackingFailureReason.BAD_STATE -> TrackingIssue.BAD_STATE
                TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingIssue.INSUFFICIENT_LIGHT
                TrackingFailureReason.EXCESSIVE_MOTION -> TrackingIssue.EXCESSIVE_MOTION
                TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingIssue.INSUFFICIENT_FEATURES
                TrackingFailureReason.CAMERA_UNAVAILABLE -> TrackingIssue.CAMERA_UNAVAILABLE
                else -> TrackingIssue.INITIALIZING
            }
        }

    override fun getProjectionMatrix(out: FloatArray, near: Float, far: Float) {
        camera.getProjectionMatrix(out, 0, near, far)
    }

    override fun getViewMatrix(out: FloatArray) {
        camera.getViewMatrix(out, 0)
    }

    override fun hitTestHorizontalPlane(screenX: Float, screenY: Float): Vec3? {
        for (hit in frame.hitTest(screenX, screenY)) {
            val plane = hit.trackable as? Plane ?: continue
            if (plane.trackingState != CoreTrackingState.TRACKING) continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING &&
                plane.type != Plane.Type.HORIZONTAL_DOWNWARD_FACING
            ) {
                continue
            }

            val translation = hit.hitPose.translation
            return Vec3(translation[0], translation[1], translation[2])
        }
        return null
    }

    override fun trackedPlanes(): List<PlaneInfo> {
        val result = ArrayList<PlaneInfo>()
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != CoreTrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue
            if (plane.extentX <= 0f || plane.extentZ <= 0f) continue

            val quaternion = FloatArray(4)
            plane.centerPose.getRotationQuaternion(quaternion, 0)
            val translation = plane.centerPose.translation
            result.add(
                PlaneInfo(
                    translation = Vec3(translation[0], translation[1], translation[2]),
                    rotationQuaternion = quaternion,
                    extentX = plane.extentX,
                    extentZ = plane.extentZ,
                ),
            )
        }
        return result
    }

    override fun copyFeaturePoints(out: FloatArray, maxPoints: Int): Int {
        var pointCloud: PointCloud? = null
        return try {
            pointCloud = frame.acquirePointCloud()
            val buffer = pointCloud.points
            val start = buffer.position()
            val total = buffer.remaining() / 4
            var count = 0
            var outIndex = 0
            for (index in 0 until total) {
                if (count >= maxPoints || outIndex + 2 >= out.size) break
                val base = start + index * 4
                if (buffer.get(base + 3) <= 0f) continue
                out[outIndex++] = buffer.get(base)
                out[outIndex++] = buffer.get(base + 1)
                out[outIndex++] = buffer.get(base + 2)
                count++
            }
            count
        } catch (_: Exception) {
            0
        } finally {
            pointCloud?.release()
        }
    }

    override fun updateBackgroundTextureCoords(outUv: FloatBuffer) {
        quadCoords.position(0)
        outUv.position(0)
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            outUv,
        )
    }

    private companion object {
        // Matches BackgroundRenderer's full-screen quad order:
        // bottom-left, bottom-right, top-left, top-right.
        val quadCoords: FloatBuffer = GlUtil.createFloatBuffer(
            floatArrayOf(
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f,
            ),
        )
    }
}
