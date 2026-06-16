package me.magnum.melonds.ui.emulator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.HotCornerConfiguration
import me.magnum.melonds.domain.model.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

class HotCornerView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface HotCornerCallback {
        fun onTopLeftClicked()
        fun onTopRightClicked()
        fun onBottomLeftClicked()
        fun onBottomRightClicked()
    }

    private enum class HotCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private val hotCornerSizePx = dpToPixels(75f)
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private val indicatorSizePx = dpToPixels(32f)
    private val indicatorPaddingPx = dpToPixels(8f)
    private val pauseIconBarWidthPx = dpToPixels(12f)
    private val pauseIconBarHeightPx = dpToPixels(42f)
    private val pauseIconBarGapPx = dpToPixels(8f)
    private val pauseIconBarRadiusPx = dpToPixels(3f)
    private val pauseOverlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = (255 * 0.24f).roundToInt()
    }
    private val pauseIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val pauseOverlayPath = Path()
    private val pauseIconBounds = RectF()
    private val pauseBreathingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pauseBreathingProgress = it.animatedValue as Float
            invalidate()
        }
    }
    private val fastForwardIndicatorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_fast_forward_indicator)?.let {
        DrawableCompat.wrap(it.mutate())
    }

    private var hotCornerCallback: HotCornerCallback? = null
    private var hotCornerConfiguration = HotCornerConfiguration()
    private var showFastForwardIndicator = false
    private var showPauseOverlay = false
    private var fastForwardIndicatorAnchorArea: Rect? = null
    private var pauseOverlayTopScreenArea: Rect? = null
    private var pauseOverlayBottomScreenArea: Rect? = null
    private var pauseBreathingProgress = 0f
    private var isTrackingHotCornerTouch = false
    private var pressedHotCorner: HotCorner? = null
    private var downX = 0f
    private var downY = 0f

    fun setHotCornerCallback(callback: HotCornerCallback?) {
        hotCornerCallback = callback
    }

    fun setHotCornerConfiguration(configuration: HotCornerConfiguration) {
        if (hotCornerConfiguration == configuration) {
            return
        }

        hotCornerConfiguration = configuration
        resetHotCornerTouch()
        updateVisibility()
    }

    fun setFastForwardIndicatorVisible(visible: Boolean) {
        if (showFastForwardIndicator == visible) {
            return
        }

        showFastForwardIndicator = visible
        updateVisibility()
        invalidate()
    }

    fun setPauseOverlayVisible(visible: Boolean) {
        if (showPauseOverlay == visible) {
            return
        }

        showPauseOverlay = visible
        updateVisibility()
        updatePauseBreathingAnimation()
        invalidate()
    }

    fun setEmulatorScreenAreas(topScreenArea: Rect?, bottomScreenArea: Rect?) {
        if (
            fastForwardIndicatorAnchorArea == topScreenArea &&
            pauseOverlayTopScreenArea == topScreenArea &&
            pauseOverlayBottomScreenArea == bottomScreenArea
        ) {
            return
        }

        fastForwardIndicatorAnchorArea = topScreenArea
        pauseOverlayTopScreenArea = topScreenArea
        pauseOverlayBottomScreenArea = bottomScreenArea
        if (showFastForwardIndicator || showPauseOverlay) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPauseOverlay(canvas)
        drawFastForwardIndicator(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePauseBreathingAnimation()
    }

    override fun onDetachedFromWindow() {
        pauseBreathingAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private fun drawPauseOverlay(canvas: Canvas) {
        if (!showPauseOverlay) {
            return
        }

        pauseOverlayPath.reset()
        pauseOverlayTopScreenArea?.let {
            pauseOverlayPath.addRect(it.x.toFloat(), it.y.toFloat(), it.right.toFloat(), it.bottom.toFloat(), Path.Direction.CW)
        }
        pauseOverlayBottomScreenArea?.let {
            pauseOverlayPath.addRect(it.x.toFloat(), it.y.toFloat(), it.right.toFloat(), it.bottom.toFloat(), Path.Direction.CW)
        }
        canvas.drawPath(pauseOverlayPath, pauseOverlayPaint)

        val topScreenArea = pauseOverlayTopScreenArea ?: return
        val centerX = topScreenArea.x + topScreenArea.width / 2f
        val centerY = topScreenArea.y + topScreenArea.height / 2f
        val iconAlpha = 0.64f + pauseBreathingProgress * 0.36f
        val iconScale = 0.92f + pauseBreathingProgress * 0.08f

        pauseIconPaint.alpha = (255 * iconAlpha).roundToInt()
        val canvasState = canvas.save()
        canvas.scale(iconScale, iconScale, centerX, centerY)
        drawPauseIconBar(canvas, centerX - pauseIconBarGapPx / 2f - pauseIconBarWidthPx, centerY)
        drawPauseIconBar(canvas, centerX + pauseIconBarGapPx / 2f, centerY)
        canvas.restoreToCount(canvasState)
        pauseIconPaint.alpha = 255
    }

    private fun drawPauseIconBar(canvas: Canvas, left: Float, centerY: Float) {
        pauseIconBounds.set(
            left,
            centerY - pauseIconBarHeightPx / 2f,
            left + pauseIconBarWidthPx,
            centerY + pauseIconBarHeightPx / 2f,
        )
        canvas.drawRoundRect(pauseIconBounds, pauseIconBarRadiusPx, pauseIconBarRadiusPx, pauseIconPaint)
    }

    private fun drawFastForwardIndicator(canvas: Canvas) {
        if (!showFastForwardIndicator) {
            return
        }

        val drawable = fastForwardIndicatorDrawable ?: return
        val anchorArea = fastForwardIndicatorAnchorArea
        val left = (anchorArea?.x ?: 0) + indicatorPaddingPx.toInt()
        val top = (anchorArea?.y ?: 0) + indicatorPaddingPx.toInt()
        val right = left + indicatorSizePx.toInt()
        val bottom = top + indicatorSizePx.toInt()
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hotCornerConfiguration.hasEnabledCorner) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val corner = findEnabledHotCorner(event.x, event.y) ?: return false

                downX = event.x
                downY = event.y
                pressedHotCorner = corner
                isTrackingHotCornerTouch = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val corner = pressedHotCorner
                if (corner != null && hasExceededTapSlop(event.x, event.y)) {
                    pressedHotCorner = null
                }

                return isTrackingHotCornerTouch
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pressedHotCorner = null
                return isTrackingHotCornerTouch
            }
            MotionEvent.ACTION_UP -> {
                val corner = pressedHotCorner
                val wasTrackingHotCornerTouch = isTrackingHotCornerTouch
                resetHotCornerTouch()

                if (corner != null && !hasExceededTapSlop(event.x, event.y)) {
                    performClick()
                    dispatchHotCornerClick(corner)
                }

                return wasTrackingHotCornerTouch
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasTrackingHotCornerTouch = isTrackingHotCornerTouch
                resetHotCornerTouch()
                return wasTrackingHotCornerTouch
            }
        }

        return isTrackingHotCornerTouch
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateVisibility() {
        isVisible = hotCornerConfiguration.hasEnabledCorner || showFastForwardIndicator || showPauseOverlay
    }

    private fun updatePauseBreathingAnimation() {
        if (showPauseOverlay && isAttachedToWindow) {
            if (!pauseBreathingAnimator.isStarted) {
                pauseBreathingAnimator.start()
            }
        } else {
            pauseBreathingAnimator.cancel()
            pauseBreathingProgress = 0f
        }
    }

    private fun dispatchHotCornerClick(corner: HotCorner) {
        val callback = hotCornerCallback ?: return
        when (corner) {
            HotCorner.TOP_LEFT -> callback.onTopLeftClicked()
            HotCorner.TOP_RIGHT -> callback.onTopRightClicked()
            HotCorner.BOTTOM_LEFT -> callback.onBottomLeftClicked()
            HotCorner.BOTTOM_RIGHT -> callback.onBottomRightClicked()
        }
    }

    private fun resetHotCornerTouch() {
        isTrackingHotCornerTouch = false
        pressedHotCorner = null
    }

    private fun hasExceededTapSlop(x: Float, y: Float): Boolean {
        return abs(x - downX) > touchSlopPx || abs(y - downY) > touchSlopPx
    }

    private fun findEnabledHotCorner(x: Float, y: Float): HotCorner? {
        if (!hotCornerConfiguration.enabled) {
            return null
        }

        return when {
            hotCornerConfiguration.topLeftEnabled && isInTopLeftHotCorner(x, y) -> HotCorner.TOP_LEFT
            hotCornerConfiguration.topRightEnabled && isInTopRightHotCorner(x, y) -> HotCorner.TOP_RIGHT
            hotCornerConfiguration.bottomLeftEnabled && isInBottomLeftHotCorner(x, y) -> HotCorner.BOTTOM_LEFT
            hotCornerConfiguration.bottomRightEnabled && isInBottomRightHotCorner(x, y) -> HotCorner.BOTTOM_RIGHT
            else -> null
        }
    }

    private fun isInTopLeftHotCorner(x: Float, y: Float): Boolean {
        return x <= hotCornerSizePx && y <= hotCornerSizePx
    }

    private fun isInTopRightHotCorner(x: Float, y: Float): Boolean {
        return x >= width - hotCornerSizePx && y <= hotCornerSizePx
    }

    private fun isInBottomLeftHotCorner(x: Float, y: Float): Boolean {
        return x <= hotCornerSizePx && y >= height - hotCornerSizePx
    }

    private fun isInBottomRightHotCorner(x: Float, y: Float): Boolean {
        return x >= width - hotCornerSizePx && y >= height - hotCornerSizePx
    }

    private fun dpToPixels(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }
}
