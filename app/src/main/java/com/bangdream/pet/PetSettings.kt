package com.bangdream.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.live2d.Live2DTransform
import org.json.JSONArray
import org.json.JSONObject

const val SETTINGS_PREFS = "bangdream_pet_settings"
const val KEY_FPS_LIMIT = "fps_limit"
const val KEY_FPS_DISPLAY_ENABLED = "fps_display_enabled"
const val KEY_VSYNC_ENABLED = "vsync_enabled"
const val KEY_RENDER_RESOLUTION = "render_resolution"
const val KEY_GAZE_FOLLOW_ENABLED = "gaze_follow_enabled"
const val KEY_LIVE2D_BACKGROUND_URI = "live2d_background_uri"
const val KEY_WALLPAPER_BACKGROUND_URI = "wallpaper_background_uri"
const val KEY_SELECTED_CHARACTER_ID = "selected_character_id"
const val KEY_SELECTED_MODEL_ASSET_PATH = "selected_model_asset_path"
const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
const val KEY_WALLPAPER_OFFSET_X = "wallpaper_offset_x"
const val KEY_WALLPAPER_OFFSET_Y = "wallpaper_offset_y"
const val KEY_WALLPAPER_SCALE = "wallpaper_scale"
const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
const val KEY_DARK_MODE = "dark_mode"
const val KEY_LIQUID_GLASS_ENABLED = "liquid_glass_enabled"
const val KEY_FLOATING_OVERLAY_ENABLED = "floating_overlay_enabled"
const val KEY_FLOATING_OVERLAY_LOCKED = "floating_overlay_locked"
const val KEY_FLOATING_OVERLAY_TOUCH_THROUGH = "floating_overlay_touch_through"
const val KEY_FLOATING_OVERLAY_ITEMS = "floating_overlay_items"

private const val DEFAULT_FLOATING_OVERLAY_X = 48
private const val DEFAULT_FLOATING_OVERLAY_Y = 160
private const val DEFAULT_FLOATING_OVERLAY_WIDTH = 360
private const val DEFAULT_FLOATING_OVERLAY_HEIGHT = 520
private const val FLOATING_OVERLAY_CASCADE_OFFSET = 36

enum class RenderResolution(val value: String, val scale: Float) {
    Half("half", 0.5f),
    TwoThirds("two_thirds", 2f / 3f),
    PointToPoint("point_to_point", 1f),
    SuperSampling("x2", 2f),
    ;

    companion object {
        fun fromValue(value: String?): RenderResolution =
            entries.firstOrNull { it.value == value } ?: PointToPoint
    }
}

@Immutable
data class FloatingLive2DItem(
    val id: String,
    val model: ModelChoice,
    val x: Int = DEFAULT_FLOATING_OVERLAY_X,
    val y: Int = DEFAULT_FLOATING_OVERLAY_Y,
    val width: Int = DEFAULT_FLOATING_OVERLAY_WIDTH,
    val height: Int = DEFAULT_FLOATING_OVERLAY_HEIGHT,
)

@Immutable
data class FloatingOverlaySettings(
    val enabled: Boolean = false,
    val locked: Boolean = true,
    val touchThrough: Boolean = false,
    val items: List<FloatingLive2DItem> = emptyList(),
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FLOATING_OVERLAY_ENABLED, enabled)
            .putBoolean(KEY_FLOATING_OVERLAY_LOCKED, locked)
            .putBoolean(KEY_FLOATING_OVERLAY_TOUCH_THROUGH, touchThrough)
            .putString(KEY_FLOATING_OVERLAY_ITEMS, encodeFloatingItems(items))
            .apply()
    }

    companion object {
        fun load(context: Context): FloatingOverlaySettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return FloatingOverlaySettings(
                enabled = prefs.getBoolean(KEY_FLOATING_OVERLAY_ENABLED, false),
                locked = prefs.getBoolean(KEY_FLOATING_OVERLAY_LOCKED, true),
                touchThrough = prefs.getBoolean(KEY_FLOATING_OVERLAY_TOUCH_THROUGH, false),
                items = decodeFloatingItems(prefs.getString(KEY_FLOATING_OVERLAY_ITEMS, null)),
            )
        }
    }
}

