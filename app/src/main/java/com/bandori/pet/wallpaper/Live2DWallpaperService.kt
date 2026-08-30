package com.bandori.pet.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.bandori.pet.RenderSettings
import com.bandori.pet.KEY_FPS_DISPLAY_ENABLED
import com.bandori.pet.KEY_FPS_LIMIT
import com.bandori.pet.KEY_RENDER_RESOLUTION
import com.bandori.pet.KEY_SELECTED_CHARACTER_ID
import com.bandori.pet.KEY_SELECTED_MODEL_ASSET_PATH
import com.bandori.pet.KEY_VSYNC_ENABLED
import com.bandori.pet.KEY_WALLPAPER_BACKGROUND_URI
import com.bandori.pet.KEY_WALLPAPER_ENABLED
import com.bandori.pet.KEY_WALLPAPER_OFFSET_X
import com.bandori.pet.KEY_WALLPAPER_OFFSET_Y
import com.bandori.pet.KEY_WALLPAPER_SCALE
import com.bandori.pet.SETTINGS_PREFS
import com.bandori.pet.isWallpaperEnabled
import android.widget.Toast
import com.bandori.pet.chat.PetRuntime
import com.bandori.pet.chat.WallpaperBubbleService
import com.bandori.pet.chat.WallpaperChatActivity
import com.bandori.pet.loadBubbleEnabled
import com.bandori.pet.loadBuiltinVoiceEnabled
import com.bandori.pet.loadBuiltinVoiceLanguage
import com.bandori.pet.loadIdleAnimationEnabled
import com.bandori.pet.loadSelectedCharacterId
import com.bandori.pet.loadIdleAnimations
import com.bandori.pet.loadIdleIntervalMs
import com.bandori.pet.loadSwipeAnimationEnabled
import com.bandori.pet.loadTouchAnimationEnabled
import com.bandori.pet.loadTouchAnimations
import com.bandori.pet.loadPersistedModelChoice
import com.bandori.pet.loadWallpaperBackgroundUri
import com.bandori.pet.loadWallpaperTransform
import com.bandori.pet.live2d.AssetSync
import com.bandori.pet.voice.BuiltinVoiceManager
import com.bandori.pet.voice.VoicePlayer
import com.bandori.pet.live2d.NativeLive2D
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class Live2DWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = Live2DEngine()

    private inner class Live2DEngine : Engine(), SurfaceHolder.Callback {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var handle = 0L
        private var runtimeRoot: String? = null
        private var surfaceReady = false
        private var visible = false
        private var loading = false
        private var width = 1
        private var height = 1
        private var loadGeneration = 0
        private var surfaceHolderRef: SurfaceHolder? = null
        private var settingsPreferences: SharedPreferences? = null
        private var restartJob: Job? = null
        private var gestureHandler: WallpaperGestureHandler? = null
        private var idleJob: Job? = null
        private var lastInteractionAt = 0L
        private val renderSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            scope.launch {
                when (key) {
                    KEY_FPS_DISPLAY_ENABLED -> {
                        if (handle != 0L) {
                            NativeLive2D.setFpsDisplayEnabled(
                                handle,
                                preferences.getBoolean(KEY_FPS_DISPLAY_ENABLED, false),
                            )
                        }
                    }
                    KEY_RENDER_RESOLUTION -> {
                        if (handle != 0L) {
                            NativeLive2D.setRenderScale(handle, RenderSettings.load(applicationContext).renderResolution.scale)
                        }
                    }
                    KEY_FPS_LIMIT, KEY_VSYNC_ENABLED -> if (handle != 0L) {
                        val settings = RenderSettings.load(applicationContext)
                        NativeLive2D.setRenderOptions(handle, settings.fpsLimit, settings.vsyncEnabled)
                    }
                    KEY_WALLPAPER_OFFSET_X, KEY_WALLPAPER_OFFSET_Y, KEY_WALLPAPER_SCALE -> applyWallpaperTransform()
                    KEY_SELECTED_CHARACTER_ID,
                    KEY_SELECTED_MODEL_ASSET_PATH,
                    KEY_WALLPAPER_BACKGROUND_URI -> scheduleRendererRestart()
                    KEY_WALLPAPER_ENABLED -> {
                        if (isWallpaperEnabled(applicationContext)) ensureRenderer() else stopRenderer()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolderRef = surfaceHolder
            settingsPreferences = applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).also {
                it.registerOnSharedPreferenceChangeListener(renderSettingsListener)
            }
            setTouchEventsEnabled(true)
            gestureHandler = WallpaperGestureHandler(
                applicationContext,
                onTap = { playTouchAction() },
                onSwipe = { nx, ny ->
                    if (loadSwipeAnimationEnabled(applicationContext)) playTouchAction()
                    if (RenderSettings.load(applicationContext).gazeFollowEnabled) {
                        NativeLive2D.lookAt(handle, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
                    }
                },
                onDoubleTap = { _, _ -> openChatInput() },
                onLongPress = {
                    PetRuntime.stopAll()
                    WallpaperBubbleService.hide(applicationContext)
                    Toast.makeText(applicationContext, "已停止", Toast.LENGTH_SHORT).show()
                },
                onMove = { nx, ny ->
                    if (RenderSettings.load(applicationContext).gazeFollowEnabled) {
                        NativeLive2D.lookAt(handle, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
                    }
                },
            )
            surfaceHolder.addCallback(this)
        }

        override fun onDestroy() {
            restartJob?.cancel()
            surfaceHolderRef?.removeCallback(this)
            settingsPreferences?.unregisterOnSharedPreferenceChangeListener(renderSettingsListener)
            settingsPreferences = null
            stopRenderer()
            gestureHandler?.destroy()
            gestureHandler = null
            scope.cancel()
            super.onDestroy()
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            ensureRenderer()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            if (handle != 0L) NativeLive2D.resize(handle, this.width, this.height)
            ensureRenderer()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            stopRenderer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                ensureRenderer()
            } else {
                stopRenderer()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (visible) {
                lastInteractionAt = System.currentTimeMillis()
                gestureHandler?.onTouchEvent(event)
            }
        }

        private fun ensureRenderer() {
            if (!visible || !surfaceReady || loading) return
            if (!isWallpaperEnabled(applicationContext)) {
                stopRenderer()
                return
            }
            if (surfaceHolderRef?.surface?.isValid != true) return
            val generation = ++loadGeneration
            loading = true
            scope.launch {
                try {
                    val model = loadStep("Failed to load wallpaper model") {
                        withContext(Dispatchers.IO) { loadPersistedModelChoice(applicationContext) }
                    } ?: return@launch
                    val prepared = loadStep("Failed to prepare wallpaper model") {
                        AssetSync.prepareModel(applicationContext, model.modelAssetPath)
                    } ?: return@launch
                    val wallpaperBackgroundUri = withContext(Dispatchers.IO) {
                        loadWallpaperBackgroundUri(applicationContext)
                    }
                    val background = NativeLive2D.loadBackground(applicationContext, wallpaperBackgroundUri)
                    val activeSurface = surfaceHolderRef?.surface
                    if (
                        generation != loadGeneration ||
                        !visible ||
                        !surfaceReady ||
                        activeSurface?.isValid != true
                    ) return@launch

                    val settings = RenderSettings.load(applicationContext)
                    if (handle == 0L || runtimeRoot != prepared.runtimeRoot) {
                        destroyHandle()
                        runtimeRoot = prepared.runtimeRoot
                        Live2DWallpaperService.activeHandle = 0L
                        handle = NativeLive2D.create(
                            activeSurface,
                            prepared.runtimeRoot,
                            width,
                            height,
                            settings.fpsLimit,
                            settings.vsyncEnabled,
                            settings.renderResolution.scale,
                        )
                        NativeLive2D.setBackground(handle, background)
                        applyWallpaperTransform()
                    } else {
                        NativeLive2D.setRenderOptions(handle, settings.fpsLimit, settings.vsyncEnabled)
                        NativeLive2D.setBackground(handle, background)
                    }
                    if (handle != 0L) {
                        NativeLive2D.setFpsDisplayEnabled(handle, settings.fpsDisplayEnabled)
                        NativeLive2D.loadModel(
                            handle,
                            prepared.modelPath,
                            prepared.resourcePaths.toTypedArray(),
                            prepared.resourceBytes.toTypedArray(),
                        )
                        startIdleLoop()
                    }
                } finally {
                    if (generation == loadGeneration) loading = false
                }
            }
        }

        private suspend fun <T> loadStep(message: String, block: suspend () -> T): T? = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, message, error)
            null
        }

        private fun scheduleRendererRestart() {
            restartJob?.cancel()
            restartJob = scope.launch {
                delay(RESTART_DEBOUNCE_MS)
                stopRenderer()
                ensureRenderer()
            }
        }

        private fun applyWallpaperTransform() {
            if (handle == 0L) return
            val transform = loadWallpaperTransform(applicationContext)
            NativeLive2D.setTransform(handle, transform.offsetX, transform.offsetY, transform.scale)
        }

        private fun playTouchAction() {
            if (handle == 0L || !visible) return
            if (!loadTouchAnimationEnabled(applicationContext)) return
            val choices = loadTouchAnimations(applicationContext)
            if (choices.isEmpty()) return
            NativeLive2D.playAction(handle, choices.random())
        }

        private fun playIdleAction() {
            if (handle == 0L || !visible) return
            if (loadBuiltinVoiceEnabled(applicationContext)) {
                val charId = loadSelectedCharacterId(applicationContext)
                val line = BuiltinVoiceManager.randomLineWithVoice(
                        applicationContext,
                        charId,
                        loadBuiltinVoiceLanguage(applicationContext),
                    )
                if (line != null) {
                    NativeLive2D.playAction(handle, baseMotionName(line.motion))
                    runCatching { line.wavFile?.readBytes()?.let { VoicePlayer.play(applicationContext, it) } }
                    if (loadBubbleEnabled(applicationContext)) {
                        WallpaperBubbleService.show(applicationContext, line.text)
                    }
                    return
                }
            }
            val choices = loadIdleAnimations(applicationContext)
            if (choices.isEmpty()) return
            NativeLive2D.playAction(handle, choices.random())
        }

        private fun baseMotionName(name: String): String = name.replace(Regex("\\d+$"), "")

        private fun startIdleLoop() {
            idleJob?.cancel()
            idleJob = scope.launch {
                while (true) {
                    delay(loadIdleIntervalMs(applicationContext))
                    if (!loadIdleAnimationEnabled(applicationContext)) continue
                    if (System.currentTimeMillis() - lastInteractionAt < 2_000L) continue
                    playIdleAction()
                }
            }
        }

        private fun openChatInput() {
            WallpaperChatActivity.open(applicationContext)
        }

        private fun stopRenderer() {
            idleJob?.cancel()
            idleJob = null
            loadGeneration++
            loading = false
            destroyHandle()
        }

        private fun destroyHandle() {
            if (handle != 0L) {
                NativeLive2D.destroy(handle)
                handle = 0L
                runtimeRoot = null
                activeHandle = 0L
            }
        }

    }

    companion object {
        @Volatile var activeHandle = 0L
            private set

        const val TAG = "BandoriPetWallpaper"
        const val RESTART_DEBOUNCE_MS = 50L
    }
}

