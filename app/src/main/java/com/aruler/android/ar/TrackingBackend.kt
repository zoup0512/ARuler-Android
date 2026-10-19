package com.aruler.android.ar

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.huawei.hiar.AREnginesApk
import java.nio.FloatBuffer
import java.util.Locale

/**
 * Common view of the tracking data used by the renderer. ARCore and Huawei AR Engine
 * expose very similar APIs, but they live in different packages and have a few
 * behavioural differences, so the rest of the app only talks to these interfaces.
 */
enum class TrackingState { TRACKING, PAUSED, STOPPED, UNKNOWN }

enum class TrackingIssue {
    NONE,
    INITIALIZING,
    BAD_STATE,
    INSUFFICIENT_LIGHT,
    EXCESSIVE_MOTION,
    INSUFFICIENT_FEATURES,
    CAMERA_UNAVAILABLE,
}

data class PlaneInfo(
    val translation: Vec3,
    val rotationQuaternion: FloatArray,
    val extentX: Float,
    val extentZ: Float,
)

interface TrackingFrame {
    val cameraTranslation: Vec3
    val cameraRotationQuaternion: FloatArray
    val trackingState: TrackingState
    val trackingIssue: TrackingIssue

    fun getProjectionMatrix(out: FloatArray, near: Float, far: Float)

    fun getViewMatrix(out: FloatArray)

    fun hitTestHorizontalPlane(screenX: Float, screenY: Float): Vec3?

    fun trackedPlanes(): List<PlaneInfo>

    fun copyFeaturePoints(out: FloatArray, maxPoints: Int): Int

    /**
     * Fills [outUv] with the texture coordinates that display the camera image behind
     * the full-screen quad. Each backend supplies the input coordinate convention it expects.
     */
    fun updateBackgroundTextureCoords(outUv: FloatBuffer)
}

interface TrackingSession {
    val backendName: String

    fun setCameraTextureName(textureId: Int)

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int)

    fun update(): TrackingFrame?

    fun resume()

    fun pause()

    fun close()
}

enum class BackendPrepareResult {
    READY,
    INSTALL_REQUESTED,
    DECLINED,
    UNSUPPORTED,
}

interface TrackingBackend {
    val name: String

    /**
     * Checks that the runtime is installed and compatible. May launch an install/update
     * flow, in which case [BackendPrepareResult.INSTALL_REQUESTED] is returned.
     */
    fun prepare(activity: Activity, userRequestedInstall: Boolean): BackendPrepareResult

    fun createSession(context: Context): TrackingSession
}

object TrackingBackends {
    private const val TAG = "TrackingBackends"

    fun select(context: Context): TrackingBackend {
        if (isHuaweiDevice()) {
            val engineReady = runCatching {
                AREnginesApk.isAREngineApkReady(context)
            }.getOrDefault(false)
            if (engineReady) {
                Log.i(TAG, "Selected Huawei AR Engine")
                return ArEngineBackend()
            }

            // Some Huawei devices are also ARCore certified; use that when possible
            // instead of asking the user to install the Huawei runtime.
            val coreSupported = runCatching {
                ArCoreApk.getInstance().checkAvailability(context).isSupported
            }.getOrDefault(false)
            if (coreSupported) {
                Log.i(TAG, "Selected ARCore (AR Engine not ready, device is ARCore capable)")
                return ArCoreBackend()
            }

            Log.i(TAG, "Selected Huawei AR Engine (runtime not installed yet)")
            return ArEngineBackend()
        }

        Log.i(TAG, "Selected ARCore")
        return ArCoreBackend()
    }

    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val brand = Build.BRAND.lowercase(Locale.US)
        return manufacturer == "huawei" || manufacturer == "honor" ||
            brand == "huawei" || brand == "honor"
    }
}
