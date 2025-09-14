package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class HotCornerView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    
    // 热区大小（dp）
    private val hotCornerSizeDp = 75f
    
    // 热区大小（像素）
    private var hotCornerSizePx = 0f
    
    // 热区是否启用
    private var hotCornersEnabled = true
    
    // 热区回调接口
    interface HotCornerCallback {
        fun onTopLeftClicked()
        fun onTopRightClicked()
        fun onBottomLeftClicked()
        fun onBottomRightClicked()
    }
    
    private var hotCornerCallback: HotCornerCallback? = null
    
    init {
        // 将dp转换为px
        hotCornerSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            hotCornerSizeDp,
            context.resources.displayMetrics
        )
    }
    
    fun setHotCornerCallback(callback: HotCornerCallback?) {
        hotCornerCallback = callback
    }
    
    fun setHotCornersEnabled(enabled: Boolean) {
        hotCornersEnabled = enabled
        // 当禁用时，设置View为不可见，这样就不会遮挡屏幕按键
        visibility = if (enabled) View.VISIBLE else View.GONE
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 热区View不需要绘制任何内容，只需要处理触摸事件
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果热区未启用，不处理触摸事件
        if (!hotCornersEnabled) {
            return false
        }
        
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            
            // 检查点击是否在热区内
            when {
                isInTopLeftHotCorner(x, y) -> {
                    hotCornerCallback?.onTopLeftClicked()
                    return true
                }
                isInTopRightHotCorner(x, y) -> {
                    hotCornerCallback?.onTopRightClicked()
                    return true
                }
                isInBottomLeftHotCorner(x, y) -> {
                    hotCornerCallback?.onBottomLeftClicked()
                    return true
                }
                isInBottomRightHotCorner(x, y) -> {
                    hotCornerCallback?.onBottomRightClicked()
                    return true
                }
            }
        }
        
        // 如果不在热区内，不拦截触摸事件
        return false
    }
    
    private fun isInTopLeftHotCorner(x: Float, y: Float): Boolean {
        return x <= hotCornerSizePx && y <= hotCornerSizePx
    }
    
    private fun isInTopRightHotCorner(x: Float, y: Float): Boolean {
        val width = width.toFloat()
        return x >= width - hotCornerSizePx && y <= hotCornerSizePx
    }
    
    private fun isInBottomLeftHotCorner(x: Float, y: Float): Boolean {
        val height = height.toFloat()
        return x <= hotCornerSizePx && y >= height - hotCornerSizePx
    }
    
    private fun isInBottomRightHotCorner(x: Float, y: Float): Boolean {
        val width = width.toFloat()
        val height = height.toFloat()
        return x >= width - hotCornerSizePx && y >= height - hotCornerSizePx
    }
}
