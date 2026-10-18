package com.aruler.android.ar

import android.content.Context
import android.opengl.GLES30
import java.nio.FloatBuffer

/**
 * Renders debug lines, point-cloud sprites, and camera-facing thick measurement lines.
 */
class ShapeRenderer {

    private var colorProgram: ShaderProgram? = null
    private var pointProgram: ShaderProgram? = null
    private var thickLineProgram: ShaderProgram? = null

    private var colorVbo = 0
    private var pointVbo = 0
    private var thickLineVbo = 0

    private var uploadBuffer: FloatBuffer = GlUtil.createFloatBuffer(4096)
    private var mvp = FloatArray(16)

    fun createOnGlThread(context: Context) {
        colorProgram = ShaderProgram(context, "color.vert", "color.frag")
        pointProgram = ShaderProgram(context, "point.vert", "point.frag")
        thickLineProgram = ShaderProgram(context, "thick_line.vert", "thick_line.frag")

        val buffers = IntArray(3)
        GLES30.glGenBuffers(3, buffers, 0)
        colorVbo = buffers[0]
        pointVbo = buffers[1]
        thickLineVbo = buffers[2]
    }

    fun setViewProjection(matrix: FloatArray) {
        System.arraycopy(matrix, 0, mvp, 0, 16)
    }

    fun drawLines(vertices: FloatArray, vertexCount: Int, color: FloatArray) {
        if (vertexCount < 2) return
        val program = colorProgram ?: return

        program.use()
        program.setMat4("u_Mvp", mvp)
        program.setVec4("u_Color", color)

        upload(colorVbo, vertices, vertexCount * 3)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun drawPoints(
        vertices: FloatArray,
        vertexCount: Int,
        color: FloatArray,
        pointSizePx: Float,
    ) {
        if (vertexCount < 1) return
        val program = pointProgram ?: return

        program.use()
        program.setMat4("u_Mvp", mvp)
        program.setVec4("u_Color", color)
        program.setFloat("u_PointSize", pointSizePx)

        upload(pointVbo, vertices, vertexCount * 3)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun drawThickLine(
        start: Vec3,
        end: Vec3,
        color: FloatArray,
        thicknessPx: Float,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val program = thickLineProgram ?: return
        if (start.distanceTo(end) < 1e-5f) return

        val vertices = FloatArray(32)
        writeVertex(vertices, 0, start, end, -1f, 0f)
        writeVertex(vertices, 8, start, end, 1f, 0f)
        writeVertex(vertices, 16, start, end, -1f, 1f)
        writeVertex(vertices, 24, start, end, 1f, 1f)

        program.use()
        program.setMat4("u_Mvp", mvp)
        program.setVec2("u_Viewport", viewportWidth.toFloat(), viewportHeight.toFloat())
        program.setFloat("u_ThicknessPx", thicknessPx)
        program.setVec4("u_Color", color)

        upload(thickLineVbo, vertices, vertices.size)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 32, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 32, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, 32, 28)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun writeVertex(
        data: FloatArray,
        offset: Int,
        start: Vec3,
        end: Vec3,
        side: Float,
        endSelect: Float,
    ) {
        data[offset] = start.x
        data[offset + 1] = start.y
        data[offset + 2] = start.z
        data[offset + 3] = end.x
        data[offset + 4] = end.y
        data[offset + 5] = end.z
        data[offset + 6] = side
        data[offset + 7] = endSelect
    }

    private fun upload(vbo: Int, data: FloatArray, floatCount: Int) {
        if (uploadBuffer.capacity() < floatCount) {
            uploadBuffer = GlUtil.createFloatBuffer(floatCount)
        }
        uploadBuffer.clear()
        uploadBuffer.put(data, 0, floatCount)
        uploadBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, uploadBuffer, GLES30.GL_STREAM_DRAW)
    }
}
