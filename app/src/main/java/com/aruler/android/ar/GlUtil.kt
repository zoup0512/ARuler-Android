package com.aruler.android.ar

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {

    private const val TAG = "GlUtil"

    fun loadAsset(context: Context, name: String): String {
        return context.assets.open("shaders/$name").bufferedReader().use { it.readText() }
    }

    fun createFloatBuffer(capacity: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = createFloatBuffer(data.size)
        buffer.put(data)
        buffer.position(0)
        return buffer
    }

    fun createProgram(context: Context, vertexAsset: String, fragmentAsset: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, loadAsset(context, vertexAsset))
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, loadAsset(context, fragmentAsset))

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("glCreateProgram failed")
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("glCreateShader failed")
        }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader: $log")
        }
        return shader
    }

    fun checkGlError(operation: String) {
        var error = GLES30.glGetError()
        while (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL error after $operation: $error")
            error = GLES30.glGetError()
        }
    }
}

class ShaderProgram(context: Context, vertexAsset: String, fragmentAsset: String) {

    val id: Int = GlUtil.createProgram(context, vertexAsset, fragmentAsset)

    private val uniformLocations = HashMap<String, Int>()

    fun use() {
        GLES30.glUseProgram(id)
    }

    private fun uniform(name: String): Int {
        return uniformLocations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }

    fun setMat4(name: String, matrix: FloatArray) {
        GLES30.glUniformMatrix4fv(uniform(name), 1, false, matrix, 0)
    }

    fun setVec4(name: String, values: FloatArray) {
        GLES30.glUniform4fv(uniform(name), 1, values, 0)
    }

    fun setVec2(name: String, x: Float, y: Float) {
        GLES30.glUniform2f(uniform(name), x, y)
    }

    fun setFloat(name: String, value: Float) {
        GLES30.glUniform1f(uniform(name), value)
    }

    fun delete() {
        GLES30.glDeleteProgram(id)
    }
}
