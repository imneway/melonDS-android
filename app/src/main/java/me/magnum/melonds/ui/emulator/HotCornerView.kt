package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rect

class HotCornerView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface HotCornerCallback {
        fun onTopLeftClicked()
        fun onTopRightClicked()
        fun onBottomLeftClicked()
        fun onBottomRightClicked()
        fun onHotCornerReleased()
    }

    private val hotCornerSizePx = dpToPixels(75f)
    private val indicatorSizePx = dpToPixels(32f)
    private val indicatorPaddingPx = dpToPixels(8f)
    private val fastForwardIndicatorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_fast_forward_indicator)?.let {
        DrawableCompat.wrap(it.mutate())
    }

    private var hotCornerCallback: HotCornerCallback? = null
    private var hotCornersEnabled = true
    private var showFastForwardIndicator = false
    private var fastForwardIndicatorAnchorArea: Rect? = null
    private var pressedInHotCorner = false

    fun setHotCornerCallback(callback: HotCornerCallback?) {
        hotCornerCallback = callback
    }

    fun setHotCornersEnabled(enabled: Boolean) {
        hotCornersEnabled = enabled
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

    fun setFastForwardIndicatorAnchorArea(anchorArea: Rect?) {
        if (fastForwardIndicatorAnchorArea == anchorArea) {
            return
        }

        fastForwardIndicatorAnchorArea = anchorArea
        if (showFastForwardIndicator) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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
        if (!hotCornersEnabled) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                val callback = hotCornerCallback
                when {
                    isInTopLeftHotCorner(x, y) -> callback?.onTopLeftClicked()
                    isInTopRightHotCorner(x, y) -> callback?.onTopRightClicked()
                    isInBottomLeftHotCorner(x, y) -> callback?.onBottomLeftClicked()
                    isInBottomRightHotCorner(x, y) -> callback?.onBottomRightClicked()
                    else -> {
                        pressedInHotCorner = false
                        return false
                    }
                }

                pressedInHotCorner = true
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (pressedInHotCorner) {
                    pressedInHotCorner = false
                    hotCornerCallback?.onHotCornerReleased()
                    return true
                }
            }
        }

        return false
    }

    private fun updateVisibility() {
        isVisible = hotCornersEnabled || showFastForwardIndicator
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