enum class DarkModeSetting(val value: String) {
    On("on"),
    Off("off"),
    System("system"),
    ;

    companion object {
        fun fromValue(value: String?): DarkModeSetting = entries.firstOrNull { it.value == value } ?: System
    }
}

@Immutable
data class ThemeSettings(
    val dynamicColorEnabled: Boolean = false,
    val darkMode: DarkModeSetting = DarkModeSetting.System,
    val liquidGlassEnabled: Boolean = true,
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, dynamicColorEnabled)
            .putString(KEY_DARK_MODE, darkMode.value)
            .putBoolean(KEY_LIQUID_GLASS_ENABLED, liquidGlassEnabled)
            .apply()
    }

    companion object {
        fun load(context: Context): ThemeSettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return ThemeSettings(
                dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false),
                darkMode = DarkModeSetting.fromValue(prefs.getString(KEY_DARK_MODE, null)),
                liquidGlassEnabled = prefs.getBoolean(KEY_LIQUID_GLASS_ENABLED, true),
            )
        }
    }
}

fun DarkModeSetting.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    DarkModeSetting.On -> true
    DarkModeSetting.Off -> false
    DarkModeSetting.System -> systemDark
}

@Immutable
data class RenderSettings(
    val fpsLimit: Int = 60,
    val fpsDisplayEnabled: Boolean = false,
    val vsyncEnabled: Boolean = true,
    val renderResolution: RenderResolution = RenderResolution.PointToPoint,
    val gazeFollowEnabled: Boolean = false,
    val backgroundUri: String? = null,
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FPS_LIMIT, fpsLimit)
            .putBoolean(KEY_FPS_DISPLAY_ENABLED, fpsDisplayEnabled)
            .putBoolean(KEY_VSYNC_ENABLED, vsyncEnabled)
            .putString(KEY_RENDER_RESOLUTION, renderResolution.value)
            .putBoolean(KEY_GAZE_FOLLOW_ENABLED, gazeFollowEnabled)
            .apply {
                if (backgroundUri.isNullOrBlank()) {
                    remove(KEY_LIVE2D_BACKGROUND_URI)
                } else {
                    putString(KEY_LIVE2D_BACKGROUND_URI, backgroundUri)
                }
            }
            .apply()
    }

    companion object {
        fun load(context: Context): RenderSettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return RenderSettings(
                fpsLimit = prefs.getInt(KEY_FPS_LIMIT, 60).coerceIn(15, 120),
                fpsDisplayEnabled = prefs.getBoolean(KEY_FPS_DISPLAY_ENABLED, false),
                vsyncEnabled = prefs.getBoolean(KEY_VSYNC_ENABLED, true),
                renderResolution = RenderResolution.fromValue(prefs.getString(KEY_RENDER_RESOLUTION, null)),
                gazeFollowEnabled = prefs.getBoolean(KEY_GAZE_FOLLOW_ENABLED, false),
                backgroundUri = prefs.getString(KEY_LIVE2D_BACKGROUND_URI, null),
            )
        }
    }
}

fun loadSelectedCharacterId(context: Context): String =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SELECTED_CHARACTER_ID, "kasumi") ?: "kasumi"

fun loadSelectedModelAssetPath(context: Context): String? =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SELECTED_MODEL_ASSET_PATH, null)

fun saveModelSelection(context: Context, characterId: String, model: ModelChoice?) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SELECTED_CHARACTER_ID, characterId)
        .apply {
            if (model == null) {
                remove(KEY_SELECTED_MODEL_ASSET_PATH)
            } else {
                putString(KEY_SELECTED_MODEL_ASSET_PATH, model.modelAssetPath)
            }
        }
        .apply()
}

