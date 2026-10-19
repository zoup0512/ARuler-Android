package com.aruler.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.aruler.android.ar.ArRenderer
import com.aruler.android.ar.BackendPrepareResult
import com.aruler.android.ar.DisplayRotationHelper
import com.aruler.android.ar.FrameUiState
import com.aruler.android.ar.MeasurementController
import com.aruler.android.ar.TrackingBackend
import com.aruler.android.ar.TrackingBackends
import com.aruler.android.ar.TrackingSession
import com.aruler.android.ui.CoachMarkOverlayView
import com.aruler.android.ui.HudView
import com.aruler.android.ui.MeasurementLabelView
import com.aruler.android.ui.dpInt
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), ArRenderer.UiListener {

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var renderer: ArRenderer
    private lateinit var controller: MeasurementController
    private lateinit var displayRotationHelper: DisplayRotationHelper

    private lateinit var header: View
    private lateinit var distanceLabel: AppCompatTextView
    private lateinit var indicator: AppCompatImageView
    private lateinit var placeButton: ImageButton
    private lateinit var trashButton: ImageButton
    private lateinit var resetButton: ImageButton
    private lateinit var helpButton: AppCompatImageView
    private lateinit var settingsButton: AppCompatImageView
    private lateinit var debugButton: AppCompatImageView
    private lateinit var hud: HudView
    private lateinit var labelOverlay: MeasurementLabelView
    private lateinit var coachOverlay: CoachMarkOverlayView
    private lateinit var permissionOverlay: View
    private lateinit var grantCameraButton: MaterialButton
    private lateinit var openSettingsButton: MaterialButton

    private var backend: TrackingBackend? = null
    private var session: TrackingSession? = null
    private var sessionResumed = false
    private var surfaceResumed = false
    private var displayListenerRegistered = false
    private var installRequested = false
    private var cameraPermissionRequested = false
    private var fatalDialogShown = false
    private var guideScheduled = false

    private var unit: LengthUnit = LengthUnit.CENTIMETER
    private var lastIndicatorGreen = false
    private var placeButtonShowsCheck = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            hidePermissionOverlay()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumeAr()
            }
        } else {
            showPermissionOverlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContentView(R.layout.activity_main)
        bindViews()
        setupSystemUi()

        controller = MeasurementController()
        displayRotationHelper = DisplayRotationHelper(this)
        renderer = ArRenderer(this, controller, displayRotationHelper, this)
        unit = Prefs.lengthUnit(this)
        renderer.lengthUnit = unit

        setupSurface()
        setupHeaderInsets()
        setupClickListeners()

        debugButton.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        resetUiState()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()

        unit = Prefs.lengthUnit(this)
        renderer.lengthUnit = unit

        if (!hasCameraPermission()) {
            showPermissionOverlay()
            if (!cameraPermissionRequested) {
                cameraPermissionRequested = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            return
        }

        hidePermissionOverlay()
        resumeAr()
    }

    override fun onPause() {
        if (displayListenerRegistered) {
            displayRotationHelper.onPause()
            displayListenerRegistered = false
        }
        if (surfaceResumed) {
            surfaceView.onPause()
            surfaceResumed = false
        }
        if (sessionResumed) {
            renderer.withSessionLock { runCatching { session?.pause() } }
            sessionResumed = false
        }
        super.onPause()
    }

    override fun onDestroy() {
        renderer.withSessionLock {
            runCatching { session?.close() }
            session = null
            renderer.setSession(null)
        }
        sessionResumed = false
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onFrameUpdate(state: FrameUiState) {
        if (isFinishing || isDestroyed) return

        state.headerText?.let { text ->
            if (distanceLabel.text.toString() != text) {
                distanceLabel.text = text
            }
        }

        if (lastIndicatorGreen != state.indicatorGreen) {
            lastIndicatorGreen = state.indicatorGreen
            indicator.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (state.indicatorGreen) R.color.fine else R.color.alert,
                ),
            )
        }

        if (placeButton.isEnabled != state.placeEnabled) {
            placeButton.isEnabled = state.placeEnabled
            placeButton.alpha = if (state.placeEnabled) 1f else 0.5f
        }

        setPlaceButtonState(state.hasActiveMeasurement)
        labelOverlay.setLabels(state.labels)
    }

    override fun onTrackingState(message: String?, flash: Boolean) {
        if (isFinishing || isDestroyed) return
        if (coachOverlay.isActive() || permissionOverlay.visibility == View.VISIBLE) return

        when {
            message == null -> hud.hideMessage()
            flash -> hud.flashMessage(message)
            else -> hud.showMessage(message)
        }
    }

    private fun bindViews() {
        surfaceView = findViewById(R.id.surface_view)
        header = findViewById(R.id.header)
        distanceLabel = findViewById(R.id.distance_label)
        indicator = findViewById(R.id.indicator)
        placeButton = findViewById(R.id.place_button)
        trashButton = findViewById(R.id.trash_button)
        resetButton = findViewById(R.id.reset_button)
        helpButton = findViewById(R.id.help_button)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.debug_button)
        hud = findViewById(R.id.hud)
        labelOverlay = findViewById(R.id.label_overlay)
        coachOverlay = findViewById(R.id.coach_overlay)
        permissionOverlay = findViewById(R.id.permission_overlay)
        grantCameraButton = findViewById(R.id.grant_camera_button)
        openSettingsButton = findViewById(R.id.open_settings_button)
    }

    private fun setupSurface() {
        surfaceView.setEGLContextClientVersion(3)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(renderer)
        surfaceView.setPreserveEGLContextOnPause(true)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun setupSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupHeaderInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val baseHeight = dpInt(84f)
            val newHeight = baseHeight + cutoutTop
            if (view.layoutParams.height != newHeight) {
                view.layoutParams = view.layoutParams.apply { height = newHeight }
            }
            if (view.paddingTop != cutoutTop) {
                view.setPadding(0, cutoutTop, 0, 0)
            }
            insets
        }
    }

    private fun setupClickListeners() {
        helpButton.setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        debugButton.setOnClickListener {
            val enabled = !renderer.debugVisuals
            renderer.debugVisuals = enabled
            debugButton.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (enabled) R.color.theme else R.color.text_title,
                ),
            )
        }
        placeButton.setOnClickListener { view ->
            bounce(view)
            if (controller.place()) {
                setPlaceButtonState(controller.hasActive())
            }
        }
        trashButton.setOnClickListener { view ->
            bounce(view)
            if (controller.delete()) {
                setPlaceButtonState(controller.hasActive())
            }
        }
        resetButton.setOnClickListener { view ->
            bounce(view)
            restartSession()
        }
        grantCameraButton.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        openSettingsButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
            startActivity(intent)
        }
    }

    private fun resumeAr() {
        if (isFinishing || isDestroyed) return
        if (!hasCameraPermission()) {
            showPermissionOverlay()
            return
        }
        if (session == null) {
            val activeBackend = backend ?: TrackingBackends.select(this).also { backend = it }
            when (activeBackend.prepare(this, !installRequested)) {
                BackendPrepareResult.READY -> Unit
                BackendPrepareResult.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }

                BackendPrepareResult.DECLINED -> {
                    showFatal(getString(R.string.ar_install_failed))
                    return
                }

                BackendPrepareResult.UNSUPPORTED -> {
                    showFatal(getString(R.string.ar_unsupported_message))
                    return
                }
            }

            var newSession: TrackingSession? = null
            try {
                newSession = activeBackend.createSession(this)
                session = newSession
                renderer.setSession(newSession)
                displayRotationHelper.forceUpdate()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to create ${activeBackend.name} session", error)
                runCatching { newSession?.close() }
                session = null
                renderer.setSession(null)
                showFatal(getString(R.string.ar_unsupported_message))
                return
            }
        }

        if (!displayListenerRegistered) {
            displayRotationHelper.onResume()
            displayListenerRegistered = true
        }
        if (!sessionResumed) {
            try {
                session?.resume()
                sessionResumed = true
            } catch (_: Exception) {
                showFatal(getString(R.string.ar_session_failed))
                return
            }
        }
        if (!surfaceResumed) {
            surfaceView.onResume()
            surfaceResumed = true
        }

        hidePermissionOverlay()
        maybeShowCoachMarks()
    }

    private fun restartSession() {
        controller.clear()
        labelOverlay.setLabels(emptyList())
        resetUiState()
        hud.hideMessage()

        var failureMessage: String? = null
        renderer.withSessionLock {
            val previousSession = session
            if (sessionResumed) {
                runCatching { previousSession?.pause() }
                sessionResumed = false
            }
            runCatching { previousSession?.close() }

            var newSession: TrackingSession? = null
            try {
                val activeBackend = backend ?: TrackingBackends.select(this).also { backend = it }
                newSession = activeBackend.createSession(this)
                session = newSession
                renderer.setSession(newSession)
                displayRotationHelper.forceUpdate()
                newSession.resume()
                sessionResumed = true
            } catch (error: Exception) {
                Log.e(TAG, "Failed to restart AR session", error)
                runCatching { newSession?.close() }
                session = null
                renderer.setSession(null)
                sessionResumed = false
                failureMessage = getString(R.string.ar_session_failed)
            }
        }

        failureMessage?.let { showFatal(it) }
    }

    private fun maybeShowCoachMarks() {
        if (guideScheduled || Prefs.hasShownGuide(this)) return
        guideScheduled = true
        Prefs.markGuideShown(this)

        coachOverlay.post {
            if (isFinishing || isDestroyed) return@post
            val circle = CoachMarkOverlayView.Shape.CIRCLE
            val square = CoachMarkOverlayView.Shape.SQUARE
            val steps = listOf(
                CoachMarkOverlayView.Step(getString(R.string.coach_welcome)),
                CoachMarkOverlayView.Step(getString(R.string.coach_move)),
                CoachMarkOverlayView.Step(
                    getString(R.string.coach_indicator_green),
                    indicator,
                    circle,
                ),
                CoachMarkOverlayView.Step(getString(R.string.coach_aim), indicator, circle),
                CoachMarkOverlayView.Step(getString(R.string.coach_start), placeButton, circle),
                CoachMarkOverlayView.Step(
                    getString(R.string.coach_result),
                    distanceLabel,
                    square,
                ),
                CoachMarkOverlayView.Step(getString(R.string.coach_end), placeButton, circle),
                CoachMarkOverlayView.Step(getString(R.string.coach_delete), trashButton, circle),
                CoachMarkOverlayView.Step(getString(R.string.coach_reset), resetButton, circle),
                CoachMarkOverlayView.Step(getString(R.string.coach_help), helpButton, circle),
                CoachMarkOverlayView.Step(
                    getString(R.string.coach_settings),
                    settingsButton,
                    circle,
                ),
                CoachMarkOverlayView.Step(getString(R.string.coach_finish)),
            )
            coachOverlay.start(steps) {
                hud.hideMessage()
                renderer.currentTrackingMessage()?.let { hud.showMessage(it) }
            }
        }
    }

    private fun resetUiState() {
        distanceLabel.text = getString(R.string.header_placeholder)
        lastIndicatorGreen = false
        indicator.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.alert),
        )
        placeButton.isEnabled = false
        placeButton.alpha = 0.5f
        setPlaceButtonState(false)
        labelOverlay.setLabels(emptyList())
    }

    private fun setPlaceButtonState(active: Boolean) {
        if (placeButtonShowsCheck == active) return
        placeButtonShowsCheck = active
        placeButton.setImageResource(
            if (active) R.drawable.ic_check_place else R.drawable.ic_add_place,
        )
        placeButton.contentDescription = getString(
            if (active) R.string.place_end_point else R.string.place_point,
        )
    }

    private fun showPermissionOverlay() {
        if (permissionOverlay.visibility != View.VISIBLE) {
            permissionOverlay.visibility = View.VISIBLE
        }
        hud.hideMessage()
    }

    private fun hidePermissionOverlay() {
        if (permissionOverlay.visibility != View.GONE) {
            permissionOverlay.visibility = View.GONE
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showFatal(message: String) {
        if (isFinishing || isDestroyed || fatalDialogShown) return
        fatalDialogShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.ar_unsupported_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    private fun bounce(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(100L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100L)
                    .start()
            }
            .start()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
