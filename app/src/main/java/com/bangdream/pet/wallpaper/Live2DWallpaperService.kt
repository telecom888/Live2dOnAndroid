package com.bangdream.pet.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.bangdream.pet.RenderSettings
import com.bangdream.pet.KEY_FPS_DISPLAY_ENABLED
import com.bangdream.pet.KEY_FPS_LIMIT
import com.bangdream.pet.KEY_RENDER_RESOLUTION
import com.bangdream.pet.KEY_SELECTED_CHARACTER_ID
import com.bangdream.pet.KEY_SELECTED_MODEL_ASSET_PATH
import com.bangdream.pet.KEY_VSYNC_ENABLED
import com.bangdream.pet.KEY_WALLPAPER_BACKGROUND_URI
import com.bangdream.pet.KEY_WALLPAPER_ENABLED
import com.bangdream.pet.KEY_WALLPAPER_OFFSET_X
import com.bangdream.pet.KEY_WALLPAPER_OFFSET_Y
import com.bangdream.pet.KEY_WALLPAPER_SCALE
import com.bangdream.pet.SETTINGS_PREFS
import com.bangdream.pet.isWallpaperEnabled
import android.widget.Toast
import com.bangdream.pet.chat.PetRuntime
import com.bangdream.pet.chat.WallpaperBubbleService
import com.bangdream.pet.chat.WallpaperChatActivity
import com.bangdream.pet.loadBubbleEnabled
import com.bangdream.pet.loadBuiltinVoiceEnabled
import com.bangdream.pet.loadBuiltinVoiceLanguage
import com.bangdream.pet.loadIdleAnimationEnabled
import com.bangdream.pet.loadSelectedCharacterId
import com.bangdream.pet.loadIdleAnimations
import com.bangdream.pet.loadLastWallpaperAction
import com.bangdream.pet.saveLastWallpaperAction
import com.bangdream.pet.loadIdleIntervalMs
import com.bangdream.pet.loadSwipeAnimationEnabled
import com.bangdream.pet.loadTouchAnimationEnabled
import com.bangdream.pet.loadTouchAnimations
import com.bangdream.pet.loadPersistedModelChoice
import com.bangdream.pet.loadWallpaperBackgroundUri
import com.bangdream.pet.loadWallpaperTransform
import com.bangdream.pet.live2d.AssetSync
import com.bangdream.pet.voice.BuiltinVoiceManager
import com.bangdream.pet.voice.VoicePlayer
import com.bangdream.pet.live2d.NativeLive2D
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
        private var modelCanvas: WallpaperHitArea.ModelCanvas? = null
        private var hitArea: RectF? = null
        private var touchDownX = 0f
        private var touchDownY = 0f
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
                onDown = { x, y ->
                    touchDownX = x
                    touchDownY = y
                    lookAtTouch(x, y)
                },
                onTap = { x, y ->
                    lookAtTouch(x, y)
                    if (isInModelArea(x, y) && loadTouchAnimationEnabled(applicationContext)) {
                        sendWallpaperTouch(x, y)
                        playRandomTouchAction()
                    }
                },
                onSwipe = { x, y ->
                    lookAtTouch(x, y)
                    if (isInModelArea(touchDownX, touchDownY) && loadSwipeAnimationEnabled(applicationContext)) {
                        sendWallpaperTouch(x, y)
                        playRandomTouchAction()
                    }
                },
                onDoubleTap = { x, y ->
                    lookAtTouch(x, y)
                    if (isInModelArea(x, y)) {
                        openChatInput()
                    }
                },
                onLongPress = { x, y ->
                    if (isInModelArea(x, y)) {
                        PetRuntime.stopAll()
                        WallpaperBubbleService.hide(applicationContext)
                        Toast.makeText(applicationContext, "已停止", Toast.LENGTH_SHORT).show()
                    }
                },
                onMove = { x, y ->
                    lookAtTouch(x, y)
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
            if (handle != 0L) {
                // surface 被系统销毁重建：重绑新 Surface，保留 Lua 模型/动作状态，不重新加载模型
                if (NativeLive2D.attachSurface(handle, holder.surface)) {
                    NativeLive2D.resize(handle, width, height)
                    if (visible) NativeLive2D.setPaused(handle, false)
                } else {
                    Log.e(TAG, "attachSurface failed, recreate renderer")
                    stopRenderer()
                    ensureRenderer()
                }
            } else {
                ensureRenderer()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            refreshHitArea()
            if (handle != 0L) {
                NativeLive2D.resize(handle, this.width, this.height)
                if (visible) NativeLive2D.setPaused(handle, false)
            } else {
                ensureRenderer()
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            // 不销毁 renderer：暂停并保留模型/动作状态，surfaceCreated 后重绑新 Surface 继续播放
            if (handle != 0L) NativeLive2D.setPaused(handle, true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                // 恢复渲染但不重建、不重新加载模型：保留当前模型/动作状态
                if (handle != 0L) {
                    NativeLive2D.setPaused(handle, false)
                } else if (surfaceReady) {
                    ensureRenderer()
                }
            } else {
                // 被覆盖时只暂停（模型/动作状态保留在 native 侧），回到桌面立即恢复，不重置动作
                idleJob?.cancel()
                idleJob = null
                if (handle != 0L) NativeLive2D.setPaused(handle, true)
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
                        val canvas = withContext(Dispatchers.IO) {
                            WallpaperHitArea.resolveCanvas(prepared)
                        }
                        if (generation == loadGeneration) {
                            modelCanvas = canvas
                            refreshHitArea()
                        }
                        replayRecentAction()
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
            refreshHitArea()
            if (handle == 0L) return
            val transform = loadWallpaperTransform(applicationContext)
            NativeLive2D.setTransform(handle, transform.offsetX, transform.offsetY, transform.scale)
        }

        private fun playRandomTouchAction() {
            if (handle == 0L || !visible) return
            val choices = loadTouchAnimations(applicationContext)
            if (choices.isEmpty()) return
            val action = choices.random()
            saveLastWallpaperAction(applicationContext, action)
            NativeLive2D.playAction(handle, action)
        }

        /** surface 被系统销毁重建后，若刚播放过动作则在窗口期内重放，避免回到桌面动作被重置。 */
        private fun replayRecentAction() {
            if (handle == 0L) return
            val (action, at) = loadLastWallpaperAction(applicationContext)
            if (action.isBlank()) return
            if (System.currentTimeMillis() - at > LAST_ACTION_REPLAY_WINDOW_MS) return
            NativeLive2D.playAction(handle, action)
        }

        /** 视线跟随：全屏任意触摸都响应（不限定模型区域）。 */
        private fun lookAtTouch(x: Float, y: Float) {
            if (handle == 0L) return
            if (!RenderSettings.load(applicationContext).gazeFollowEnabled) return
            val nx = (x / width).coerceIn(0f, 1f)
            val ny = (y / height).coerceIn(0f, 1f)
            NativeLive2D.lookAt(handle, nx, ny)
        }

        /** 动作类交互（单击/滑动/双击/长按）只允许落在模型显示区域内。 */
        private fun isInModelArea(x: Float, y: Float): Boolean =
            hitArea?.contains(x, y) == true

        /** 根据 surface 尺寸、模型画布与用户变换重新计算模型显示区域。 */
        private fun refreshHitArea() {
            hitArea = WallpaperHitArea.computeRect(
                surfaceWidth = width,
                surfaceHeight = height,
                transform = loadWallpaperTransform(applicationContext),
                canvas = modelCanvas,
            )
        }

        /** 把桌面触摸坐标（surface 像素）归一化后发送给 native，触发区域化触摸动作（摸头/拍肩等）。 */
        private fun sendWallpaperTouch(x: Float, y: Float) {
            if (handle == 0L || !visible) return
            val nx = (x / width).coerceIn(0f, 1f)
            val ny = (y / height).coerceIn(0f, 1f)
            NativeLive2D.touch(handle, nx, ny)
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
                    val motion = baseMotionName(line.motion)
                    saveLastWallpaperAction(applicationContext, motion)
                    NativeLive2D.playAction(handle, motion)
                    line.readWav(applicationContext)?.let { VoicePlayer.play(applicationContext, it) }
                    if (loadBubbleEnabled(applicationContext)) {
                        WallpaperBubbleService.show(applicationContext, line.text)
                    }
                    return
                }
            }
            val choices = loadIdleAnimations(applicationContext)
            if (choices.isEmpty()) return
            val action = choices.random()
            saveLastWallpaperAction(applicationContext, action)
            NativeLive2D.playAction(handle, action)
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
            modelCanvas = null
            hitArea = null
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

        const val TAG = "BangDreamPetWallpaper"
        const val RESTART_DEBOUNCE_MS = 50L
        const val LAST_ACTION_REPLAY_WINDOW_MS = 10_000L
    }
}

