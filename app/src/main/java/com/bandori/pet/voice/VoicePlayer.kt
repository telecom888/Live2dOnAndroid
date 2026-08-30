package com.bandori.pet.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream

/** 播放 WAV/音频字节的轻量封装（线程安全）。 */
object VoicePlayer {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var playing = false
    private val lock = Any()

    fun play(context: Context, bytes: ByteArray): Boolean = synchronized(lock) {
        stopInternal()
        runCatching {
            val dir = File(context.cacheDir, "voice").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.wav")
            FileOutputStream(file).use { it.write(bytes) }
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { stopInternal() }
            p.setOnErrorListener { _, _, _ -> stopInternal(); true }
            p.prepare()
            p.start()
            player = p
            playing = true
            true
        }.getOrDefault(false)
    }

    fun isPlaying(): Boolean = synchronized(lock) { playing }

    fun stop() = synchronized(lock) { stopInternal() }

    private fun stopInternal() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = false
    }
}
