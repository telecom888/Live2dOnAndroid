package com.bangdream.pet.ui.design

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** 毛玻璃：Haze 毛玻璃 + 半透明底色 + 噪点（参考 BiliPai BlurStyles：模糊 + 半透明底色 + 顶部高光）。 */
@Composable
fun rememberLiquidGlassState(): HazeState = remember { HazeState() }

/**
 * 给前景层（顶栏/底栏/面板）叠加毛玻璃效果。
 * @param enabled 关闭时回退为半透明纯色，保证低端机/关闭开关时依然可读。
 * @param backgroundAlpha 半透明底色不透明度。
 * @param blurRadius 模糊半径。
 * @param tintAlpha 玻璃着色强度。
 */
@Composable
fun Modifier.appLiquidGlass(
    state: HazeState,
    enabled: Boolean = true,
    backgroundAlpha: Float = 0.55f,
    blurRadius: Dp = 24.dp,
    tintAlpha: Float = 0.12f,
): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return if (enabled) {
        this.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = surface.copy(alpha = backgroundAlpha),
                tint = HazeTint(surface.copy(alpha = tintAlpha)),
                blurRadius = blurRadius,
                noiseFactor = 0.06f,
            ),
        )
    } else {
        this.background(surface.copy(alpha = backgroundAlpha))
    }
}

/** 标记需要被模糊的背景内容（列表/舞台等）。 */
fun Modifier.appHazeSource(state: HazeState): Modifier = this.hazeSource(state)