fun loadPersistedModelChoice(context: Context): ModelChoice? {
    val repository = DataRepository(context)
    val data = repository.load()
    val selectedCharacterId = loadSelectedCharacterId(context)
    val activeCharacterId = when {
        data.characters.containsKey(selectedCharacterId) -> selectedCharacterId
        data.characters.containsKey("kasumi") -> "kasumi"
        else -> data.bands.firstOrNull()
            ?.characters
            ?.firstOrNull { it in data.characters }
            ?: data.characters.keys.firstOrNull()
    } ?: return null
    val models = data.characters[activeCharacterId]?.let { repository.availableModels(it) }.orEmpty()
    val selectedModelPath = loadSelectedModelAssetPath(context)
    return selectedModelPath?.let { path -> models.firstOrNull { it.modelAssetPath == path } }
        ?: models.firstOrNull()
}

fun persistBackgroundUri(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

fun isWallpaperEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_WALLPAPER_ENABLED, false)

fun setWallpaperEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_WALLPAPER_ENABLED, enabled)
        .apply()
}

fun addFloatingLive2DItem(context: Context, model: ModelChoice): FloatingOverlaySettings {
    val settings = FloatingOverlaySettings.load(context)
    val index = settings.items.size
    val item = FloatingLive2DItem(
        id = "${System.currentTimeMillis()}_$index",
        model = model,
        x = DEFAULT_FLOATING_OVERLAY_X + index * FLOATING_OVERLAY_CASCADE_OFFSET,
        y = DEFAULT_FLOATING_OVERLAY_Y + index * FLOATING_OVERLAY_CASCADE_OFFSET,
    )
    return settings.copy(items = settings.items + item).also { it.save(context) }
}

fun resetFloatingLive2DItemPositions(context: Context): FloatingOverlaySettings {
    val settings = FloatingOverlaySettings.load(context)
    return settings.copy(
        items = settings.items.mapIndexed { index, item ->
            item.copy(
                x = DEFAULT_FLOATING_OVERLAY_X + index * FLOATING_OVERLAY_CASCADE_OFFSET,
                y = DEFAULT_FLOATING_OVERLAY_Y + index * FLOATING_OVERLAY_CASCADE_OFFSET,
            )
        },
    ).also { it.save(context) }
}

fun removeFloatingLive2DItem(context: Context, itemId: String): FloatingOverlaySettings {
    val settings = FloatingOverlaySettings.load(context)
    return settings.copy(items = settings.items.filterNot { it.id == itemId }).also { it.save(context) }
}

fun saveFloatingLive2DItemBounds(context: Context, itemId: String, x: Int, y: Int, width: Int, height: Int) {
    val settings = FloatingOverlaySettings.load(context)
    settings.copy(
        items = settings.items.map { item ->
            if (item.id == itemId) {
                item.copy(
                    x = x,
                    y = y,
                    width = width.coerceIn(180, 1200),
                    height = height.coerceIn(240, 1600),
                )
            } else {
                item
            }
        },
    ).save(context)
}

fun loadWallpaperBackgroundUri(context: Context): String? =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_WALLPAPER_BACKGROUND_URI, null)

fun saveWallpaperBackgroundUri(context: Context, uri: String?) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (uri.isNullOrBlank()) {
                remove(KEY_WALLPAPER_BACKGROUND_URI)
            } else {
                putString(KEY_WALLPAPER_BACKGROUND_URI, uri)
            }
        }
        .apply()
}

fun loadWallpaperTransform(context: Context): Live2DTransform {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    return Live2DTransform(
        offsetX = prefs.getFloat(KEY_WALLPAPER_OFFSET_X, 0f),
        offsetY = prefs.getFloat(KEY_WALLPAPER_OFFSET_Y, 0f),
        scale = prefs.getFloat(KEY_WALLPAPER_SCALE, 1f).coerceIn(0.4f, 3f),
    )
}

fun saveWallpaperTransform(context: Context, transform: Live2DTransform) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_WALLPAPER_OFFSET_X, transform.offsetX)
        .putFloat(KEY_WALLPAPER_OFFSET_Y, transform.offsetY)
        .putFloat(KEY_WALLPAPER_SCALE, transform.scale.coerceIn(0.4f, 3f))
        .apply()
}

