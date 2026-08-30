package com.bangdream.pet.live2d

import android.content.Context
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.bangdream.pet.I18n
import com.bangdream.pet.RenderResolution
import com.bangdream.pet.WallpaperModelPlacement
import com.bangdream.pet.wallpaper.WallpaperHitArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 多模型实时预览：把多个模型分别加载到 native slot，支持在预览里逐个模型
 * 拖拽移动、双指缩放（行为与单模型 WallpaperAdjustActivity 一致）。
 */
class MultiLive2DRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handle = 0L
    private var renderSurface: Surface? = null
    private var runtimeRoot: String? = null
    private var loading = false
    private var loadGeneration = 0
    private var fpsLimit = 60
    private var vsyncEnabled = true
    private var renderResolution = RenderResolution.PointToPoint
    private var pendingPlacements: List<WallpaperModelPlacement> = emptyList()

    private class SlotModel(
        val placement: WallpaperModelPlacement,
        val prepared: PreparedModel? = null,
        val canvas: WallpaperHitArea.ModelCanvas? = null,
    ) {
        fun copy(placement: WallpaperModelPlacement = this.placement): SlotModel =
            SlotModel(placement, prepared, canvas)
    }

    private val slots = mutableListOf<SlotModel>()

    // 触摸状态
    private var activeSlot = -1
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var pinching = false
    private var moved = false
    private var interactiveTransformScheduled = false

    /** 某个模型位置/大小变化时回调（携带最新 placements 列表）。 */
    var placementsChanged: ((List<WallpaperModelPlacement>) -> Unit)? = null
    var statusChanged: ((String?) -> Unit)? = null

    private val applyInteractiveTransform = Runnable {
        interactiveTransformScheduled = false
        emitPlacements()
    }

    init {
        setOpaque(false)
        surfaceTextureListener = this
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setPlacements(placements: List<WallpaperModelPlacement>) {
        val enabled = placements.filter { it.enabled }
        if (slots.isNotEmpty() && enabled.map { it.id } == slots.map { it.placement.id } &&
            enabled.zip(slots.map { it.placement }).all { (a, b) -> a.modelAssetPath == b.modelAssetPath }
        ) {
            // 结构未变 -> 只同步 transform（拖拽/滑杆）
            enabled.forEachIndexed { index, placement ->
                val slot = slots[index]
                if (slot.placement.offsetX != placement.offsetX ||
                    slot.placement.offsetY != placement.offsetY ||
                    slot.placement.scale != placement.scale
                ) {
                    slots[index] = slot.copy(placement = placement)
                    applySlotTransform(index)
                }
            }
            return
        }
        pendingPlacements = placements
        reload()
    }

    fun setRenderOptions(fpsLimit: Int, vsyncEnabled: Boolean) {
        this.fpsLimit = fpsLimit.coerceIn(15, 120)
        this.vsyncEnabled = vsyncEnabled
        updateSurfaceFrameRate()
        if (handle != 0L) NativeLive2D.setRenderOptions(handle, this.fpsLimit, this.vsyncEnabled)
    }

    fun setRenderResolution(resolution: RenderResolution) {
        if (renderResolution == resolution) return
        renderResolution = resolution
        if (handle != 0L) NativeLive2D.setRenderScale(handle, resolution.scale)
    }

    fun setBackgroundUri(uri: String?) {
        backgroundUri = uri
    }

    fun currentPlacements(): List<WallpaperModelPlacement> = slots.map { it.placement }

    private var backgroundUri: String? = null

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderSurface?.release()
        renderSurface = Surface(surface)
        updateSurfaceFrameRate()
        reload()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (handle != 0L) NativeLive2D.resize(handle, width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        destroyRenderer()
        renderSurface?.release()
        renderSurface = null
        return true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            if (handle != 0L) NativeLive2D.setPaused(handle, false)
        } else {
            if (handle != 0L) NativeLive2D.setPaused(handle, true)
        }
    }

    fun release() {
        surfaceTextureListener = null
        destroyRenderer()
        renderSurface?.release()
        renderSurface = null
        scope.cancel()
    }

    private fun updateSurfaceFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val surface = renderSurface?.takeIf(Surface::isValid) ?: return
        runCatching {
            surface.setFrameRate(fpsLimit.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun destroyRenderer() {
        if (handle != 0L) {
            NativeLive2D.destroy(handle)
            handle = 0L
        }
        runtimeRoot = null
        slots.clear()
    }

    private fun reload() {
        val placements = pendingPlacements.filter { it.enabled }
        if (placements.isEmpty() || renderSurface?.isValid != true || loading) return
        loadGeneration += 1
        loading = true
        val generation = loadGeneration
        val surface = renderSurface!!
        statusChanged?.invoke(I18n.t("status_preparing"))
        scope.launch {
            try {
                val preparedList = withContext(Dispatchers.IO) {
                    placements.map { placement ->
                        placement to AssetSync.prepareModel(context.applicationContext, placement.modelAssetPath)
                    }
                }
                if (generation != loadGeneration || renderSurface != surface || !surface.isValid) return@launch
                val first = preparedList.firstOrNull()?.second ?: return@launch
                if (handle == 0L || runtimeRoot != first.runtimeRoot) {
                    if (handle != 0L) NativeLive2D.destroy(handle)
                    runtimeRoot = first.runtimeRoot
                    handle = NativeLive2D.create(
                        surface,
                        first.runtimeRoot,
                        width.coerceAtLeast(1),
                        height.coerceAtLeast(1),
                        fpsLimit,
                        vsyncEnabled,
                        renderResolution.scale,
                    )
                } else {
                    NativeLive2D.setRenderOptions(handle, fpsLimit, vsyncEnabled)
                }
                if (handle == 0L) return@launch
                val canvases = withContext(Dispatchers.IO) {
                    preparedList.associate { (placement, prepared) ->
                        placement.id to WallpaperHitArea.resolveCanvas(prepared)
                    }
                }
                val newSlots = mutableListOf<SlotModel>()
                preparedList.forEachIndexed { index, (placement, prepared) ->
                    NativeLive2D.loadModelAt(
                        handle,
                        index,
                        prepared.modelPath,
                        prepared.resourcePaths.toTypedArray(),
                        prepared.resourceBytes.toTypedArray(),
                    )
                    NativeLive2D.setTransformAt(handle, index, placement.offsetX, placement.offsetY, placement.scale)
                    newSlots.add(SlotModel(placement, prepared, canvases[placement.id]))
                }
                if (generation == loadGeneration) {
                    slots.clear()
                    slots.addAll(newSlots)
                    statusChanged?.invoke(null)
                }
            } catch (e: Exception) {
                statusChanged?.invoke(e.message ?: I18n.t("status_resource_failed"))
            } finally {
                if (generation == loadGeneration) loading = false
            }
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                lastSpan = 0f
                pinching = false
                moved = false
                activeSlot = hitTest(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastSpan = pointerSpan(event)
                    pinching = true
                    if (activeSlot < 0) {
                        val mx = (event.getX(0) + event.getX(1)) / 2f
                        val my = (event.getY(0) + event.getY(1)) / 2f
                        activeSlot = hitTest(mx, my)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    val span = pointerSpan(event)
                    if (span > 0f && lastSpan > 0f && activeSlot in slots.indices) {
                        val slot = slots[activeSlot]
                        val nextScale = (slot.placement.scale * (span / lastSpan)).coerceIn(0.4f, 3f)
                        if (nextScale != slot.placement.scale) {
                            slots[activeSlot] = slot.copy(placement = slot.placement.copy(scale = nextScale))
                            applySlotTransform(activeSlot)
                            scheduleInteractiveTransform()
                            moved = true
                        }
                    }
                    lastSpan = span
                } else if (activeSlot in slots.indices) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (dx * dx + dy * dy > 4f) {
                        val slot = slots[activeSlot]
                        val base = max(1f, min(width.toFloat(), height.toFloat()))
                        val nx = (slot.placement.offsetX + dx * 2f / base).coerceIn(-1.2f, 1.2f)
                        val ny = (slot.placement.offsetY - dy * 2f / base).coerceIn(-1.2f, 1.2f)
                        slots[activeSlot] = slot.copy(placement = slot.placement.copy(offsetX = nx, offsetY = ny))
                        applySlotTransform(activeSlot)
                        scheduleInteractiveTransform()
                        moved = true
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount - 1 < 2) {
                    pinching = false
                    val keepIndex = if (event.actionIndex == 0) 1 else 0
                    if (keepIndex < event.pointerCount) {
                        lastX = event.getX(keepIndex)
                        lastY = event.getY(keepIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && activeSlot in slots.indices && handle != 0L) {
                    val nx = (event.x / max(width.toFloat(), 1f)).coerceIn(0f, 1f)
                    val ny = (event.y / max(height.toFloat(), 1f)).coerceIn(0f, 1f)
                    NativeLive2D.touchAt(handle, activeSlot, nx, ny)
                    performClick()
                }
                pinching = false
                activeSlot = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                pinching = false
                activeSlot = -1
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        // 命中检测：优先最上层（后添加的模型）
        for (index in slots.indices.reversed()) {
            val slot = slots[index]
            val rect = slotRect(slot)
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    private fun slotRect(slot: SlotModel): RectF = WallpaperHitArea.computeRect(
        surfaceWidth = width.coerceAtLeast(1),
        surfaceHeight = height.coerceAtLeast(1),
        transform = slot.placement.toTransform(),
        canvas = slot.canvas,
    )

    private fun applySlotTransform(index: Int) {
        if (handle == 0L || index !in slots.indices) return
        val p = slots[index].placement
        NativeLive2D.setTransformAt(handle, index, p.offsetX, p.offsetY, p.scale)
    }

    private fun scheduleInteractiveTransform() {
        if (interactiveTransformScheduled) return
        interactiveTransformScheduled = true
        postOnAnimation(applyInteractiveTransform)
    }

    private fun emitPlacements() {
        interactiveTransformScheduled = false
        // 合并完整列表：已加载的 slot 用最新 transform，未加载/禁用的保持原样
        val byId = slots.associate { it.placement.id to it.placement }
        val merged = pendingPlacements.map { byId[it.id] ?: it }
        placementsChanged?.invoke(merged)
    }

    private fun pointerSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }
}
