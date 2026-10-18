package com.aruler.android.ar

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aruler.android.LengthUnit
import com.aruler.android.R
import com.aruler.android.ui.ScreenLabel
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

data class FrameUiState(
    val hasActiveMeasurement: Boolean,
    val placeEnabled: Boolean,
    val indicatorGreen: Boolean,
    val headerText: String?,
    val labels: List<ScreenLabel>,
)

/**
 * Renders the AR camera feed and measurement overlays, and drives the hit-testing
 * state that the activity uses for its HUD and buttons.
 */
class ArRenderer(
    private val context: Context,
    private val controller: MeasurementController,
    private val displayRotationHelper: DisplayRotationHelper,
    private val uiListener: UiListener,
) : GLSurfaceView.Renderer {

    interface UiListener {
        fun onFrameUpdate(state: FrameUiState)
        fun onTrackingState(message: String?, flash: Boolean)
    }

    @Volatile
    var lengthUnit: LengthUnit = LengthUnit.CENTIMETER

    @Volatile
    var debugVisuals: Boolean = false

    private val sessionLock = Any()
    private var session: Session? = null

    private val backgroundRenderer = BackgroundRenderer()
    private val shapeRenderer = ShapeRenderer()
    private val hitTester = HitTester()

    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)

    private var viewportWidth = 0
    private var viewportHeight = 0

    private val pointCloudPoints = FloatArray(MAX_POINTS * 3)
    private var pointCount = 0

    private val featureColor = floatArrayOf(1f, 0.894f, 0.361f, 0.85f)
    private val lineColor = floatArrayOf(1f, 1f, 1f, 1f)
    private val gridColor = floatArrayOf(1f, 1f, 1f, 0.35f)
    private val axisXColor = floatArrayOf(1f, 0.322f, 0.322f, 0.9f)
    private val axisYColor = floatArrayOf(0.412f, 0.941f, 0.682f, 0.9f)
    private val axisZColor = floatArrayOf(0.267f, 0.541f, 1f, 0.9f)
    private val focusColor = floatArrayOf(1f, 0.839f, 0f, 0.95f)

    private var lastUiUpdateNanos = 0L
    private var lastSessionErrorNanos = 0L
    private var lastRenderErrorNanos = 0L
    private var lastTrackingMessage: String? = null
    private var lastTrackingFlash = false

    @Volatile
    private var currentTrackingMessage: String? = null

    fun currentTrackingMessage(): String? = currentTrackingMessage

    fun setSession(newSession: Session?) {
        synchronized(sessionLock) {
            session = newSession
        }
    }

    fun <T> withSessionLock(block: () -> T): T {
        return synchronized(sessionLock) { block() }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(FALLBACK_RED, FALLBACK_GREEN, FALLBACK_BLUE, 1f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        backgroundRenderer.createOnGlThread(context)
        shapeRenderer.createOnGlThread(context)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glClearColor(FALLBACK_RED, FALLBACK_GREEN, FALLBACK_BLUE, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        synchronized(sessionLock) {
            val activeSession = session ?: return
            val frame = try {
                if (backgroundRenderer.textureId >= 0) {
                    activeSession.setCameraTextureName(backgroundRenderer.textureId)
                }
                displayRotationHelper.updateSessionIfNeeded(activeSession)
                activeSession.update()
            } catch (error: Exception) {
                val now = System.nanoTime()
                if (now - lastSessionErrorNanos > 3_000_000_000L) {
                    Log.w(TAG, "ARCore session update failed", error)
                    lastSessionErrorNanos = now
                }
                return
            }

            try {
                render(activeSession, frame)
            } catch (error: Exception) {
                val now = System.nanoTime()
                if (now - lastRenderErrorNanos > 3_000_000_000L) {
                    Log.e(TAG, "AR rendering failed", error)
                    lastRenderErrorNanos = now
                }
            }
        }
    }

    private fun render(session: Session, frame: Frame) {
        val camera = frame.camera
        camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
        camera.getViewMatrix(view, 0)
        MeasurementMath.multiply(projection, view, viewProjection)
        shapeRenderer.setViewProjection(viewProjection)

        updatePointCloud(frame)
        notifyTrackingState(camera)

        val hit = try {
            hitTester.hitTest(
                frame = frame,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                pointCloud = pointCloudPoints,
                pointCount = pointCount,
            )
        } catch (_: Exception) {
            null
        }
        controller.update(hit?.position)

        val cameraTranslation = camera.pose.translation
        val cameraPosition = Vec3(
            cameraTranslation[0],
            cameraTranslation[1],
            cameraTranslation[2],
        )
        val snapshot = controller.snapshot()
        val closeToCamera = hit != null && hit.position.distanceTo(cameraPosition) < 0.05f
        val indicatorGreen = hit != null && (snapshot.hasActive || !closeToCamera)

        backgroundRenderer.draw(frame)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        if (pointCount > 0) {
            shapeRenderer.drawPoints(
                pointCloudPoints,
                pointCount,
                featureColor,
                2.4f * density,
            )
        }

        if (debugVisuals) {
            drawDebugVisuals(session, hit?.position)
        }

        val labels = ArrayList<ScreenLabel>(snapshot.completed.size + 2)
        for (line in snapshot.completed) {
            drawMeasurement(line, labels)
        }
        snapshot.active?.let { drawMeasurement(it, labels) }

        notifyFrameState(hit, snapshot, indicatorGreen, labels)
    }

    private fun drawMeasurement(line: MeasurementController.Line, labels: MutableList<ScreenLabel>) {
        val startVisible = isInFrontOfCamera(line.start)
        val endVisible = isInFrontOfCamera(line.end)

        if (startVisible && endVisible) {
            shapeRenderer.drawThickLine(
                start = line.start,
                end = line.end,
                color = lineColor,
                thicknessPx = 3f * density,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
            val dots = floatArrayOf(
                line.start.x, line.start.y, line.start.z,
                line.end.x, line.end.y, line.end.z,
            )
            shapeRenderer.drawPoints(dots, 2, lineColor, 8f * density)
        }

        val middle = (line.start + line.end) * 0.5f + Vec3(0f, 0.002f, 0f)
        val screenPoint = MeasurementMath.project(
            middle,
            viewProjection,
            viewportWidth,
            viewportHeight,
        ) ?: return
        if (screenPoint.x < 0f || screenPoint.x > viewportWidth ||
            screenPoint.y < 0f || screenPoint.y > viewportHeight
        ) {
            return
        }
        labels.add(
            ScreenLabel(
                x = screenPoint.x,
                y = screenPoint.y,
                text = lengthUnit.format(line.length(), 2),
            ),
        )
    }

    private fun isInFrontOfCamera(point: Vec3): Boolean {
        val z = view[2] * point.x + view[6] * point.y + view[10] * point.z + view[14]
        return z < -0.01f
    }

    private fun updatePointCloud(frame: Frame) {
        pointCount = 0
        var pointCloud: com.google.ar.core.PointCloud? = null
        try {
            pointCloud = frame.acquirePointCloud()
            val buffer = pointCloud.points
            val start = buffer.position()
            val total = buffer.remaining() / 4
            var outIndex = 0
            for (index in 0 until total) {
                val base = start + index * 4
                if (buffer.get(base + 3) <= 0f) continue
                if (pointCount >= MAX_POINTS) break
                pointCloudPoints[outIndex++] = buffer.get(base)
                pointCloudPoints[outIndex++] = buffer.get(base + 1)
                pointCloudPoints[outIndex++] = buffer.get(base + 2)
                pointCount++
            }
        } catch (_: Exception) {
            pointCount = 0
        } finally {
            pointCloud?.release()
        }
    }

    private fun drawDebugVisuals(session: Session, focusPoint: Vec3?) {
        val planeLines = VertexList()
        val quaternion = FloatArray(4)
        val translation = FloatArray(3)

        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue

            val extentX = plane.extentX
            val extentZ = plane.extentZ
            if (extentX <= 0f || extentZ <= 0f) continue

            plane.centerPose.getRotationQuaternion(quaternion, 0)
            plane.centerPose.getTranslation(translation, 0)
            val center = Vec3(translation[0], translation[1], translation[2])

            fun worldPoint(x: Float, z: Float): Vec3 {
                val rotated = MeasurementMath.rotate(quaternion, Vec3(x, 0f, z))
                return rotated + center
            }

            val halfX = extentX / 2f
            val halfZ = extentZ / 2f

            planeLines.addSegment(worldPoint(-halfX, -halfZ), worldPoint(halfX, -halfZ))
            planeLines.addSegment(worldPoint(halfX, -halfZ), worldPoint(halfX, halfZ))
            planeLines.addSegment(worldPoint(halfX, halfZ), worldPoint(-halfX, halfZ))
            planeLines.addSegment(worldPoint(-halfX, halfZ), worldPoint(-halfX, -halfZ))

            val divisionsX = min(20, (extentX / 0.1f).toInt())
            for (i in 1 until divisionsX) {
                val x = -halfX + extentX * i / divisionsX
                planeLines.addSegment(worldPoint(x, -halfZ), worldPoint(x, halfZ))
            }
            val divisionsZ = min(20, (extentZ / 0.1f).toInt())
            for (i in 1 until divisionsZ) {
                val z = -halfZ + extentZ * i / divisionsZ
                planeLines.addSegment(worldPoint(-halfX, z), worldPoint(halfX, z))
            }
        }

        val planeVertices = planeLines.toArray()
        if (planeVertices.isNotEmpty()) {
            shapeRenderer.drawLines(planeVertices, planeVertices.size / 3, gridColor)
        }

        val axes = floatArrayOf(
            0f, 0f, 0f, 0.1f, 0f, 0f,
        )
        shapeRenderer.drawLines(axes, 2, axisXColor)
        val yAxis = floatArrayOf(
            0f, 0f, 0f, 0f, 0.1f, 0f,
        )
        shapeRenderer.drawLines(yAxis, 2, axisYColor)
        val zAxis = floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0.1f,
        )
        shapeRenderer.drawLines(zAxis, 2, axisZColor)

        if (focusPoint != null) {
            val half = 0.085f
            val corners = arrayOf(
                focusPoint + Vec3(-half, 0f, -half),
                focusPoint + Vec3(half, 0f, -half),
                focusPoint + Vec3(half, 0f, half),
                focusPoint + Vec3(-half, 0f, half),
            )
            for (index in 0 until 4) {
                shapeRenderer.drawThickLine(
                    corners[index],
                    corners[(index + 1) % 4],
                    focusColor,
                    2f * density,
                    viewportWidth,
                    viewportHeight,
                )
            }
        }
    }

    private fun notifyTrackingState(camera: Camera) {
        if (camera.trackingState == TrackingState.TRACKING) {
            currentTrackingMessage = null
            if (lastTrackingMessage != null) {
                lastTrackingMessage = null
                lastTrackingFlash = false
                mainHandler.post { uiListener.onTrackingState(null, false) }
            }
            return
        }

        val (messageRes, flash) = when (camera.trackingFailureReason) {
            TrackingFailureReason.NONE -> R.string.tracking_initializing to false
            TrackingFailureReason.BAD_STATE -> R.string.tracking_bad_state to false
            TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.tracking_insufficient_light to false
            TrackingFailureReason.EXCESSIVE_MOTION -> R.string.tracking_excessive_motion to true
            TrackingFailureReason.INSUFFICIENT_FEATURES ->
                R.string.tracking_insufficient_features to false

            TrackingFailureReason.CAMERA_UNAVAILABLE ->
                R.string.tracking_camera_unavailable to false
        }

        val message = context.getString(messageRes)
        currentTrackingMessage = message
        if (message != lastTrackingMessage || flash != lastTrackingFlash) {
            lastTrackingMessage = message
            lastTrackingFlash = flash
            mainHandler.post { uiListener.onTrackingState(message, flash) }
        }
    }

    private fun notifyFrameState(
        hit: HitTester.WorldHit?,
        snapshot: MeasurementController.Snapshot,
        indicatorGreen: Boolean,
        labels: List<ScreenLabel>,
    ) {
        val now = System.nanoTime()
        if (now - lastUiUpdateNanos < UI_UPDATE_INTERVAL_NANOS) return
        lastUiUpdateNanos = now

        val headerText = if (hit != null) {
            val meters = if (snapshot.hasActive) snapshot.activeLength ?: 0f else 0f
            lengthUnit.format(meters, 1)
        } else {
            null
        }

        val state = FrameUiState(
            hasActiveMeasurement = snapshot.hasActive,
            placeEnabled = indicatorGreen || snapshot.hasActive,
            indicatorGreen = indicatorGreen,
            headerText = headerText,
            labels = labels,
        )
        mainHandler.post { uiListener.onFrameUpdate(state) }
    }

    private class VertexList(initialCapacity: Int = 192) {
        private var data = FloatArray(initialCapacity)
        private var size = 0

        fun add(point: Vec3) {
            ensureCapacity(3)
            data[size++] = point.x
            data[size++] = point.y
            data[size++] = point.z
        }

        fun addSegment(start: Vec3, end: Vec3) {
            add(start)
            add(end)
        }

        fun toArray(): FloatArray = data.copyOf(size)

        private fun ensureCapacity(additional: Int) {
            if (size + additional <= data.size) return
            var newCapacity = data.size * 2
            while (newCapacity < size + additional) {
                newCapacity *= 2
            }
            data = data.copyOf(newCapacity)
        }
    }

    companion object {
        private const val TAG = "ArRenderer"
        private const val MAX_POINTS = 61440
        private const val UI_UPDATE_INTERVAL_NANOS = 33_000_000L

        private const val FALLBACK_RED = 0.667f
        private const val FALLBACK_GREEN = 0.667f
        private const val FALLBACK_BLUE = 0.667f
    }
}
