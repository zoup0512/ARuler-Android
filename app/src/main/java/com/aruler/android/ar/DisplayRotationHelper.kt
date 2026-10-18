package com.aruler.android.ar

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.google.ar.core.Session

/**
 * Keeps the ARCore display geometry in sync with the GL surface and display rotation.
 */
class DisplayRotationHelper(context: Context) : DisplayManager.DisplayListener {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val display: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    private var viewportWidth = 0
    private var viewportHeight = 0

    @Volatile
    private var lastKnownRotation = -1

    fun onResume() {
        displayManager.registerDisplayListener(this, null)
    }

    fun onPause() {
        displayManager.unregisterDisplayListener(this)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        lastKnownRotation = -1
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportWidth == 0 || viewportHeight == 0) return
        val rotation = display.rotation
        if (rotation != lastKnownRotation) {
            session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            lastKnownRotation = rotation
        }
    }

    fun forceUpdate() {
        lastKnownRotation = -1
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        lastKnownRotation = -1
    }
}
