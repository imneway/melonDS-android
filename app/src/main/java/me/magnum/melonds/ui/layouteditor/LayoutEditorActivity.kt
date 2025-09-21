package me.magnum.melonds.ui.layouteditor

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityLayoutEditorBinding
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.setBackgroundMode
import me.magnum.melonds.extensions.setLayoutOrientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import me.magnum.melonds.ui.common.TextInputDialog
import me.magnum.melonds.utils.getLayoutComponentName
import kotlin.math.max
import javax.inject.Inject

@AndroidEntryPoint
class LayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val KEY_LAYOUT_ID = "layout_id"

        private const val CONTROLS_SLIDE_ANIMATION_DURATION_MS = 100L
    }

    enum class MenuOption(@StringRes val stringRes: Int) {
        PROPERTIES(R.string.properties),
        BACKGROUNDS(R.string.background),
        REVERT(R.string.revert_changes),
        RESET(R.string.reset_default),
        SAVE_AND_EXIT(R.string.save_and_exit),
        EXIT_WITHOUT_SAVING(R.string.exit_without_saving)
    }

    @Inject
    lateinit var screenUnitsConverter: ScreenUnitsConverter
    @Inject
    lateinit var picasso: Picasso

    private val viewModel: LayoutEditorViewModel by viewModels()
    private lateinit var binding: ActivityLayoutEditorBinding
    private lateinit var handler: Handler
    private var areBottomControlsShown = true
    private var areScalingControlsShown = true
    private var areTopScalingControlsShown = false
    private var selectedViewMinSize = 0
    private var isMenuAtTop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        handler = Handler(mainLooper)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openMenu()
            }
        })

        binding.buttonAddButton.setOnClickListener {
            openButtonsMenu()
        }
        binding.buttonMenu.setOnClickListener {
            openMenu()
        }
        binding.buttonDeleteButton.setOnClickListener {
            binding.viewLayoutEditor.deleteSelectedView()
        }

        // 设置方向移动按钮的点击事件
        binding.buttonMoveUp.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(0, -1)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveDown.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(0, 1)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveLeft.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(-1, 0)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveRight.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(1, 0)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }

        // 设置顶部菜单按钮的点击事件
        binding.buttonDeleteButtonTop.setOnClickListener {
            binding.viewLayoutEditor.deleteSelectedView()
        }
        binding.buttonMoveUpTop.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(0, -1)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveDownTop.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(0, 1)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveLeftTop.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(-1, 0)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }
        binding.buttonMoveRightTop.setOnClickListener {
            binding.viewLayoutEditor.moveSelectedViewInDp(1, 0)
            updatePositionDisplay()
            checkAndRepositionMenu()
        }

        binding.viewLayoutEditor.setLayoutComponentViewBuilderFactory(EditorLayoutComponentViewBuilderFactory())
        binding.viewLayoutEditor.setOnClickListener {
            if (areBottomControlsShown)
                hideBottomControls()
            else
                showBottomControls()
        }
        binding.viewLayoutEditor.setOnViewSelectedListener { _, scale, maxSize, minSize ->
            hideBottomControls()
            determineAndShowMenu(scale, maxSize, minSize)
            updatePositionDisplay()
        }
        binding.viewLayoutEditor.setOnViewDeselectedListener {
            hideScalingControls()
            hideTopScalingControls()
        }
        binding.seekBarScaling.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress / binding.seekBarScaling.max.toFloat()
                binding.textSize.text = ((binding.seekBarScaling.max - selectedViewMinSize) * scale + selectedViewMinSize).toInt().toString()
                binding.viewLayoutEditor.scaleSelectedView(scale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 允许透明度从 0% 到 100%
                binding.textOpacity.text = "$progress%"
                binding.viewLayoutEditor.setSelectedViewOpacity(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        // 顶部菜单的 SeekBar 监听器
        binding.seekBarScalingTop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress / binding.seekBarScalingTop.max.toFloat()
                binding.textSizeTop.text = ((binding.seekBarScalingTop.max - selectedViewMinSize) * scale + selectedViewMinSize).toInt().toString()
                binding.viewLayoutEditor.scaleSelectedView(scale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.seekBarOpacityTop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 允许透明度从 0% 到 100%
                binding.textOpacityTop.text = "$progress%"
                binding.viewLayoutEditor.setSelectedViewOpacity(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.viewLayoutEditor.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val oldWith = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            val viewWidth = right - left
            val viewHeight = bottom - top

            if (viewWidth != oldWith || viewHeight != oldHeight) {
                storeLayoutChanges()
                viewModel.setCurrentUiSize(viewWidth, viewHeight)
            }
        }

        setupFullscreen()
        hideScalingControls(false)
        hideTopScalingControls(false)
        updateOrientation(resources.configuration)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLayout.collect {
                    if (it == null) {
                        binding.viewLayoutEditor.destroyLayout()
                    } else {
                        Log.d("LayoutEditorActivity", "Instantiating layout. On main thread: ${mainLooper.isCurrentThread}")
                        handler.removeCallbacksAndMessages(null)
                        handler.post {
                            binding.viewLayoutEditor.instantiateLayout(it.layout)
                            setLayoutOrientation(it.orientation)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.background.collect {
                    it?.let {
                        updateBackground(it)
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@LayoutEditorActivity).windowLayoutInfo(this@LayoutEditorActivity).collect { info ->
                    val folds = info.displayFeatures.mapNotNull {
                        if (it is FoldingFeature) {
                            ScreenFold(
                                orientation = if (it.orientation == FoldingFeature.Orientation.HORIZONTAL) Orientation.LANDSCAPE else Orientation.PORTRAIT,
                                type = if (it.isSeparating) ScreenFold.FoldType.SEAMLESS else ScreenFold.FoldType.GAP,
                                foldBounds = Rect(it.bounds.left, it.bounds.top, it.bounds.width(), it.bounds.height())
                            )
                        } else {
                            null
                        }
                    }

                    storeLayoutChanges()
                    viewModel.setScreenFolds(folds)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        return if (binding.viewLayoutEditor.handleKeyDown(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setupFullscreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        storeLayoutChanges()
        updateOrientation(newConfig)
    }

    private fun updateOrientation(configuration: Configuration) {
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Orientation.PORTRAIT
        } else {
            Orientation.LANDSCAPE
        }
        viewModel.setCurrentSystemOrientation(orientation)
    }

    private fun storeLayoutChanges() {
        if (binding.viewLayoutEditor.isModifiedByUser()) {
            val layoutComponents = binding.viewLayoutEditor.buildCurrentLayout()
            viewModel.saveLayoutToCurrentConfiguration(layoutComponents)
        }
    }

    private fun setupFullscreen() {
        window.insetsControllerCompat?.let {
            it.hide(WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateBackground(background: RuntimeBackground) {
        picasso.load(background.background?.uri).into(binding.imageBackground, object : Callback {
            override fun onSuccess() {
                binding.imageBackground.setBackgroundMode(background.mode)
            }

            override fun onError(e: java.lang.Exception?) {
                e?.printStackTrace()
                Toast.makeText(this@LayoutEditorActivity, R.string.layout_background_load_failed, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showBottomControls(animate: Boolean = true) {
        if (areBottomControlsShown) {
            return
        }

        binding.layoutControls.clearAnimation()
        if (animate) {
            binding.layoutControls
                .animate()
                .y(binding.layoutControls.bottom.toFloat() - binding.layoutControls.height.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withStartAction {
                    binding.layoutControls.isVisible = true
                }
                .start()
        } else {
            binding.layoutControls.isVisible = true
        }

        areBottomControlsShown = true
    }

    private fun hideBottomControls(animate: Boolean = true) {
        if (!areBottomControlsShown) {
            return
        }

        if (animate) {
            binding.layoutControls.clearAnimation()
            if (animate) {
                binding.layoutControls.animate()
                    .y(binding.layoutControls.bottom.toFloat())
                    .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                    .withEndAction {
                        binding.layoutControls.isGone = true
                    }
                    .start()
            }
        } else {
            binding.layoutControls.isGone = true
        }

        areBottomControlsShown = false
    }

    private fun showScalingControls(currentScale: Float, maxSize: Int, minSize: Int, animate: Boolean = true) {
        binding.seekBarScaling.apply {
            max = maxSize
            progress = (currentScale * maxSize).toInt()
        }

        // 设置透明度控制
        val currentOpacity = binding.viewLayoutEditor.getSelectedViewOpacity()
        binding.seekBarOpacity.progress = currentOpacity
        binding.textOpacity.text = "$currentOpacity%"

        selectedViewMinSize = minSize

        if (areScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScaling
                .animate()
                .y(binding.layoutScaling.bottom.toFloat() - binding.layoutScaling.height.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withStartAction {
                    binding.layoutScaling.isVisible = true
                }
                .start()
        } else {
            binding.layoutScaling.isVisible = true
        }

        areScalingControlsShown = true
    }

    private fun hideScalingControls(animate: Boolean = true) {
        if (!areScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScaling
                .animate()
                .y(binding.layoutScaling.bottom.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withEndAction {
                    binding.layoutScaling.isInvisible = true
                }
                .start()
        } else {
            binding.layoutScaling.isInvisible = true
        }

        areScalingControlsShown = false
    }

    private fun updatePositionDisplay() {
        val position = binding.viewLayoutEditor.getSelectedViewPositionInDp()
        binding.textPosition.text = position
        binding.textPositionTop.text = position
    }

    private fun determineAndShowMenu(scale: Float, maxSize: Int, minSize: Int) {
        val isInUpperHalf = binding.viewLayoutEditor.isSelectedViewInUpperHalf()
        
        if (isInUpperHalf) {
            // 控件在上半部分，菜单显示在底部
            hideTopScalingControls()
            showScalingControls(scale, maxSize, minSize)
            isMenuAtTop = false
        } else {
            // 控件在下半部分，菜单显示在顶部
            hideScalingControls()
            showTopScalingControls(scale, maxSize, minSize)
            isMenuAtTop = true
        }
    }

    private fun showTopScalingControls(currentScale: Float, maxSize: Int, minSize: Int, animate: Boolean = true) {
        binding.seekBarScalingTop.apply {
            max = maxSize
            progress = (currentScale * maxSize).toInt()
        }

        // 设置透明度控制
        val currentOpacity = binding.viewLayoutEditor.getSelectedViewOpacity()
        binding.seekBarOpacityTop.progress = currentOpacity
        binding.textOpacityTop.text = "$currentOpacity%"

        selectedViewMinSize = minSize

        if (areTopScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScalingTop
                .animate()
                .y(0f)
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withStartAction {
                    binding.layoutScalingTop.isVisible = true
                }
                .start()
        } else {
            binding.layoutScalingTop.isVisible = true
        }

        areTopScalingControlsShown = true
    }

    private fun hideTopScalingControls(animate: Boolean = true) {
        if (!areTopScalingControlsShown) {
            return
        }

        if (animate) {
            binding.layoutScalingTop
                .animate()
                .y(-binding.layoutScalingTop.height.toFloat())
                .setDuration(CONTROLS_SLIDE_ANIMATION_DURATION_MS)
                .withEndAction {
                    binding.layoutScalingTop.isGone = true
                }
                .start()
        } else {
            binding.layoutScalingTop.isGone = true
        }

        areTopScalingControlsShown = false
    }

    private fun checkAndRepositionMenu() {
        if (binding.viewLayoutEditor.hasSelectedView()) {
            val isInUpperHalf = binding.viewLayoutEditor.isSelectedViewInUpperHalf()
            
            // 如果控件位置发生了变化，需要重新定位菜单
            if ((isInUpperHalf && isMenuAtTop) || (!isInUpperHalf && !isMenuAtTop)) {
                // 需要切换菜单位置
                val currentScale = binding.seekBarScaling.progress / binding.seekBarScaling.max.toFloat()
                val maxSize = binding.seekBarScaling.max
                val minSize = selectedViewMinSize
                
                determineAndShowMenu(currentScale, maxSize, minSize)
            }
        }
    }

    private fun openButtonsMenu() {
        hideBottomControls()
        val instantiatedComponents = binding.viewLayoutEditor.getInstantiatedComponents()
        val componentsToShow = LayoutComponent.entries.filterNot { instantiatedComponents.contains(it) }

        val dialogBuilder = AlertDialog.Builder(this)
                .setTitle(R.string.choose_component)
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }

        if (componentsToShow.isNotEmpty()) {
            dialogBuilder.setItems(componentsToShow.map { getString(getLayoutComponentName(it)) }.toTypedArray()) { _, which ->
                val component = componentsToShow[which]
                binding.viewLayoutEditor.addLayoutComponent(component)
            }
        } else {
            dialogBuilder.setMessage(R.string.no_more_components)
        }

        dialogBuilder.show()
    }

    private fun openMenu() {
        storeLayoutChanges()
        val values = MenuOption.entries
        val options = Array(values.size) { i -> getString(values[i].stringRes) }

        AlertDialog.Builder(this)
                .setTitle(R.string.menu)
                .setItems(options) { _, which -> onMenuOptionSelected(values[which]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun onMenuOptionSelected(option: MenuOption) {
        when (option) {
            MenuOption.PROPERTIES -> openPropertiesDialog()
            MenuOption.BACKGROUNDS -> openBackgroundsConfigDialog()
            MenuOption.REVERT -> viewModel.revertLayoutChanges()
            MenuOption.RESET -> viewModel.resetLayout()
            MenuOption.SAVE_AND_EXIT -> {
                if (viewModel.currentLayoutHasName()) {
                    saveLayoutAndExit()
                } else {
                    showLayoutNameInputDialog()
                }
            }
            MenuOption.EXIT_WITHOUT_SAVING -> finish()
        }
    }

    private fun openPropertiesDialog() {
        storeLayoutChanges()
        val layoutConfiguration = viewModel.getCurrentLayoutConfiguration() ?: return
        LayoutPropertiesDialog.newInstance(layoutConfiguration).show(supportFragmentManager, null)
    }

    private fun openBackgroundsConfigDialog() {
        storeLayoutChanges()
        LayoutBackgroundDialog.newInstance().show(supportFragmentManager, null)
    }

    private fun showLayoutNameInputDialog() {
        TextInputDialog.Builder()
            .setTitle(getString(R.string.layout_name))
            .setText(getString(R.string.custom_layout_default_name))
            .setOnConfirmListener {
                viewModel.setCurrentLayoutName(it)
                saveLayoutAndExit()
            }
            .build()
            .show(supportFragmentManager, null)
    }

    private fun saveLayoutAndExit() {
        storeLayoutChanges()
        viewModel.saveCurrentLayout()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        picasso.cancelRequest(binding.imageBackground)
    }
}