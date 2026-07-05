package me.magnum.melonds.ui.emulator.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

private const val HDR_LOG_TAG = "MelonHdr"

// From EGL_EXT_pixel_format_float. Not exposed by android.opengl.EGL14/EGLExt, so declared here.
private const val EGL_COLOR_COMPONENT_TYPE_EXT = 0x3339
private const val EGL_COLOR_COMPONENT_TYPE_FLOAT_EXT = 0x333B

// From EGL_KHR_gl_colorspace + EGL_EXT_gl_colorspace_scrgb: non-linear extended-sRGB, where [0,1] is SDR and values
// above 1.0 map into the display's HDR headroom (overbright). Same sRGB transfer as the default, so SDR content is unchanged.
private const val EGL_GL_COLORSPACE_KHR = 0x309D
private const val EGL_GL_COLORSPACE_SCRGB_EXT = 0x3351

class GlContext(sharedEglContext: Long? = null) {

    private var display: EGLDisplay
    private var config: EGLConfig
    private var context: Long

    /**
     * True when the context was created with an FP16 config and the scRGB colorspace extension is available, i.e. an
     * overbright (HDR headroom) window surface can be created. When false everything falls back to a standard 8-bit SDR
     * surface and the LCD filter stays within SDR (plan "C+").
     */
    val isHdrCapable: Boolean

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw GlContextException("No display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw GlContextException("Unable to initialize EGL")
        }

        val shared = sharedEglContext ?: 0
        val extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS).orEmpty()
        val scRgbSupported = extensions.contains("EGL_EXT_pixel_format_float") &&
                extensions.contains("EGL_EXT_gl_colorspace_scrgb")

        // Prefer an FP16 config so the LCD filter can push overbright (>1.0) highlights into the display's HDR headroom.
        // Fall back to the standard 8-bit SDR config if the FP16 config or its shared context can't be created.
        var hdr = false
        var chosenConfig: EGLConfig? = null
        var createdContext = 0L
        if (scRgbSupported) {
            val fp16Config = createFp16Config()
            if (fp16Config != null) {
                val fp16Context = createContext(display.nativeHandle, fp16Config.nativeHandle, shared)
                if (fp16Context != 0L) {
                    chosenConfig = fp16Config
                    createdContext = fp16Context
                    hdr = true
                }
            }
        }
        if (createdContext == 0L) {
            chosenConfig = create8BitConfig()
            createdContext = createContext(display.nativeHandle, chosenConfig.nativeHandle, shared)
        }
        if (createdContext == 0L) {
            throw GlContextException("Failed to create context: ${EGL14.eglGetError()}")
        }

        config = chosenConfig!!
        context = createdContext
        isHdrCapable = hdr
        Log.i(HDR_LOG_TAG, "GlContext: isHdrCapable=$isHdrCapable (scRgbSupported=$scRgbSupported)")
    }

    fun use(surface: EGLSurface) {
        if (!makeCurrent(display.nativeHandle, surface.nativeHandle, context)) {
            throw GlContextException("Failed to make current: ${EGL14.eglGetError()}")
        }

        // Allows immediate buffer swapping without waiting for VSync
        EGL14.eglSwapInterval(display, 0)
    }

    fun swapBuffers(surface: EGLSurface) {
        EGL14.eglSwapBuffers(display, surface)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun destroy() {
        destroyContext(display.nativeHandle, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()

        display = EGL14.EGL_NO_DISPLAY
        context = 0L
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attributes = if (isHdrCapable) {
            intArrayOf(EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_SCRGB_EXT, EGL14.EGL_NONE)
        } else {
            intArrayOf(EGL14.EGL_NONE)
        }
        var eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE && isHdrCapable) {
            // The FP16 config was accepted but this particular surface rejects the scRGB colorspace; keep rendering in SDR.
            Log.w(HDR_LOG_TAG, "scRGB window surface failed (0x${Integer.toHexString(EGL14.eglGetError())}); falling back to default colorspace")
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        }
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw GlContextException("Failed to create window surface: ${EGL14.eglGetError()}")
        }

        return eglSurface
    }

    fun destroyWindowSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(display, eglSurface)
    }

    private fun createFp16Config(): EGLConfig? {
        val attributeList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL_COLOR_COMPONENT_TYPE_EXT, EGL_COLOR_COMPONENT_TYPE_FLOAT_EXT,
            EGL14.EGL_RED_SIZE, 16,
            EGL14.EGL_GREEN_SIZE, 16,
            EGL14.EGL_BLUE_SIZE, 16,
            EGL14.EGL_ALPHA_SIZE, 16,
            EGL14.EGL_DEPTH_SIZE, 24,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE,
        )

        val eglConfig = arrayOfNulls<EGLConfig?>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributeList, 0, eglConfig, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            return null
        }

        return eglConfig[0]
    }

    private fun create8BitConfig(): EGLConfig {
        val attributeList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 24,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE,
        )

        val eglConfig = arrayOfNulls<EGLConfig?>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributeList, 0, eglConfig, 0, 1, numConfigs, 0)) {
            throw GlContextException("Unable to choose config")
        }

        return eglConfig[0]!!
    }

    // Because we need to use a shared EGL context, and we can't build an EGLContext instance from a native context instance, we need to perform context manipulation
    // operations on the native side instead of using the Java wrappers

    private external fun createContext(display: Long, config: Long, sharedGlContext: Long): Long
    private external fun makeCurrent(display: Long, surface: Long, context: Long): Boolean
    private external fun destroyContext(display: Long, context: Long)

    class GlContextException(message: String) : Exception(message)
}