private fun encodeFloatingItems(items: List<FloatingLive2DItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("characterId", item.model.characterId)
                .put("characterName", item.model.characterName)
                .put("costumeId", item.model.costumeId)
                .put("costumeName", item.model.costumeName)
                .put("modelAssetPath", item.model.modelAssetPath)
                .put("x", item.x)
                .put("y", item.y)
                .put("width", item.width)
                .put("height", item.height),
        )
    }
    return array.toString()
}

private fun decodeFloatingItems(value: String?): List<FloatingLive2DItem> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val model = ModelChoice(
                    characterId = item.getString("characterId"),
                    characterName = item.getString("characterName"),
                    costumeId = item.getString("costumeId"),
                    costumeName = item.getString("costumeName"),
                    modelAssetPath = item.getString("modelAssetPath"),
                )
                add(
                    FloatingLive2DItem(
                        id = item.optString("id", "${System.currentTimeMillis()}_$index"),
                        model = model,
                        x = item.optInt("x", DEFAULT_FLOATING_OVERLAY_X),
                        y = item.optInt("y", DEFAULT_FLOATING_OVERLAY_Y),
                        width = item.optInt("width", DEFAULT_FLOATING_OVERLAY_WIDTH).coerceIn(180, 1200),
                        height = item.optInt("height", DEFAULT_FLOATING_OVERLAY_HEIGHT).coerceIn(240, 1600),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}


// ==================== 壁纸交互 / 动画 / 原壁纸保留 ====================
const val KEY_TOUCH_ANIMATION_ENABLED = "touch_animation_enabled"
const val KEY_TOUCH_ANIMATIONS = "touch_animations"
const val KEY_SWIPE_ANIMATION_ENABLED = "swipe_animation_enabled"
const val KEY_IDLE_ANIMATION_ENABLED = "idle_animation_enabled"
const val KEY_IDLE_ANIMATIONS = "idle_animations"
const val KEY_IDLE_INTERVAL_MS = "idle_interval_ms"
const val KEY_LAST_WALLPAPER_ACTION = "last_wallpaper_action"
const val KEY_LAST_WALLPAPER_ACTION_AT = "last_wallpaper_action_at"
const val KEY_WALLPAPER_ORIGINAL_BACKUP_PATH = "wallpaper_original_backup_path"

/** 可选动画基础名（Lua 端 __bp_action 用基础名匹配，如 smile 匹配 smile01） */
val ANIMATION_CHOICES: List<String> = listOf(
    "smile", "kandou", "kime", "sad", "cry", "serious", "thinking",
    "surprised", "angry", "shame", "sing", "nf", "nnf", "bye",
)

fun encodeAnimations(values: Set<String>): String = values.joinToString(",")
fun decodeAnimations(value: String?): Set<String> =
    value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

fun loadTouchAnimationEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_TOUCH_ANIMATION_ENABLED, true)

fun saveTouchAnimationEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_TOUCH_ANIMATION_ENABLED, enabled).apply()
}

fun loadTouchAnimations(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    val stored = decodeAnimations(prefs.getString(KEY_TOUCH_ANIMATIONS, null))
    return if (stored.isEmpty()) setOf("smile", "kandou", "kime", "sad", "surprised", "thinking") else stored
}

fun saveTouchAnimations(context: Context, values: Set<String>) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_TOUCH_ANIMATIONS, encodeAnimations(values)).apply()
}

fun loadSwipeAnimationEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SWIPE_ANIMATION_ENABLED, true)

fun saveSwipeAnimationEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_SWIPE_ANIMATION_ENABLED, enabled).apply()
}

fun loadIdleAnimationEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_IDLE_ANIMATION_ENABLED, false)

fun saveIdleAnimationEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_IDLE_ANIMATION_ENABLED, enabled).apply()
}

fun loadIdleAnimations(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    val stored = decodeAnimations(prefs.getString(KEY_IDLE_ANIMATIONS, null))
    return if (stored.isEmpty()) setOf("smile", "kandou", "thinking") else stored
}

