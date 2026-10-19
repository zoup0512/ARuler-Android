package com.aruler.android.ar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.huawei.hiar.ARConfigBase
import com.huawei.hiar.AREnginesApk
import com.huawei.hiar.ARFrame
import com.huawei.hiar.ARPlane
import com.huawei.hiar.ARPointCloud
import com.huawei.hiar.ARSession
import com.huawei.hiar.ARWorldTrackingConfig
import com.huawei.hiar.ARTrackable.TrackingState as EngineTrackingState
import java.nio.FloatBuffer

/**
 * Huawei devices that are not ARCore certified run Huawei's own AR Engine instead.
 * The public AR Engine SDK is API-compatible enough with ARCore that the renderer,
 * measurement math and UI can be shared.
 */
class ArEngineBackend : TrackingBackend {

    override val name: String = "Huawei AR Engine"

    override fun prepare(activity: Activity, userRequestedInstall: Boolean): BackendPrepareResult {
        return try {
            if (AREnginesApk.isAREngineApkReady(activity)) {
                BackendPrepareResult.READY
            } else if (userRequestedInstall && launchAppGallery(activity)) {
                BackendPrepareResult.INSTALL_REQUESTED
            } else {
                BackendPrepareResult.DECLINED
            }
        } catch (_: Exception) {
            BackendPrepareResult.UNSUPPORTED
        }
    }

    override fun createSession(context: Context): TrackingSession {
        val session = ARSession(context)
        try {
            val config = ARWorldTrackingConfig(session).apply {
                setPlaneFindingMode(ARConfigBase.PlaneFindingMode.HORIZONTAL_ONLY)
                setUpdateMode(ARConfigBase.UpdateMode.LATEST_CAMERA_IMAGE)
                setFocusMode(ARConfigBase.FocusMode.AUTO_FOCUS)
                // No lighting mode: old AR Engine services (3.x) throw
                // ARUnavailableServiceApkTooOldException, and this app does not use lighting.
            }
            session.configure(config)
            return ArEngineTrackingSession(session)
        } catch (error: Throwable) {
            runCatching { session.stop() }
            throw error
        }
    }

    private fun launchAppGallery(activity: Activity): Boolean {
        val detailIntent = Intent(ACTION_APP_DETAIL).apply {
            putExtra(EXTRA_PACKAGE_NAME, AR_ENGINE_PACKAGE)
            setPackage(HUAWEI_APP_MARKET)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            activity.startActivity(detailIntent)
            true
        } catch (_: Exception) {
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$AR_ENGINE_PACKAGE"),
                ).setPackage(HUAWEI_APP_MARKET)
                activity.startActivity(marketIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private companion object {
        const val HUAWEI_APP_MARKET = "com.huawei.appmarket"
        const val AR_ENGINE_PACKAGE = "com.huawei.arengine.service"
        const val ACTION_APP_DETAIL = "com.huawei.appmarket.intent.action.AppDetail"
        const val EXTRA_PACKAGE_NAME = "APP_PACKAGENAME"
    }
}

private class ArEngineTrackingSession(
    private val session: ARSession,
) : TrackingSession {

    override val backendName: String = "Huawei AR Engine"

    override fun setCameraTextureName(textureId: Int) {
        session.setCameraTextureName(textureId)
    }

    override fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        session.setDisplayGeometry(rotation, width, height)
    }

    override fun update(): TrackingFrame? {
        val frame = session.update() ?: return null
        return ArEngineTrackingFrame(frame, session)
    }

    override fun resume() {
        session.resume()
    }

    override fun pause() {
        session.pause()
    }

    override fun close() {
        runCatching { session.pause() }
        runCatching { session.stop() }
    }
}

private class ArEngineTrackingFrame(
    private val frame: ARFrame,
    private val session: ARSession,
) : TrackingFrame {

    private var rotationQuaternion: FloatArray? = null

    override val cameraTranslation: Vec3
        get() {
            val pose = frame.camera.pose
            return Vec3(pose.tx(), pose.ty(), pose.tz())
        }

    override val cameraRotationQuaternion: FloatArray
        get() = rotationQuaternion ?: FloatArray(4).also { quaternion ->
            frame.camera.pose.getRotationQuaternion(quaternion, 0)
            rotationQuaternion = quaternion
        }

    override val trackingState: TrackingState
        get() = when (frame.camera.trackingState) {
            EngineTrackingState.TRACKING -> TrackingState.TRACKING
            EngineTrackingState.PAUSED -> TrackingState.PAUSED
            EngineTrackingState.STOPPED -> TrackingState.STOPPED
            else -> TrackingState.UNKNOWN
        }

    override val trackingIssue: TrackingIssue
        get() = when (trackingState) {
            TrackingState.TRACKING -> TrackingIssue.NONE
            TrackingState.STOPPED -> TrackingIssue.BAD_STATE
            TrackingState.PAUSED, TrackingState.UNKNOWN -> TrackingIssue.INITIALIZING
        }

    override fun getProjectionMatrix(out: FloatArray, near: Float, far: Float) {
        frame.camera.getProjectionMatrix(out, 0, near, far)
    }

    override fun getViewMatrix(out: FloatArray) {
        frame.camera.getViewMatrix(out, 0)
    }

    override fun hitTestHorizontalPlane(screenX: Float, screenY: Float): Vec3? {
        for (hit in frame.hitTest(screenX, screenY)) {
            val plane = hit.trackable as? ARPlane ?: continue
            if (plane.trackingState != EngineTrackingState.TRACKING) continue
            if (plane.type != ARPlane.PlaneType.HORIZONTAL_UPWARD_FACING &&
                plane.type != ARPlane.PlaneType.HORIZONTAL_DOWNWARD_FACING
            ) {
                continue
            }
            if (!plane.isPoseInPolygon(hit.hitPose)) continue

            val pose = hit.hitPose
            return Vec3(pose.tx(), pose.ty(), pose.tz())
        }
        return null
    }

    override fun trackedPlanes(): List<PlaneInfo> {
        val planes = session.getAllTrackables(ARPlane::class.java)
        val result = ArrayList<PlaneInfo>(planes.size)
        for (plane in planes) {
            if (plane.trackingState != EngineTrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue
            if (plane.extentX <= 0f || plane.extentZ <= 0f) continue

            val quaternion = FloatArray(4)
            val centerPose = plane.centerPose
            centerPose.getRotationQuaternion(quaternion, 0)
            result.add(
                PlaneInfo(
                    translation = Vec3(centerPose.tx(), centerPose.ty(), centerPose.tz()),
                    rotationQuaternion = quaternion,
                    extentX = plane.extentX,
                    extentZ = plane.extentZ,
                ),
            )
        }
        return result
    }

    override fun copyFeaturePoints(out: FloatArray, maxPoints: Int): Int {
        var pointCloud: ARPointCloud? = null
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
        displayUvCoords.position(0)
        outUv.position(0)
        frame.transformDisplayUvCoords(displayUvCoords, outUv)
    }

    private companion object {
        // AR Engine expects display UV coordinates (origin at the top-left), while the
        // quad is drawn bottom-left, bottom-right, top-left, top-right in OpenGL space.
        val displayUvCoords: FloatBuffer = GlUtil.createFloatBuffer(
            floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f,
            ),
        )
    }
}
