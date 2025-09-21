package me.magnum.melonds.ui.emulator.input

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.annotation.RequiresApi
import me.magnum.melonds.common.vibration.TouchVibrator

abstract class FeedbackInputHandler(inputListener: IInputListener, private val enableHapticFeedback: Boolean, private val touchVibrator: TouchVibrator) : BaseInputHandler(inputListener) {

    enum class HapticFeedbackType {
        KEY_PRESS,
        KEY_RELEASE
    }

    protected fun performHapticFeedback(view: View, type: HapticFeedbackType) {
        if (enableHapticFeedback) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 使用 VibrationEffect 的预定义效果
                val vibrator = view.context.getSystemService(Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    val effect = when (type) {
                        HapticFeedbackType.KEY_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        HapticFeedbackType.KEY_RELEASE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    }
                    vibrator.vibrate(effect)
                }
            } else {
                // 对于旧版本，回退到系统触觉反馈
                val feedbackType = when (type) {
                    HapticFeedbackType.KEY_PRESS -> android.view.HapticFeedbackConstants.KEYBOARD_TAP
                    HapticFeedbackType.KEY_RELEASE -> android.view.HapticFeedbackConstants.CLOCK_TICK
                }
                view.performHapticFeedback(feedbackType)
            }
        }
    }
}