fun saveIdleAnimations(context: Context, values: Set<String>) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_IDLE_ANIMATIONS, encodeAnimations(values)).apply()
}

fun loadIdleIntervalMs(context: Context): Long =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_IDLE_INTERVAL_MS, 8_000L).coerceIn(3_000L, 60_000L)

fun saveIdleIntervalMs(context: Context, interval: Long) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putLong(KEY_IDLE_INTERVAL_MS, interval.coerceIn(3_000L, 60_000L)).apply()
}

/** 最近一次在桌面播放的动作（用于 surface 重建后短暂恢复动作状态）。 */
fun saveLastWallpaperAction(context: Context, action: String) {
    if (action.isBlank()) return
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LAST_WALLPAPER_ACTION, action)
        .putLong(KEY_LAST_WALLPAPER_ACTION_AT, System.currentTimeMillis())
        .apply()
}

fun clearLastWallpaperAction(context: Context) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_LAST_WALLPAPER_ACTION)
        .remove(KEY_LAST_WALLPAPER_ACTION_AT)
        .apply()
}

fun loadLastWallpaperAction(context: Context): Pair<String, Long> {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    return (prefs.getString(KEY_LAST_WALLPAPER_ACTION, null) ?: "") to
        prefs.getLong(KEY_LAST_WALLPAPER_ACTION_AT, 0L)
}

fun loadWallpaperOriginalBackupPath(context: Context): String? =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_WALLPAPER_ORIGINAL_BACKUP_PATH, null)

fun saveWallpaperOriginalBackupPath(context: Context, path: String?) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().apply {
            if (path.isNullOrBlank()) remove(KEY_WALLPAPER_ORIGINAL_BACKUP_PATH)
            else putString(KEY_WALLPAPER_ORIGINAL_BACKUP_PATH, path)
        }.apply()
}


// ==================== mimo（语音克隆 TTS） ====================
const val KEY_MIMO_API_KEY = "mimo_api_key"

fun loadMimoApiKey(context: Context): String =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_MIMO_API_KEY, null).orEmpty().trim()

fun saveMimoApiKey(context: Context, key: String) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_MIMO_API_KEY, key.trim()).apply()
}


// ==================== 语音合成（服务商 + 模型 + 密钥） ====================
const val VOICE_PROVIDER_MIMO = "mimo"
const val VOICE_PROVIDER_CUSTOM = "custom"
const val VOICE_MIMO_DEFAULT_MODEL = "mimo-v2.5-tts-voiceclone"
const val VOICE_MIMO_DEFAULT_BASE = "https://api.xiaomimimo.com/v1"

const val KEY_VOICE_PROVIDER = "voice_provider"
const val KEY_VOICE_BASE_URL = "voice_base_url"
const val KEY_VOICE_MODEL = "voice_model"
const val KEY_VOICE_API_KEY = "voice_api_key"
const val KEY_BUBBLE_ENABLED = "bubble_enabled"

data class VoiceSettings(
    val provider: String = VOICE_PROVIDER_MIMO,
    val baseUrl: String = VOICE_MIMO_DEFAULT_BASE,
    val model: String = VOICE_MIMO_DEFAULT_MODEL,
    val apiKey: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.isNotBlank()
    fun normalized(): VoiceSettings = copy(
        baseUrl = baseUrl.trim().trimEnd('/'),
        model = model.trim(),
        apiKey = apiKey.trim(),
    )
    fun endpoint(): String =
        if (baseUrl.endsWith("/chat/completions", ignoreCase = true)) baseUrl else "$baseUrl/chat/completions"
    fun save(context: Context) {
        val value = normalized()
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VOICE_PROVIDER, value.provider)
            .putString(KEY_VOICE_BASE_URL, value.baseUrl)
            .putString(KEY_VOICE_MODEL, value.model)
            .putString(KEY_VOICE_API_KEY, value.apiKey)
            .apply()
    }
    companion object {
        fun load(context: Context): VoiceSettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val legacyMimoKey = prefs.getString(KEY_MIMO_API_KEY, null).orEmpty()
            return VoiceSettings(
                provider = prefs.getString(KEY_VOICE_PROVIDER, VOICE_PROVIDER_MIMO) ?: VOICE_PROVIDER_MIMO,
                baseUrl = prefs.getString(KEY_VOICE_BASE_URL, VOICE_MIMO_DEFAULT_BASE) ?: VOICE_MIMO_DEFAULT_BASE,
                model = prefs.getString(KEY_VOICE_MODEL, VOICE_MIMO_DEFAULT_MODEL) ?: VOICE_MIMO_DEFAULT_MODEL,
                apiKey = prefs.getString(KEY_VOICE_API_KEY, null)?.takeIf { it.isNotBlank() } ?: legacyMimoKey,
            )
        }
    }
}

