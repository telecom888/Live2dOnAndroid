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
import com.bangdream.pet.KEY_WALLPAPER_MODE
import com.bangdream.pet.KEY_WALLPAPER_MODELS
import com.bangdream.pet.KEY_WALLPAPER_OFFSET_X
import com.bangdream.pet.KEY_WALLPAPER_OFFSET_Y
import com.bangdream.pet.KEY_WALLPAPER_SCALE
import com.bangdream.pet.SETTINGS_PREFS
import com.bangdream.pet.WallpaperMode
import com.bangdream.pet.WallpaperModelPlacement
import com.bangdream.pet.WallpaperMultiModelSettings
import com.bangdream.pet.isWallpaperEnabled
import com.bangdream.pet.loadWallpaperMode
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
import com.bangdream.pet.clearLastWallpaperAction
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
import com.bangdream.pet.live2d.PreparedModel
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

    /** 多模型模式：单个 slot 的活动模型。 */
    private data class ActiveSlotModel(
        val placement: WallpaperModelPlacement,
        val prepared: PreparedModel,
        val canvas: WallpaperHitArea.ModelCanvas?,
        val hitArea: RectF?,
    )

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
        private var wallpaperMode = WallpaperMode.SINGLE

        // 单模型模式字段（复用既有实现）
        private var modelCanvas: WallpaperHitArea.ModelCanvas? = null
        private var hitArea: RectF? = null

        // 多模型模式字段：slot -> 活动模型
        private val slotModels = mutableMapOf<Int, ActiveSlotModel>()

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
                        // 直接改渲染 buffer 尺寸在部分机型（HyperOS 动态壁纸）会导致壁纸/模型异常缩放。
                        // 改为整体重建渲染器（走首次启动的 create 路径，已验证尺寸正确）。
                        scheduleRendererRestart()
                    }
                    KEY_FPS_LIMIT, KEY_VSYNC_ENABLED -> if (handle != 0L) {
                        val settings = RenderSettings.load(applicationContext)
                        NativeLive2D.setRenderOptions(handle, settings.fpsLimit, settings.vsyncEnabled)
                    }
                    KEY_WALLPAPER_OFFSET_X, KEY_WALLPAPER_OFFSET_Y, KEY_WALLPAPER_SCALE -> {
                        if (wallpaperMode == WallpaperMode.SINGLE) applyWallpaperTransform()
                    }
                    KEY_WALLPAPER_MODE,
                    KEY_WALLPAPER_MODELS,
                    KEY_SELECTED_CHARACTER_ID,
                    KEY_SELECTED_MODEL_ASSET_PATH,
                    KEY_WALLPAPER_BACKGROUND_URI -> scheduleRendererRestart()
                    KEY_WALLPAPER_ENABLED -> {
                        val enabled = isWallpaperEnabled(applicationContext)
                        Log.d(TAG, "wallpaper enabled changed -> $enabled handle=$handle")
                        if (enabled) {
                            ensureRenderer()
                        } else {
                            disableModelRendering()
                        }
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
                    val slot = findHitSlot(x, y)
                    if (slot != null && loadTouchAnimationEnabled(applicationContext)) {
                        sendWallpaperTouch(slot, x, y)
                        playRandomTouchAction(slot)
                    }
                },
                onSwipe = { x, y ->
                    lookAtTouch(x, y)
                    val startSlot = findHitSlot(touchDownX, touchDownY)
                    if (startSlot != null && loadSwipeAnimationEnabled(applicationContext)) {
                        sendWallpaperTouch(startSlot, x, y)
                        playRandomTouchAction(startSlot)
                    }
                },
                onDoubleTap = { x, y ->
                    lookAtTouch(x, y)
                    if (findHitSlot(x, y) != null) {
                        openChatInput()
                    }
                },
                onLongPress = { x, y ->
                    if (findHitSlot(x, y) != null) {
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
            refreshAllHitAreas()
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
                    wallpaperMode = loadWallpaperMode(applicationContext)
                    if (wallpaperMode == WallpaperMode.MULTI) {
                        ensureMultiRenderer(generation)
                    } else {
                        ensureSingleRenderer(generation)
                    }
                } finally {
                    if (generation == loadGeneration) loading = false
                }
            }
        }

        // ==================== 单模型模式（既有实现） ====================
        private suspend fun ensureSingleRenderer(generation: Int) {
            val model = loadStep("Failed to load wallpaper model") {
                withContext(Dispatchers.IO) { loadPersistedModelChoice(applicationContext) }
            } ?: return
            val prepared = loadStep("Failed to prepare wallpaper model") {
                AssetSync.prepareModel(applicationContext, model.modelAssetPath)
            } ?: return
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
            ) return

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
                    refreshSingleHitArea()
                }
                replayRecentAction()
                startIdleLoop()
            }
        }

        // ==================== 多模型模式 ====================
        private suspend fun ensureMultiRenderer(generation: Int) {
            val placements = withContext(Dispatchers.IO) {
                WallpaperMultiModelSettings.load(applicationContext).models.filter { it.enabled }
            }
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
            ) return

            // 逐个准备模型（各自可能有独立的资源树）
            val preparedList = mutableListOf<Pair<WallpaperModelPlacement, PreparedModel>>()
            for (placement in placements) {
                val prepared = loadStep("Failed to prepare wallpaper model ${placement.id}") {
                    AssetSync.prepareModel(applicationContext, placement.modelAssetPath)
                } ?: continue
                preparedList.add(placement to prepared)
            }

            val settings = RenderSettings.load(applicationContext)
            val firstPrepared = preparedList.firstOrNull()?.second
            if (handle == 0L || runtimeRoot != (firstPrepared?.runtimeRoot ?: runtimeRoot)) {
                destroyHandle()
                slotModels.clear()
                if (firstPrepared != null) {
                    runtimeRoot = firstPrepared.runtimeRoot
                    Live2DWallpaperService.activeHandle = 0L
                    handle = NativeLive2D.create(
                        activeSurface,
                        firstPrepared.runtimeRoot,
                        width,
                        height,
                        settings.fpsLimit,
                        settings.vsyncEnabled,
                        settings.renderResolution.scale,
                    )
                    NativeLive2D.setBackground(handle, background)
                }
            } else {
                NativeLive2D.setRenderOptions(handle, settings.fpsLimit, settings.vsyncEnabled)
                NativeLive2D.setBackground(handle, background)
            }
            if (handle == 0L) return

            // 增量同步：保留相同模型路径的 slot，卸载消失的 slot，加载新增/变更的 slot
            val newSlots = preparedList.mapIndexed { index, (placement, prepared) -> index to (placement to prepared) }.toMap()
            val oldSlots = slotModels.keys.toSet()
            for (slot in oldSlots - newSlots.keys) {
                NativeLive2D.unloadModelAt(handle, slot)
                slotModels.remove(slot)
            }
            val canvases = withContext(Dispatchers.IO) {
                preparedList.associate { (placement, prepared) -> placement.id to WallpaperHitArea.resolveCanvas(prepared) }
            }
            val newSlotModels = mutableMapOf<Int, ActiveSlotModel>()
            for ((slot, pair) in newSlots) {
                val (placement, prepared) = pair
                val existing = slotModels[slot]
                if (existing?.placement?.modelAssetPath != placement.modelAssetPath) {
                    NativeLive2D.loadModelAt(
                        handle,
                        slot,
                        prepared.modelPath,
                        prepared.resourcePaths.toTypedArray(),
                        prepared.resourceBytes.toTypedArray(),
                    )
                }
                val canvas = canvases[placement.id]
                val rect = canvas?.let { WallpaperHitArea.computeRect(width, height, placement.toTransform(), it) }
                newSlotModels[slot] = ActiveSlotModel(placement, prepared, canvas, rect)
                NativeLive2D.setTransformAt(handle, slot, placement.offsetX, placement.offsetY, placement.scale)
            }
            slotModels.clear()
            slotModels.putAll(newSlotModels)
            NativeLive2D.setFpsDisplayEnabled(handle, settings.fpsDisplayEnabled)
            if (generation == loadGeneration) {
                replayRecentAction()
                startIdleLoop()
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
            refreshSingleHitArea()
            if (handle == 0L) return
            val transform = loadWallpaperTransform(applicationContext)
            NativeLive2D.setTransform(handle, transform.offsetX, transform.offsetY, transform.scale)
        }

        private fun playRandomTouchAction(slot: Int?) {
            if (handle == 0L || !visible) return
            val choices = loadTouchAnimations(applicationContext)
            if (choices.isEmpty()) return
            val action = choices.random()
            saveLastWallpaperAction(applicationContext, action)
            if (wallpaperMode == WallpaperMode.MULTI && slot != null) {
                NativeLive2D.playActionAt(handle, slot, action)
            } else {
                NativeLive2D.playAction(handle, action)
            }
        }

        /** surface 被系统销毁重建后，若刚播放过动作则在窗口期内重放，避免回到桌面动作被重置。 */
        private fun replayRecentAction() {
            if (handle == 0L) return
            val (action, at) = loadLastWallpaperAction(applicationContext)
            if (action.isBlank()) return
            if (System.currentTimeMillis() - at > LAST_ACTION_REPLAY_WINDOW_MS) return
            if (wallpaperMode == WallpaperMode.MULTI) {
                for (slot in slotModels.keys) {
                    NativeLive2D.playActionAt(handle, slot, action)
                }
            } else {
                NativeLive2D.playAction(handle, action)
            }
        }

        /** 视线跟随：全屏任意触摸都响应（不限定模型区域）。 */
        private fun lookAtTouch(x: Float, y: Float) {
            if (handle == 0L) return
            if (!RenderSettings.load(applicationContext).gazeFollowEnabled) return
            val nx = (x / width).coerceIn(0f, 1f)
            val ny = (y / height).coerceIn(0f, 1f)
            NativeLive2D.lookAt(handle, nx, ny)
        }

        /** 动作类交互（单击/滑动/双击/长按）只允许落在模型显示区域内。单模型返回 0 或 null。 */
        private fun findHitSlot(x: Float, y: Float): Int? {
            if (wallpaperMode == WallpaperMode.MULTI) {
                for ((slot, active) in slotModels) {
                    if (active.hitArea?.contains(x, y) == true) return slot
                }
                return null
            }
            return if (hitArea?.contains(x, y) == true) 0 else null
        }

        private fun refreshAllHitAreas() {
            if (wallpaperMode == WallpaperMode.MULTI) {
                val rebuilt = slotModels.mapValues { (_, active) ->
                    val rect = active.canvas?.let { WallpaperHitArea.computeRect(width, height, active.placement.toTransform(), it) }
                    active.copy(hitArea = rect)
                }
                slotModels.clear()
                slotModels.putAll(rebuilt)
            } else {
                refreshSingleHitArea()
            }
        }

        private fun refreshSingleHitArea() {
            hitArea = WallpaperHitArea.computeRect(
                surfaceWidth = width,
                surfaceHeight = height,
                transform = loadWallpaperTransform(applicationContext),
                canvas = modelCanvas,
            )
        }

        /** 把桌面触摸坐标（surface 像素）归一化后发送给 native，触发区域化触摸动作（摸头/拍肩等）。 */
        private fun sendWallpaperTouch(slot: Int, x: Float, y: Float) {
            if (handle == 0L || !visible) return
            val nx = (x / width).coerceIn(0f, 1f)
            val ny = (y / height).coerceIn(0f, 1f)
            if (wallpaperMode == WallpaperMode.MULTI) {
                NativeLive2D.touchAt(handle, slot, nx, ny)
            } else {
                NativeLive2D.touch(handle, nx, ny)
            }
        }

        private fun playIdleAction() {
            if (handle == 0L || !visible) return
            val slot = if (wallpaperMode == WallpaperMode.MULTI) {
                slotModels.keys.randomOrNull()
            } else {
                0
            }
            if (slot == null) return
            val charId = if (wallpaperMode == WallpaperMode.MULTI) {
                slotModels[slot]?.placement?.characterId
            } else {
                loadSelectedCharacterId(applicationContext)
            }
            if (loadBuiltinVoiceEnabled(applicationContext) && charId != null) {
                val line = BuiltinVoiceManager.randomLineWithVoice(
                        applicationContext,
                        charId,
                        loadBuiltinVoiceLanguage(applicationContext),
                    )
                if (line != null) {
                    val motion = baseMotionName(line.motion)
                    saveLastWallpaperAction(applicationContext, motion)
                    if (wallpaperMode == WallpaperMode.MULTI) {
                        NativeLive2D.playActionAt(handle, slot, motion)
                    } else {
                        NativeLive2D.playAction(handle, motion)
                    }
                    line.readWav(applicationContext)?.let { VoicePlayer.play(applicationContext, it) }
                    if (loadBubbleEnabled(applicationContext)) {
                        WallpaperBubbleService.show(applicationContext, line.display)
                    }
                    return
                }
            }
            val choices = loadIdleAnimations(applicationContext)
            if (choices.isEmpty()) return
            val action = choices.random()
            saveLastWallpaperAction(applicationContext, action)
            if (wallpaperMode == WallpaperMode.MULTI) {
                NativeLive2D.playActionAt(handle, slot, action)
            } else {
                NativeLive2D.playAction(handle, action)
            }
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

        /**
         * 关闭桌面渲染：保留渲染器只绘制背景，卸载全部模型并清除最近动作。
         * 避免「关闭后残留模型最后一帧冻结图、重新开启后续播旧动作」。
         */
        private fun disableModelRendering() {
            Log.d(TAG, "disableModelRendering handle=$handle slots=${slotModels.keys}")
            idleJob?.cancel()
            idleJob = null
            loadGeneration++
            loading = false
            clearLastWallpaperAction(applicationContext)
            if (handle != 0L) {
                // 单模型走 slot 0（loadModel），多模型走 slotModels；两种情况都卸载
                NativeLive2D.unloadModelAt(handle, 0)
                for (slot in slotModels.keys.toList()) {
                    NativeLive2D.unloadModelAt(handle, slot)
                }
            }
            slotModels.clear()
            modelCanvas = null
            hitArea = null
        }

        private fun stopRenderer() {
            idleJob?.cancel()
            idleJob = null
            loadGeneration++
            loading = false
            modelCanvas = null
            hitArea = null
            slotModels.clear()
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

        const val TAG = "BangDreamLive2DWallpaper"
        const val RESTART_DEBOUNCE_MS = 50L
        const val LAST_ACTION_REPLAY_WINDOW_MS = 10_000L
    }
}
