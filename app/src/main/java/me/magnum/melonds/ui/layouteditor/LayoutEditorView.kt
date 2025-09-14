package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.ui.common.LayoutComponentView
import me.magnum.melonds.ui.common.LayoutView
import kotlin.math.*

typealias ViewSelectedListener = ((LayoutComponentView, currentScale: Float, maxSize: Int, minSize: Int) -> Unit)

class LayoutEditorView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private var onViewSelectedListener: ViewSelectedListener? = null
    private var onViewDeselectedListener: ((LayoutComponentView) -> Unit)? = null
    private var otherClickListener: OnClickListener? = null
    private val defaultComponentWidth by lazy { screenUnitsConverter.dpToPixels(100f).toInt() }
    private val minComponentSize by lazy { screenUnitsConverter.dpToPixels(30f).toInt() }
    private var selectedView: LayoutComponentView? = null
    private var selectedViewAnchor = Anchor.TOP_LEFT
    private var modifiedByUser = false
    private val componentOpacityMap = mutableMapOf<LayoutComponent, Int>()
    private var globalOpacity = 50 // 默认全局透明度

    init {
        super.setOnClickListener {
            if (selectedView != null) {
                deselectCurrentView()
            } else {
                otherClickListener?.onClick(it)
            }
        }
    }

    override fun instantiateLayout(layoutConfiguration: UILayout) {
        super.instantiateLayout(layoutConfiguration)
        // 初始化透明度映射
        layoutConfiguration.components?.forEach { component ->
            componentOpacityMap[component.component] = component.opacity
        }
        // 获取全局透明度设置
        updateGlobalOpacity()
        modifiedByUser = false
    }

    fun setOnViewSelectedListener(listener: ViewSelectedListener) {
        onViewSelectedListener = listener
    }

    fun setOnViewDeselectedListener(listener: (LayoutComponentView) -> Unit) {
        onViewDeselectedListener = listener
    }

    fun addLayoutComponent(component: LayoutComponent) {
        val componentBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(component)
        val componentHeight = defaultComponentWidth / componentBuilder.getAspectRatio()
        // 新添加的控件默认透明度为 100%
        componentOpacityMap[component] = 100
        val componentView = addPositionedLayoutComponent(PositionedLayoutComponent(Rect(0, 0, defaultComponentWidth, componentHeight.toInt()), component, 100))
        views[component] = componentView
        modifiedByUser = true
    }

    fun isModifiedByUser(): Boolean {
        return modifiedByUser
    }

    fun buildCurrentLayout(): List<PositionedLayoutComponent> {
        return views.values.map { 
            val opacity = componentOpacityMap[it.component] ?: 100
            PositionedLayoutComponent(it.getRect(), it.component, opacity)
        }
    }

    fun handleKeyDown(event: KeyEvent): Boolean {
        val currentlySelectedView = selectedView ?: return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> dragView(currentlySelectedView, 0f, -1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> dragView(currentlySelectedView, 0f, 1f)
            KeyEvent.KEYCODE_DPAD_LEFT -> dragView(currentlySelectedView, -1f, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> dragView(currentlySelectedView, 1f, 0f)
            else -> return false
        }

        return true
    }

    override fun setOnClickListener(l: OnClickListener?) {
        otherClickListener = l
    }

    override fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
        super.onLayoutComponentViewAdded(layoutComponentView)
        setupDragHandler(layoutComponentView)
        // 在编辑界面中，未选中的控件显示为半透明，选中的控件显示为不透明
        // 但实际保存的透明度值不受影响
        updateViewAlphaForEditor(layoutComponentView)
    }

    private fun setupDragHandler(layoutComponentView: LayoutComponentView) {
        layoutComponentView.view.setOnTouchListener(object : OnTouchListener {
            private var dragging = false

            private var downOffsetX = -1f
            private var downOffsetY = -1f

            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                if (view == null)
                    return false

                if (selectedView != null) {
                    deselectCurrentView()
                }

                return when (motionEvent?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downOffsetX = motionEvent.x
                        downOffsetY = motionEvent.y
                        view.alpha = 1f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val distance = sqrt((motionEvent.x - downOffsetX).pow(2f) + (motionEvent.y - downOffsetY).pow(2f))
                            if (distance >= 25) {
                                dragging = true
                            }
                        } else {
                            dragView(layoutComponentView, motionEvent.x - downOffsetX, motionEvent.y - downOffsetY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            selectView(layoutComponentView)
                        } else {
                            view.alpha = 0.5f
                            dragging = false
                        }
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun selectView(view: LayoutComponentView) {
        val anchorDistances = mutableMapOf<Anchor, Double>()
        anchorDistances[Anchor.TOP_LEFT] = view.getPosition().x.toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.TOP_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_LEFT] = view.getPosition().x.toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)

        var anchor = Anchor.TOP_LEFT
        var minDistance = Double.MAX_VALUE
        anchorDistances.keys.forEach {
            if (anchorDistances[it]!! < minDistance) {
                minDistance = anchorDistances[it]!!
                anchor = it
            }
        }

        selectedViewAnchor = anchor
        selectedView = view

        // 更新所有控件的显示透明度
        views.values.forEach { updateViewAlphaForEditor(it) }

        val layoutAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = view.aspectRatio
        val currentConstrainedDimension: Int
        val maxDimension: Int

        if (layoutAspectRatio > selectedViewAspectRatio) {
            maxDimension = height / 2  // 将最大值改为原来的一半
            currentConstrainedDimension = view.getHeight()
        } else {
            maxDimension = width / 2   // 将最大值改为原来的一半
            currentConstrainedDimension = view.getWidth()
        }

        val viewScale = (currentConstrainedDimension - minComponentSize) / (maxDimension - minComponentSize).toFloat()
        onViewSelectedListener?.invoke(view, viewScale, maxDimension, minComponentSize)
    }

    private fun deselectCurrentView() {
        selectedView?.let {
            updateViewAlphaForEditor(it)
            onViewDeselectedListener?.invoke(it)
        }
        selectedView = null
    }

    fun deleteSelectedView() {
        val currentlySelectedView = selectedView ?: return
        deselectCurrentView()
        removeView(currentlySelectedView.view)
        views.remove(currentlySelectedView.component)
    }

    private fun dragView(view: LayoutComponentView, offsetX: Float, offsetY: Float) {
        val currentPosition = view.getPosition()
        val finalX = min(max(currentPosition.x + offsetX, 0f), width - view.getWidth().toFloat())
        val finalY = min(max(currentPosition.y + offsetY, 0f), height - view.getHeight().toFloat())
        view.setPosition(Point(finalX.toInt(), finalY.toInt()))
        modifiedByUser = true
    }

    fun scaleSelectedView(newScale: Float) {
        val currentlySelectedView = selectedView ?: return

        val screenAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = currentlySelectedView.aspectRatio
        val newViewWidth: Int
        val newViewHeight: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            // The scale range must go from minComponentSize to height/2
            val maxHeight = height / 2
            val scaledHeight = ((maxHeight - minComponentSize) * newScale + minComponentSize).roundToInt()
            newViewWidth = (scaledHeight * selectedViewAspectRatio).toInt()
            newViewHeight = scaledHeight
        } else {
            // The scale range must go from minComponentSize to width/2
            val maxWidth = width / 2
            val scaledWidth = ((maxWidth - minComponentSize) * newScale + minComponentSize).roundToInt()
            newViewWidth = scaledWidth
            newViewHeight = (scaledWidth / selectedViewAspectRatio).toInt()
        }

        val viewPosition = currentlySelectedView.getPosition()
        var viewX: Int
        var viewY: Int

        if (selectedViewAnchor == Anchor.TOP_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.TOP_RIGHT) {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.BOTTOM_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY < 0) {
                viewY = 0
            }
        } else {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY < 0) {
                viewY = 0
            }
        }
        currentlySelectedView.setPositionAndSize(Point(viewX, viewY), newViewWidth, newViewHeight)
        modifiedByUser = true
    }

    fun setSelectedViewOpacity(opacity: Int) {
        val currentlySelectedView = selectedView ?: return
        // 允许透明度从 0% 到 100%
        // 保存透明度值到映射中
        componentOpacityMap[currentlySelectedView.component] = opacity
        // 立即更新显示
        updateViewAlphaForEditor(currentlySelectedView)
        modifiedByUser = true
    }

    fun getSelectedViewOpacity(): Int {
        val currentlySelectedView = selectedView ?: return 100
        // 返回实际保存的透明度值
        return componentOpacityMap[currentlySelectedView.component] ?: 100
    }

    private fun updateViewAlphaForEditor(layoutComponentView: LayoutComponentView) {
        // 在编辑界面中，显示最终透明度（全局透明度 × 控件独立透明度），但选中的控件稍微亮一些以便识别
        val componentOpacity = componentOpacityMap[layoutComponentView.component] ?: 100
        val globalAlpha = globalOpacity / 100f
        val componentAlpha = componentOpacity / 100f
        val finalAlpha = globalAlpha * componentAlpha
        
        if (selectedView == layoutComponentView) {
            // 选中的控件稍微亮一些，但不超过 1.0f
            layoutComponentView.view.alpha = min(1.0f, finalAlpha + 0.2f)
        } else {
            // 未选中的控件显示最终透明度
            layoutComponentView.view.alpha = finalAlpha
        }
    }

    private fun updateGlobalOpacity() {
        // 获取全局透明度设置
        try {
            // 这里我们需要同步获取全局透明度，因为这是一个简单的设置值
            // 我们可以通过 SharedPreferences 直接获取
            val sharedPrefs = context.getSharedPreferences("me.magnum.melonds_preferences", Context.MODE_PRIVATE)
            globalOpacity = sharedPrefs.getInt("input_opacity", 50)
        } catch (e: Exception) {
            // 如果获取失败，使用默认值
            globalOpacity = 50
        }
    }
}