fun loadBubbleEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_BUBBLE_ENABLED, true)

fun saveBubbleEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply()
}

const val KEY_BUBBLE_DURATION_SECONDS = "bubble_duration_seconds"

fun loadBubbleDurationSeconds(context: Context): Int =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_BUBBLE_DURATION_SECONDS, 6)
        .coerceIn(1, 60)

fun saveBubbleDurationSeconds(context: Context, seconds: Int) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putInt(KEY_BUBBLE_DURATION_SECONDS, seconds.coerceIn(1, 60)).apply()
}


// ==================== 角色自定义系统提示词（覆盖内置人物设定） ====================
const val KEY_CHARACTER_CUSTOM_PROMPT_PREFIX = "character_custom_prompt_"

fun loadCharacterCustomPrompt(context: Context, characterId: String): String? =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CHARACTER_CUSTOM_PROMPT_PREFIX + characterId, null)
        ?.takeIf(String::isNotBlank)

fun saveCharacterCustomPrompt(context: Context, characterId: String, prompt: String?) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().apply {
            if (prompt.isNullOrBlank()) {
                remove(KEY_CHARACTER_CUSTOM_PROMPT_PREFIX + characterId)
            } else {
                putString(KEY_CHARACTER_CUSTOM_PROMPT_PREFIX + characterId, prompt.trim())
            }
        }.apply()
}

// ==================== Line UI（仿 LINE 对话界面） ====================
const val KEY_LINE_UI_ENABLED = "line_ui_enabled"

fun loadLineUiEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_LINE_UI_ENABLED, false)

fun saveLineUiEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_LINE_UI_ENABLED, enabled).apply()
}

// ==================== 角色记忆（对话消息长按载入 → 附加到系统提示词） ====================
const val KEY_CHARACTER_MEMORY_PREFIX = "character_memory_"

fun loadCharacterMemory(context: Context, characterId: String): String =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CHARACTER_MEMORY_PREFIX + characterId, null)
        .orEmpty()
        .trim()

/** 把一条记忆追加到角色记忆中（每行一条，自动去重相邻重复内容）。 */
fun appendCharacterMemory(context: Context, characterId: String, entry: String) {
    val text = entry.trim()
    if (text.isEmpty()) return
    val current = loadCharacterMemory(context, characterId)
    if (current == text || current.contains("\n- $text\n") || current.endsWith("\n- $text")) return
    val newValue = if (current.isEmpty()) "- $text" else "$current\n- $text"
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_CHARACTER_MEMORY_PREFIX + characterId, newValue).apply()
}

fun clearCharacterMemory(context: Context, characterId: String) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().remove(KEY_CHARACTER_MEMORY_PREFIX + characterId).apply()
}

// ==================== 回复后语音（对话回复 → 克隆 TTS 播放） ====================
const val KEY_REPLY_VOICE_ENABLED = "reply_voice_enabled"

fun loadReplyVoiceEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_REPLY_VOICE_ENABLED, false)

fun saveReplyVoiceEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_REPLY_VOICE_ENABLED, enabled).apply()
}

// ==================== 内置语音（台词 → 克隆 TTS） ====================
const val KEY_BUILTIN_VOICE_ENABLED = "builtin_voice_enabled"

