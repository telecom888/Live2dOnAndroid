package com.bangdream.pet.chat

import com.bangdream.pet.voice.VoicePlayer
import kotlinx.coroutines.Job

/** 桌面运行时单例：管理当前对话任务，供壁纸长按打断。 */
object PetRuntime {
    @Volatile var activeJob: Job? = null
    @Volatile var onStopped: (() -> Unit)? = null

    fun stopAll() {
        activeJob?.cancel()
        activeJob = null
        VoicePlayer.stop()
        onStopped?.invoke()
    }

    fun clearCallbacks() {
        onStopped = null
    }
}
