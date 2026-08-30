package com.bangdream.pet.ui.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 运行时视觉守卫（参考 BiliPai RuntimeVisualGuardPolicy 的静态部分）：
 * 低内存设备或不支持真毛玻璃的平台自动降级，避免液态玻璃拖垮性能。
 */
object VisualGuard {
    fun supportsLiquidGlass(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        return !lowRam && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