fun loadBuiltinVoiceEnabled(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_BUILTIN_VOICE_ENABLED, false)

fun saveBuiltinVoiceEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_BUILTIN_VOICE_ENABLED, enabled).apply()
}

// ==================== 内置语音语言（中文 / 日本語） ====================
const val KEY_BUILTIN_VOICE_LANGUAGE = "builtin_voice_language"

enum class BuiltinVoiceLanguage(val value: String, val label: String) {
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    ;

    companion object {
        fun fromValue(value: String?): BuiltinVoiceLanguage =
            entries.firstOrNull { it.value == value } ?: ZH
    }
}

fun loadBuiltinVoiceLanguage(context: Context): BuiltinVoiceLanguage =
    BuiltinVoiceLanguage.fromValue(
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BUILTIN_VOICE_LANGUAGE, null)
    )

fun saveBuiltinVoiceLanguage(context: Context, language: BuiltinVoiceLanguage) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_BUILTIN_VOICE_LANGUAGE, language.value).apply()
}

// ==================== 壁纸渲染模式（单模型 / 多模型） ====================
const val KEY_WALLPAPER_MODE = "wallpaper_mode"

enum class WallpaperMode(val value: String, val label: String) {
    SINGLE("single", "单模型"),
    MULTI("multi", "多模型"),
    ;

    companion object {
        fun fromValue(value: String?): WallpaperMode =
            entries.firstOrNull { it.value == value } ?: SINGLE
    }
}

fun loadWallpaperMode(context: Context): WallpaperMode =
    WallpaperMode.fromValue(
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER_MODE, null)
    )

fun saveWallpaperMode(context: Context, mode: WallpaperMode) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_WALLPAPER_MODE, mode.value).apply()
}

// ==================== 壁纸多模型放置 ====================
const val KEY_WALLPAPER_MODELS = "wallpaper_models"

@Immutable
data class WallpaperModelPlacement(
    val id: String,
    val characterId: String,
    val characterName: String = "",
    val costumeId: String = "",
    val costumeName: String = "",
    val modelAssetPath: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val enabled: Boolean = true,
) {
    fun toModelChoice(): ModelChoice =
        ModelChoice(characterId, characterName, costumeId, costumeName, modelAssetPath)

    fun toTransform(): Live2DTransform = Live2DTransform(offsetX, offsetY, scale)
}

@Immutable
data class WallpaperMultiModelSettings(
    val models: List<WallpaperModelPlacement> = emptyList(),
) {
    fun save(context: Context) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_WALLPAPER_MODELS, encodeWallpaperModels(models)).apply()
    }

    companion object {
        fun load(context: Context): WallpaperMultiModelSettings {
            val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            return WallpaperMultiModelSettings(
                models = decodeWallpaperModels(prefs.getString(KEY_WALLPAPER_MODELS, null)),
            )
        }
    }
}

private fun encodeWallpaperModels(models: List<WallpaperModelPlacement>): String {
    val array = JSONArray()
    models.forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("characterId", item.characterId)
                .put("characterName", item.characterName)
                .put("costumeId", item.costumeId)
                .put("costumeName", item.costumeName)
                .put("modelAssetPath", item.modelAssetPath)
                .put("offsetX", item.offsetX)
                .put("offsetY", item.offsetY)
                .put("scale", item.scale)
                .put("enabled", item.enabled),
        )
    }
    return array.toString()
}

private fun decodeWallpaperModels(value: String?): List<WallpaperModelPlacement> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WallpaperModelPlacement(
                        id = item.optString("id", "${System.currentTimeMillis()}_$index"),
                        characterId = item.getString("characterId"),
                        characterName = item.optString("characterName", ""),
                        costumeId = item.optString("costumeId", ""),
                        costumeName = item.optString("costumeName", ""),
                        modelAssetPath = item.getString("modelAssetPath"),
                        offsetX = item.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = item.optDouble("offsetY", 0.0).toFloat(),
                        scale = item.optDouble("scale", 1.0).toFloat().coerceIn(0.4f, 3f),
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
