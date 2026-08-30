package com.bangdream.pet.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 入场动效（参考 BiliPai AppEntranceMotion：淡入 + 上移，EmphasizedEnter）。
 * 首次组合时执行一次。
 */
fun Modifier.appEntrance(
    delayMillis: Int = 0,
    startAlpha: Float = 0f,
    startTranslationY: Float = 16f,
): Modifier = composed {
    val alpha = remember { Animatable(startAlpha) }
    val offsetY = remember { Animatable(startTranslationY) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, emphasizedTween()) }
        launch { offsetY.animateTo(0f, emphasizedTween()) }
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}
