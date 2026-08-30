package com.bangdream.pet.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效令牌（参考 BiliPai design-system AppMotionTokens / AppMotionEasing）。
 * 本应用固定采用 Material3 基准时长：standard 200ms / emphasized 300ms / expressive 180ms。
 */
object AppMotionEasing {
    val EmphasizedEnter: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EmphasizedExit: Easing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
    val Continuity: Easing = CubicBezierEasing(0.20f, 0.90f, 0.22f, 1.00f)
    val GentleEnter: Easing = CubicBezierEasing(0.18f, 0.80f, 0.20f, 1.00f)
    /** 景深返回清晰：ease-in 向 0，先留住模糊再柔化。 */
    val SoftClear: Easing = CubicBezierEasing(0.40f, 0.00f, 0.55f, 0.30f)
}

/** 标准过渡（Material3 200ms，Continuity）。 */
fun <T> standardTween(): TweenSpec<T> = tween(
    durationMillis = 200,
    easing = AppMotionEasing.Continuity,
)

/** 强调入场（Material3 300ms，EmphasizedEnter）。 */
fun <T> emphasizedTween(): TweenSpec<T> = tween(
    durationMillis = 300,
    easing = AppMotionEasing.EmphasizedEnter,
)

/** 表达性离场（Material3 180ms，EmphasizedExit）。 */
fun <T> expressiveTween(): TweenSpec<T> = tween(
    durationMillis = 180,
    easing = AppMotionEasing.EmphasizedExit,
)

/** 柔和落位。 */
fun <T> softLandingSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
)

/** 按压反馈。 */
fun pressFeedbackSpring(): SpringSpec<Float> = spring(
    dampingRatio = 1f,
    stiffness = 1000f,
    visibilityThreshold = 0.001f,
)

/** 交互吸附。 */
fun interactiveSnapSpring(): SpringSpec<Float> = spring(
    dampingRatio = 0.78f,
    stiffness = 420f,
)
