package com.aruler.android.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.FloatBuffer

/**
 * Draws the tracking backend's camera image as an opaque full-screen quad.
 */
class BackgroundRenderer {

    var textureId: Int = -1
        private set

    private var program: ShaderProgram? = null

    private val quadTexCoords: FloatBuffer = GlUtil.createFloatBuffer(8)

    private val positionBuffer: FloatBuffer = GlUtil.createFloatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 0f, 1f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 0f, 1f,
        ),
    )

    fun createOnGlThread(context: Context) {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        program = ShaderProgram(context, "background.vert", "background.frag")
    }

    fun draw(frame: TrackingFrame) {
        val shader = program ?: return
        if (textureId < 0) return

        quadTexCoords.position(0)
        frame.updateBackgroundTextureCoords(quadTexCoords)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_BLEND)

        shader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shader.id, "u_Texture"), 0)

        positionBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 0, positionBuffer)

        quadTexCoords.position(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, quadTexCoords)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }
}
