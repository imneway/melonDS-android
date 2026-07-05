package me.magnum.melonds.common.opengl

import android.opengl.GLES30

private const val INVALID_ATTRIBUTE = -1

class Shader(
    private val vertexShaderId: Int,
    private val fragmentShaderId: Int,
    private val programId: Int,
    val textureFiltering: Int,
) {
    val attribUv: Int
    val attribPos: Int
    val attribAlpha: Int
    val uniformTex: Int
    val uniformHeadroom: Int

    init {
        GLES30.glUseProgram(programId)
        attribUv = GLES30.glGetAttribLocation(programId, "vUV")
        attribPos = GLES30.glGetAttribLocation(programId, "vPos")
        attribAlpha = GLES30.glGetAttribLocation(programId, "vAlpha")
        uniformTex = GLES30.glGetUniformLocation(programId, "tex")
        // Only present on filters that support HDR headroom (e.g. LCD). -1 (absent) for the rest, which is a no-op when set.
        uniformHeadroom = GLES30.glGetUniformLocation(programId, "uHeadroom")
        GLES30.glUseProgram(0)
    }

    fun use() {
        GLES30.glUseProgram(programId)
        GLES30.glEnableVertexAttribArray(attribUv)
        GLES30.glEnableVertexAttribArray(attribPos)
        if (attribAlpha != INVALID_ATTRIBUTE) {
            GLES30.glEnableVertexAttribArray(attribAlpha)
        }
    }

    /**
     * Sets the HDR headroom for filters that support it. [headroom] is the ratio of peak displayable white to SDR white:
     * 1.0 means SDR (highlights roll off to white), values above 1.0 let the bright phase exceed SDR white. No-op for
     * shaders without the uniform.
     */
    fun setHeadroom(headroom: Float) {
        if (uniformHeadroom != INVALID_ATTRIBUTE) {
            GLES30.glUniform1f(uniformHeadroom, headroom)
        }
    }

    fun delete() {
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)
        GLES30.glDeleteProgram(programId)
    }
}