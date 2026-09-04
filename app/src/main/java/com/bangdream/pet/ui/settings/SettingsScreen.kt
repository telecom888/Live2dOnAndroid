package com.bangdream.pet.ui.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bangdream.pet.DarkModeSetting
import com.bangdream.pet.I18n
import com.bangdream.pet.RenderResolution
import com.bangdream.pet.RenderSettings
import com.bangdream.pet.ui.design.VisualGuard
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.ui.design.appHazeSource
import com.bangdream.pet.ui.design.appLiquidGlass
import com.bangdream.pet.ui.design.appPressScale
import com.bangdream.pet.ui.design.rememberLiquidGlassState
import com.bangdream.pet.ThemeSettings
import android.widget.Toast
import com.bangdream.pet.ANIMATION_CHOICES
import com.bangdream.pet.VOICE_PROVIDER_CUSTOM
import com.bangdream.pet.VOICE_PROVIDER_MIMO
import com.bangdream.pet.VoiceSettings
import com.bangdream.pet.loadBubbleEnabled
import com.bangdream.pet.loadLineUiEnabled
import com.bangdream.pet.saveLineUiEnabled
import com.bangdream.pet.loadLineNavEnabled
import com.bangdream.pet.saveLineNavEnabled
import com.bangdream.pet.loadBubbleDurationSeconds
import com.bangdream.pet.saveBubbleDurationSeconds
import com.bangdream.pet.loadReplyVoiceEnabled
import com.bangdream.pet.BuiltinVoiceLanguage
import com.bangdream.pet.loadBuiltinVoiceLanguage
import com.bangdream.pet.loadDesktopLipSyncEnabled
import com.bangdream.pet.loadDesktopVoiceEnabled
import com.bangdream.pet.saveBuiltinVoiceLanguage
import com.bangdream.pet.saveDesktopLipSyncEnabled
import com.bangdream.pet.saveDesktopVoiceEnabled
import com.bangdream.pet.saveReplyVoiceEnabled
import com.bangdream.pet.saveBubbleEnabled
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.AvatarManager
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import com.bangdream.pet.isWallpaperEnabled
import com.bangdream.pet.llm.ChatHistoryRepository
import com.bangdream.pet.llm.LlmHeader
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.llm.loadOrCreateSessionId
import com.bangdream.pet.llm.ThinkingMode
import com.bangdream.pet.loadWallpaperMode
import com.bangdream.pet.saveWallpaperMode
import com.bangdream.pet.loadIdleAnimationEnabled
import com.bangdream.pet.loadMimoApiKey
import com.bangdream.pet.loadIdleAnimations
import com.bangdream.pet.loadIdleIntervalMs
import com.bangdream.pet.loadSwipeAnimationEnabled
import com.bangdream.pet.loadTouchAnimationEnabled
import com.bangdream.pet.loadTouchAnimations
import com.bangdream.pet.loadWallpaperBackgroundUri
import com.bangdream.pet.persistBackgroundUri
import com.bangdream.pet.saveIdleAnimationEnabled
import com.bangdream.pet.saveMimoApiKey
import com.bangdream.pet.saveIdleAnimations
import com.bangdream.pet.saveIdleIntervalMs
import com.bangdream.pet.saveSwipeAnimationEnabled
import com.bangdream.pet.saveTouchAnimationEnabled
import com.bangdream.pet.saveTouchAnimations
import com.bangdream.pet.saveWallpaperBackgroundUri
import com.bangdream.pet.setWallpaperEnabled
import com.bangdream.pet.voice.BuiltinVoiceManager
import com.bangdream.pet.voice.VoiceSamples
import com.bangdream.pet.wallpaper.Live2DWallpaperService
import com.bangdream.pet.wallpaper.WallpaperBackup
import com.bangdream.pet.wallpaper.WallpaperUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    selectedModel: ModelChoice?,
    themeSettings: ThemeSettings,
    onThemeSettingsChanged: (ThemeSettings) -> Unit,
    renderSettings: RenderSettings,
    onRenderSettingsChanged: (RenderSettings) -> Unit,
    topInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var wallpaperEnabled by remember { mutableStateOf(isWallpaperEnabled(appContext)) }
    var wallpaperBackgroundUri by remember { mutableStateOf(loadWallpaperBackgroundUri(appContext)) }
    var wallpaperMode by remember { mutableStateOf(loadWallpaperMode(appContext)) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "section_appearance") { SettingsSectionHeader("外观") }
        item(key = "theme") {
            ThemeSettingsCard(
                settings = themeSettings,
                onSettingsChanged = onThemeSettingsChanged,
            )
        }
        item(key = "line_ui") {
            LineUiSettingsCard()
        }
        item(key = "render") {
            RenderSettingsCard(
                settings = renderSettings,
                onSettingsChanged = { settings ->
                    onRenderSettingsChanged(settings)
                },
            )
        }
        item(key = "section_desktop") { SettingsSectionHeader("桌面壁纸") }
        item(key = "wallpaper") {
            WallpaperSettingsCard(
                enabled = wallpaperEnabled,
                backgroundUri = wallpaperBackgroundUri,
                mode = wallpaperMode,
                onModeChanged = { mode ->
                    wallpaperMode = mode
                    saveWallpaperMode(appContext, mode)
                },
                onEnabledChanged = { enabled ->
                    wallpaperEnabled = enabled
                    setWallpaperEnabled(appContext, enabled)
                },
                onBackgroundChanged = { uri ->
                    wallpaperBackgroundUri = uri
                    saveWallpaperBackgroundUri(appContext, uri)
                },
                onAdjustPosition = {
                    if (wallpaperMode == com.bangdream.pet.WallpaperMode.MULTI) {
                        context.startActivity(Intent(context, com.bangdream.pet.MultiWallpaperManageActivity::class.java))
                    } else {
                        context.startActivity(Intent(context, com.bangdream.pet.WallpaperAdjustActivity::class.java))
                    }
                },
            )
        }
        item(key = "original_wallpaper") {
            OriginalWallpaperCard(
                onCaptured = { uri ->
                    wallpaperBackgroundUri = uri
                    saveWallpaperBackgroundUri(appContext, uri)
                },
            )
        }
        item(key = "interaction") {
            InteractionSettingsCard()
        }
        item(key = "section_ai") { SettingsSectionHeader("AI 对话") }
        item(key = "llm") {
            LlmSettingsEntryCard()
        }
        item(key = "section_voice") { SettingsSectionHeader("语音") }
        item(key = "voice_settings") {
            VoiceSettingsCard()
        }
        item(key = "builtin_voice") {
            BuiltinVoiceCard(selectedModel = selectedModel)
        }
        item(key = "voice_samples") {
            VoiceSamplesCard(selectedModel = selectedModel)
        }
        item(key = "section_about") { SettingsSectionHeader("关于") }
        item(key = "info") {
            InfoCard(
                I18n.t("settings_about"),
                I18n.t("settings_about_text"),
            )
        }
    }
}

@Composable
private fun LineUiSettingsCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var lineUiEnabled by remember { mutableStateOf(loadLineUiEnabled(appContext)) }
    var lineNavEnabled by remember { mutableStateOf(loadLineNavEnabled(appContext)) }
    var showLineNavWarning by remember { mutableStateOf(false) }
    var userAvatarTick by remember { mutableStateOf(0) }
    val userAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = AvatarManager.importUserAvatar(appContext, uri)
            Toast.makeText(appContext, if (ok) "我的头像已设置" else "头像导入失败", Toast.LENGTH_SHORT).show()
            userAvatarTick++
        }
    }
    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("对话界面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Line UI（仿 LINE 气泡）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "开启后消息列表使用 LINE 风格气泡并显示角色头像",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = lineUiEnabled,
                    onCheckedChange = {
                        lineUiEnabled = it
                        saveLineUiEnabled(appContext, it)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导航栏 Line（实验性）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "开启后底部导航显示 Line；功能不保证稳定",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = lineNavEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showLineNavWarning = true
                        } else {
                            lineNavEnabled = false
                            saveLineNavEnabled(appContext, false)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatarPreview(userAvatarTick)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("我的头像", fontWeight = FontWeight.SemiBold)
                    Text(
                        "默认不显示；设置后显示在 Line 消息右侧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (remember(userAvatarTick) { AvatarManager.userAvatarFile(appContext) } != null) {
                    TextButton(onClick = {
                        AvatarManager.clearUserAvatar(appContext)
                        userAvatarTick++
                        Toast.makeText(appContext, "已清除我的头像", Toast.LENGTH_SHORT).show()
                    }) { Text("清除") }
                }
                FilledTonalButton(onClick = { userAvatarLauncher.launch("image/*") }) { Text("设置") }
            }
        }
    }

    if (showLineNavWarning) {
        AlertDialog(
            onDismissRequest = { showLineNavWarning = false },
            title = { Text("实验性功能") },
            text = {
                Text("Line 多角色旁观对话为实验性功能，不保证稳定性，可能出现异常或性能问题。是否开启导航栏 Line？")
            },
            confirmButton = {
                TextButton(onClick = {
                    lineNavEnabled = true
                    saveLineNavEnabled(appContext, true)
                    showLineNavWarning = false
                }) { Text("开启") }
            },
            dismissButton = {
                TextButton(onClick = { showLineNavWarning = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UserAvatarPreview(tick: Int, size: Dp = 44.dp) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val file = remember(tick) { AvatarManager.userAvatarFile(appContext) }
    val key = remember(tick, file?.lastModified()) {
        if (file != null) "user-avatar:${file.lastModified()}" else "user-avatar:none"
    }
    var bitmap by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val decoded = file?.let {
            withContext(Dispatchers.IO) { SampledImageDecoder.decodeBytes(it.readBytes(), 128) }
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        bitmap = decoded
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LlmSettingsEntryCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var settings by remember { mutableStateOf(LlmSettings.load(appContext)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settings = LlmSettings.load(appContext)
    }
    Card(
        onClick = {
            launcher.launch(Intent(context, LlmSettingsActivity::class.java))
        },
        modifier = Modifier.fillMaxWidth().appPressScale(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(I18n.t("settings_llm_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (settings.isConfigured) I18n.t("settings_llm_configured", settings.model) else I18n.t("settings_llm_not_configured"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

/** LLM 服务商预设：标签 / baseUrl / model。提到顶层，避免每次重组重建列表。 */
private val LLM_PROVIDER_PRESETS = listOf(
    Triple("DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
    Triple("OpenCode Go", "https://opencode.ai/zen/go/v1", "mimo-v2.5"),
    Triple("小米 mimo", "https://api.xiaomimimo.com/v1", "mimo-v2.5"),
)

/** LLM 自定义请求头编辑：OpenCode Go 等需要 x-opencode-session（稳定会话 ID）之类的请求头。 */
@Composable
private fun LlmHeadersEditor(
    headers: List<LlmHeader>,
    onHeadersChange: (List<LlmHeader>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("自定义请求头（可选）", fontWeight = FontWeight.SemiBold)
        Text(
            "随每个模型请求附加的 HTTP 头。选择 OpenCode Go 预设会自动预填 x-opencode-session（稳定会话 ID）；也可自行添加其它头。Authorization / Content-Type / Accept 由应用自动设置，无需重复。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (headers.isEmpty()) {
            Text("未设置", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        headers.forEachIndexed { index, header ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = header.name,
                    onValueChange = { value ->
                        onHeadersChange(headers.mapIndexed { i, h -> if (i == index) h.copy(name = value) else h })
                    },
                    modifier = Modifier.weight(0.7f),
                    label = { Text("名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = header.value,
                    onValueChange = { value ->
                        onHeadersChange(headers.mapIndexed { i, h -> if (i == index) h.copy(value = value) else h })
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("值") },
                    singleLine = true,
                )
                IconButton(onClick = {
                    onHeadersChange(headers.filterIndexed { i, _ -> i != index })
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        FilledTonalButton(onClick = { onHeadersChange(headers + LlmHeader()) }) {
            Text("＋ 添加请求头")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LlmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var draft by remember { mutableStateOf(LlmSettings.load(appContext)) }
    var maxTokensText by remember { mutableStateOf(draft.maxTokens.toString()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var thinkingMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var clearAllFailed by remember { mutableStateOf(false) }
    var mimoKey by remember { mutableStateOf(loadMimoApiKey(appContext)) }
    val scope = rememberCoroutineScope()
    val hazeState = rememberLiquidGlassState()
    // 之前每次重组都同步读一次 SharedPreferences
    val glassEnabled = remember(appContext) {
        ThemeSettings.load(appContext).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(appContext)
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .appHazeSource(hazeState),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topInset + 72.dp,
                end = 16.dp,
                bottom = 20.dp,
            ),
        ) {
            item(key = "llm_form") {
                SettingsSectionCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Row 会顺序测量、把剩余宽度丢给最后一个按钮，窄屏/大字号下「小米 mimo」被压扁并换行成瘦长块。
                // 改用 FlowRow：各按钮按自身宽度排布，放不下时整体换行；再配合紧凑内边距与单行文本兜底。
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LLM_PROVIDER_PRESETS.forEach { (label, presetBaseUrl, presetModel) ->
                        FilledTonalButton(
                            onClick = {
                                var next = draft.copy(baseUrl = presetBaseUrl, model = presetModel)
                                if (label == "OpenCode Go") {
                                    // OpenCode Go：自动预填 x-opencode-session（名称 + 稳定会话 ID 值），UI 会直接显示该行
                                    val session = loadOrCreateSessionId(appContext)
                                    val existing = next.headers.indexOfFirst {
                                        it.name.equals("x-opencode-session", ignoreCase = true)
                                    }
                                    next = if (existing >= 0) {
                                        next.copy(
                                            headers = next.headers.mapIndexed { i, h ->
                                                if (i == existing && h.value.isBlank()) h.copy(value = session) else h
                                            },
                                        )
                                    } else {
                                        next.copy(headers = next.headers + LlmHeader(name = "x-opencode-session", value = session))
                                    }
                                } else {
                                    // 其它 provider：移除自动加的 opencode 会话头（用户自填的其它头保留）
                                    next = next.copy(
                                        headers = next.headers.filterNot {
                                            it.name.equals("x-opencode-session", ignoreCase = true)
                                        },
                                    )
                                }
                                draft = next
                                saved = false
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it); saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_base_url")) },
                    supportingText = { Text(I18n.t("settings_llm_http_warning")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it); saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_api_key")) },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it); saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_model")) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(I18n.t("settings_llm_thinking"), fontWeight = FontWeight.SemiBold)
                        Text(thinkingModeLabel(draft.thinkingMode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { thinkingMenuExpanded = true }) { Text(thinkingModeLabel(draft.thinkingMode)) }
                        DropdownMenu(
                            expanded = thinkingMenuExpanded,
                            onDismissRequest = { thinkingMenuExpanded = false },
                        ) {
                            ThinkingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(thinkingModeLabel(mode)) },
                                    onClick = {
                                        draft = draft.copy(thinkingMode = mode)
                                        saved = false
                                        thinkingMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(I18n.t("settings_llm_temperature"), fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.1f", draft.temperature), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = draft.temperature,
                        onValueChange = { draft = draft.copy(temperature = (it * 10).roundToInt() / 10f); saved = false },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                }
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { value ->
                        maxTokensText = value.filter(Char::isDigit).take(5)
                        maxTokensText.toIntOrNull()?.takeIf { it in 1..32_768 }?.let { maxTokens ->
                            draft = draft.copy(maxTokens = maxTokens)
                        }
                        saved = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_max_tokens")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("上下文窗口（预估基准）", fontWeight = FontWeight.SemiBold)
                        Text(
                            "不限制发送；仅用于对话详情页上下文占用预估显示",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box {
                        TextButton(onClick = { contextMenuExpanded = true }) {
                            Text(contextTokensLabel(draft.contextTokens))
                        }
                        DropdownMenu(
                            expanded = contextMenuExpanded,
                            onDismissRequest = { contextMenuExpanded = false },
                        ) {
                            CONTEXT_TOKEN_OPTIONS.forEach { tokens ->
                                DropdownMenuItem(
                                    text = { Text(contextTokensLabel(tokens)) },
                                    onClick = {
                                        draft = draft.copy(contextTokens = tokens)
                                        saved = false
                                        contextMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("图片输入（多模态）", fontWeight = FontWeight.SemiBold)
                        Text(
                            "mimo-v2.5 / deepseek-v4-flash-vision-exp 支持；开启后输入框可附加图片（单张或多张）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = draft.imageInputEnabled,
                        onCheckedChange = { draft = draft.copy(imageInputEnabled = it); saved = false },
                    )
                }
                LlmHeadersEditor(
                    headers = draft.headers,
                    onHeadersChange = { updated ->
                        draft = draft.copy(headers = updated)
                        saved = false
                    },
                )
                OutlinedTextField(
                    value = mimoKey,
                    onValueChange = { mimoKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("mimo TTS 音色克隆 Key（免费）") },
                    supportingText = { Text("用于 docs/mimo-tts-voiceclone.txt 的语音合成") },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = draft.baseUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") } &&
                        draft.apiKey.isNotBlank() && draft.model.isNotBlank() &&
                        maxTokensText.toIntOrNull()?.let { it in 1..32_768 } == true,
                    onClick = {
                        maxTokensText.toIntOrNull()?.let { maxTokens ->
                            draft = draft.copy(maxTokens = maxTokens).normalized()
                            maxTokensText = draft.maxTokens.toString()
                            draft.save(appContext)
                            saveMimoApiKey(appContext, mimoKey)
                            saved = true
                        }
                    },
                ) { Text(if (saved) I18n.t("settings_llm_saved") else I18n.t("settings_llm_save")) }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { confirmClearAll = true }) {
                    Text(I18n.t("settings_llm_clear_all"))
                }
                    }
                }
            }
        }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(I18n.t("settings_llm_clear_all")) },
            text = { Text(I18n.t("settings_llm_clear_all_confirm")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    scope.launch {
                        val cleared = withContext(Dispatchers.IO) {
                            runCatching { ChatHistoryRepository(appContext).clearAll() }.isSuccess
                        }
                        clearAllFailed = !cleared
                    }
                }) { Text(I18n.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text(I18n.t("cancel")) } },
        )
    }

    if (clearAllFailed) {
        AlertDialog(
            onDismissRequest = { clearAllFailed = false },
            title = { Text(I18n.t("settings_llm_clear_all")) },
            text = { Text(I18n.t("chat_history_delete_failed")) },
            confirmButton = {
                TextButton(onClick = { clearAllFailed = false }) { Text(I18n.t("confirm")) }
            },
        )
    }

    CenterAlignedTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter).appLiquidGlass(hazeState, enabled = glassEnabled),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(I18n.t("settings_llm_title"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        I18n.t("settings_llm_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = I18n.t("back"))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
            ),
        )
    }
}

private val CONTEXT_TOKEN_OPTIONS = listOf(
    32_768,      // 32K
    65_536,      // 64K
    131_072,     // 128K
    262_144,     // 256K
    524_288,     // 512K
    1_048_576,   // 1M
    2_097_152,   // 2M
)

private fun contextTokensLabel(tokens: Int): String = when (tokens) {
    32_768 -> "32K"
    65_536 -> "64K"
    131_072 -> "128K"
    262_144 -> "256K"
    524_288 -> "512K"
    1_048_576 -> "1M"
    2_097_152 -> "2M"
    else -> "${tokens / 1024}K"
}

private fun thinkingModeLabel(mode: ThinkingMode): String = when (mode) {
    ThinkingMode.Auto -> I18n.t("settings_llm_thinking_auto")
    ThinkingMode.Enabled -> I18n.t("settings_llm_thinking_on")
    ThinkingMode.Disabled -> I18n.t("settings_llm_thinking_off")
}

@Composable
private fun ThemeSettingsCard(
    settings: ThemeSettings,
    onSettingsChanged: (ThemeSettings) -> Unit,
) {
    var darkModeMenuExpanded by remember { mutableStateOf(false) }
    var accentMenuExpanded by remember { mutableStateOf(false) }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(I18n.t("settings_theme_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    I18n.t("settings_theme_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("主题色", fontWeight = FontWeight.SemiBold)
                    Text(
                        ThemeSettings.accentLabel(settings.accentColor),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box {
                    Row(
                        modifier = Modifier.clickable { accentMenuExpanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                .background(accentSwatchColor(settings.accentColor)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(ThemeSettings.accentLabel(settings.accentColor))
                    }
                    DropdownMenu(
                        expanded = accentMenuExpanded,
                        onDismissRequest = { accentMenuExpanded = false },
                    ) {
                        ThemeSettings.ACCENT_OPTIONS.forEach { accent ->
                            DropdownMenuItem(
                                text = { Text(ThemeSettings.accentLabel(accent)) },
                                onClick = {
                                    onSettingsChanged(
                                        settings.copy(
                                            accentColor = accent,
                                            dynamicColorEnabled = accent == ThemeSettings.ACCENT_MONET,
                                        ),
                                    )
                                    accentMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_dynamic_color"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.dynamicColorEnabled) I18n.t("settings_dynamic_color_on") else I18n.t("settings_dynamic_color_off"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(dynamicColorEnabled = enabled))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("毛玻璃", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.liquidGlassEnabled) "已开启，低配设备自动降级" else "已关闭",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.liquidGlassEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(liquidGlassEnabled = enabled))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_dark_mode"), fontWeight = FontWeight.SemiBold)
                    Text(
                        darkModeDescription(settings.darkMode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box {
                    TextButton(onClick = { darkModeMenuExpanded = true }) {
                        Text(darkModeLabel(settings.darkMode))
                    }
                    DropdownMenu(
                        expanded = darkModeMenuExpanded,
                        onDismissRequest = { darkModeMenuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        DarkModeSetting.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(darkModeLabel(mode)) },
                                onClick = {
                                    darkModeMenuExpanded = false
                                    onSettingsChanged(settings.copy(darkMode = mode))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderSettingsCard(
    settings: RenderSettings,
    onSettingsChanged: (RenderSettings) -> Unit,
) {
    val context = LocalContext.current
    var fpsLimit by remember(settings.fpsLimit) { mutableFloatStateOf(settings.fpsLimit.toFloat()) }
    var renderResolutionIndex by remember(settings.renderResolution) {
        mutableFloatStateOf(settings.renderResolution.ordinal.toFloat())
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persistBackgroundUri(context.applicationContext, uri)
        onSettingsChanged(settings.copy(backgroundUri = uri.toString()))
    }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(I18n.t("settings_live2d_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    I18n.t("settings_live2d_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(I18n.t("settings_fps_limit"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${fpsLimit.roundToInt()} FPS",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = fpsLimit,
                    onValueChange = { value ->
                        fpsLimit = ((value / 5f).roundToInt().coerceIn(3, 24) * 5).toFloat()
                    },
                    onValueChangeFinished = {
                        val fps = fpsLimit.roundToInt()
                        if (fps != settings.fpsLimit) onSettingsChanged(settings.copy(fpsLimit = fps))
                    },
                    valueRange = 15f..120f,
                    steps = 20,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(I18n.t("settings_render_resolution"), fontWeight = FontWeight.SemiBold)
                    Text(
                        renderResolutionLabel(RenderResolution.entries[renderResolutionIndex.roundToInt()]),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = renderResolutionIndex,
                    onValueChange = { value ->
                        renderResolutionIndex = value.roundToInt()
                            .coerceIn(RenderResolution.entries.indices)
                            .toFloat()
                    },
                    onValueChangeFinished = {
                        val resolution = RenderResolution.entries[renderResolutionIndex.roundToInt()]
                        if (resolution != settings.renderResolution) {
                            onSettingsChanged(settings.copy(renderResolution = resolution))
                        }
                    },
                    valueRange = 0f..RenderResolution.entries.lastIndex.toFloat(),
                    steps = RenderResolution.entries.size - 2,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_fps_display"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.fpsDisplayEnabled) {
                            I18n.t("settings_fps_display_on")
                        } else {
                            I18n.t("settings_fps_display_off")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.fpsDisplayEnabled,
                    onCheckedChange = { enabled -> onSettingsChanged(settings.copy(fpsDisplayEnabled = enabled)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_vsync"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.vsyncEnabled) I18n.t("settings_vsync_on") else I18n.t("settings_vsync_off"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.vsyncEnabled,
                    onCheckedChange = { enabled -> onSettingsChanged(settings.copy(vsyncEnabled = enabled)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_gaze"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.gazeFollowEnabled) {
                            I18n.t("settings_gaze_on")
                        } else {
                            I18n.t("settings_gaze_off")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.gazeFollowEnabled,
                    onCheckedChange = { enabled -> onSettingsChanged(settings.copy(gazeFollowEnabled = enabled)) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(I18n.t("settings_bg_label"), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.backgroundUri == null) I18n.t("settings_bg_default") else I18n.t("settings_bg_selected"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            backgroundPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Text(if (settings.backgroundUri == null) I18n.t("settings_select") else I18n.t("settings_change"))
                    }
                }
                if (settings.backgroundUri != null) {
                    TextButton(onClick = { onSettingsChanged(settings.copy(backgroundUri = null)) }) {
                        Text(I18n.t("settings_clear_bg"))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperSettingsCard(
    enabled: Boolean,
    backgroundUri: String?,
    mode: com.bangdream.pet.WallpaperMode,
    onModeChanged: (com.bangdream.pet.WallpaperMode) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onBackgroundChanged: (String?) -> Unit,
    onAdjustPosition: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persistBackgroundUri(appContext, uri)
        onBackgroundChanged(uri.toString())
    }
    val scope = rememberCoroutineScope()
    var wallpaperStatus by remember { mutableStateOf("") }
    LaunchedEffect(appContext) {
        // 读系统壁纸状态要走 WallpaperManager + 磁盘，不能在组合期同步做
        wallpaperStatus = withContext(Dispatchers.IO) { WallpaperBackup.wallpaperStatus(appContext) }
    }
    var showAllFilesAccessDialog by remember { mutableStateOf(false) }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesAccessDialog = false
    }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(I18n.t("settings_wallpaper_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    I18n.t("settings_wallpaper_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_wallpaper_enable"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) I18n.t("settings_wallpaper_enable_on") else I18n.t("settings_wallpaper_enable_off"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newEnabled ->
                        onEnabledChanged(newEnabled)
                        if (newEnabled) {
                            scope.launch {
                                if (loadWallpaperBackgroundUri(appContext).isNullOrBlank()) {
                                    val result = withContext(Dispatchers.IO) {
                                        WallpaperBackup.captureAndUseAsBackgroundResult(appContext)
                                    }
                                    when (result) {
                                        is WallpaperBackup.WallpaperCaptureResult.Success ->
                                            onBackgroundChanged(result.uri)
                                        WallpaperBackup.WallpaperCaptureResult.NeedAllFilesAccess ->
                                            showAllFilesAccessDialog = true
                                        WallpaperBackup.WallpaperCaptureResult.Failed -> Unit
                                    }
                                }
                                // 每次开启都跳转到系统壁纸选择器（选择/启用 BangDream Pet 动态壁纸）
                                openLiveWallpaperPicker(context)
                            }
                        }
                        else {
                            // 关闭：恢复系统原壁纸，清除桌面模型显示（解码+写壁纸很重，必须离开主线程）
                            scope.launch {
                                val restored = withContext(Dispatchers.IO) {
                                    WallpaperBackup.restoreSystemWallpaper(appContext)
                                }
                                if (!restored) {
                                    Toast.makeText(
                                        appContext,
                                        "未找到原壁纸备份，无法自动恢复（模型将由壁纸服务停止渲染）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("渲染模式", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) "多模型：桌面同时放置多个角色" else "单模型：沿用当前选择的一个角色",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SingleChoiceSegmentedButtonRow {
                    com.bangdream.pet.WallpaperMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { onModeChanged(option) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = com.bangdream.pet.WallpaperMode.entries.size),
                        ) { Text(option.label) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前壁纸状态", fontWeight = FontWeight.SemiBold)
                    Text(
                        wallpaperStatus,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) "管理桌面模型" else I18n.t("settings_wallpaper_adjust"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) "添加/移除桌面角色，逐个调整位置与大小" else I18n.t("settings_wallpaper_adjust_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = onAdjustPosition) {
                    Text(if (mode == com.bangdream.pet.WallpaperMode.MULTI) "管理" else I18n.t("settings_wallpaper_adjust_btn"))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(I18n.t("settings_wallpaper_bg_label"), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (backgroundUri == null) I18n.t("settings_wallpaper_bg_default") else I18n.t("settings_wallpaper_bg_selected"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            backgroundPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Text(if (backgroundUri == null) I18n.t("settings_select") else I18n.t("settings_change"))
                    }
                }
                if (backgroundUri != null) {
                    TextButton(onClick = { onBackgroundChanged(null) }) {
                        Text(I18n.t("settings_clear_bg"))
                    }
                }
            }
        }
        if (showAllFilesAccessDialog) {
            AllFilesAccessPermissionDialog(
                onDismiss = { showAllFilesAccessDialog = false },
                onGrant = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${appContext.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { allFilesAccessLauncher.launch(intent) }
                        .onFailure {
                            showAllFilesAccessDialog = false
                            Toast.makeText(
                                appContext,
                                "无法打开授权页，请到：系统设置 → 应用 → 权限 → 所有文件访问",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                },
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().appEntrance(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

private fun darkModeLabel(mode: DarkModeSetting): String = when (mode) {
    DarkModeSetting.On -> I18n.t("settings_dark_mode_on")
    DarkModeSetting.Off -> I18n.t("settings_dark_mode_off")
    DarkModeSetting.System -> I18n.t("settings_dark_mode_system")
}

private fun accentSwatchColor(accent: String): Color = when (accent) {
    ThemeSettings.ACCENT_MINT -> Color(0xFF00796B)
    ThemeSettings.ACCENT_SKY -> Color(0xFF1565C0)
    ThemeSettings.ACCENT_SUNSET -> Color(0xFFE65100)
    ThemeSettings.ACCENT_LAVENDER -> Color(0xFF6A4FB8)
    ThemeSettings.ACCENT_ROSE -> Color(0xFFC2185B)
    ThemeSettings.ACCENT_MONET -> Color(0xFF7E7E7E)
    else -> Color(0xFFB32666)
}

private fun darkModeDescription(mode: DarkModeSetting): String = when (mode) {
    DarkModeSetting.On -> I18n.t("settings_dark_mode_on_desc")
    DarkModeSetting.Off -> I18n.t("settings_dark_mode_off_desc")
    DarkModeSetting.System -> I18n.t("settings_dark_mode_system_desc")
}

private fun renderResolutionLabel(resolution: RenderResolution): String = when (resolution) {
    RenderResolution.SuperSampling -> I18n.t("settings_render_resolution_x2")
    RenderResolution.PointToPoint -> I18n.t("settings_render_resolution_point_to_point")
    RenderResolution.TwoThirds -> I18n.t("settings_render_resolution_two_thirds")
    RenderResolution.Half -> I18n.t("settings_render_resolution_half")
}

private fun openLiveWallpaperPicker(context: android.content.Context) {
    val component = ComponentName(context, Live2DWallpaperService::class.java)
    val intents = buildList {
        add(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        add(
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        add(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    for (intent in intents) {
        runCatching { context.startActivity(intent) }
            .onSuccess { return }
    }
    Toast.makeText(
        context,
        "未找到系统壁纸选择器，请到：系统设置 → 壁纸 → 动态壁纸 → 选择 BangDream Live2D",
        Toast.LENGTH_LONG,
    ).show()
}


@Composable
private fun OriginalWallpaperCard(onCaptured: (String?) -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var showAllFilesAccessDialog by remember { mutableStateOf(false) }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesAccessDialog = false
    }
    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("保留系统原壁纸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "开启桌面渲染时会自动捕获当前系统壁纸作为背景层（模型在壁纸与桌面图标之间）；这里可手动重新捕获或恢复系统壁纸。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                WallpaperBackup.captureAndUseAsBackgroundResult(appContext)
                            }
                            when (result) {
                                is WallpaperBackup.WallpaperCaptureResult.Success -> {
                                    onCaptured(result.uri)
                                    Toast.makeText(
                                        appContext,
                                        if (result.fromBackup) {
                                            "已复用旧备份作为背景（本次未能读取当前壁纸）"
                                        } else {
                                            "已捕获原壁纸并设为背景"
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                WallpaperBackup.WallpaperCaptureResult.NeedAllFilesAccess ->
                                    showAllFilesAccessDialog = true
                                WallpaperBackup.WallpaperCaptureResult.Failed -> {
                                    val info = WallpaperManager.getInstance(appContext).wallpaperInfo
                                    Toast.makeText(
                                        appContext,
                                        if (info != null) {
                                            "当前系统壁纸是动态壁纸（${info.component.flattenToShortString()}）。要恢复原静态壁纸：系统设置 → 壁纸 → 换成静态图片；或直接用「壁纸背景 → 选择照片」"
                                        } else {
                                            "系统限制无法读取壁纸，请直接用「壁纸背景 → 选择照片」"
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    Text("使用原壁纸为背景")
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                WallpaperBackup.restoreSystemWallpaper(appContext)
                            }
                            Toast.makeText(appContext, if (ok) "已恢复系统壁纸" else "没有可恢复的备份", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("恢复系统壁纸")
                }
            }
            if (showAllFilesAccessDialog) {
                AllFilesAccessPermissionDialog(
                    onDismiss = { showAllFilesAccessDialog = false },
                    onGrant = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:${appContext.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { allFilesAccessLauncher.launch(intent) }
                            .onFailure {
                                showAllFilesAccessDialog = false
                                Toast.makeText(
                                    appContext,
                                    "无法打开授权页，请到：系统设置 → 应用 → 权限 → 所有文件访问",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun AllFilesAccessPermissionDialog(onDismiss: () -> Unit, onGrant: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要「所有文件访问」权限") },
        text = {
            Text(
                "Android 13 及以上系统限制普通应用读取系统壁纸。\n\n" +
                    "请授予「所有文件访问」权限，" +
                    "授权返回后重新点击「使用原壁纸为背景」即可捕获当前壁纸。",
            )
        },
        confirmButton = {
            TextButton(onClick = onGrant) { Text("去授权") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InteractionSettingsCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var touchEnabled by remember { mutableStateOf(loadTouchAnimationEnabled(appContext)) }
    var touchChoices by remember { mutableStateOf(loadTouchAnimations(appContext)) }
    var swipeEnabled by remember { mutableStateOf(loadSwipeAnimationEnabled(appContext)) }
    var idleEnabled by remember { mutableStateOf(loadIdleAnimationEnabled(appContext)) }
    var idleChoices by remember { mutableStateOf(loadIdleAnimations(appContext)) }
    var idleInterval by remember { mutableStateOf(loadIdleIntervalMs(appContext).toFloat()) }
    var bubbleDurationSeconds by remember { mutableStateOf(loadBubbleDurationSeconds(appContext)) }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("壁纸交互与动画", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("触摸播放动画", fontWeight = FontWeight.SemiBold)
                    Text("单击/滑动抚摸时随机播放勾选动作", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = touchEnabled, onCheckedChange = { touchEnabled = it; saveTouchAnimationEnabled(appContext, it) })
            }
            AnimationMultiSelect(
                choices = touchChoices,
                enabled = touchEnabled,
                onToggle = { name ->
                    touchChoices = if (name in touchChoices) touchChoices - name else touchChoices + name
                    saveTouchAnimations(appContext, touchChoices)
                },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("滑动抚摸动画", fontWeight = FontWeight.SemiBold)
                    Text("在模型上滑动触发随机动作", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = swipeEnabled, onCheckedChange = { swipeEnabled = it; saveSwipeAnimationEnabled(appContext, it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("待机随机动画", fontWeight = FontWeight.SemiBold)
                    Text("空闲时每隔一段时间随机播放勾选动作", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = idleEnabled, onCheckedChange = { idleEnabled = it; saveIdleAnimationEnabled(appContext, it) })
            }
            AnimationMultiSelect(
                choices = idleChoices,
                enabled = idleEnabled,
                onToggle = { name ->
                    idleChoices = if (name in idleChoices) idleChoices - name else idleChoices + name
                    saveIdleAnimations(appContext, idleChoices)
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("间隔：${(idleInterval / 1000).toInt()} 秒", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = idleInterval,
                    onValueChange = { idleInterval = it },
                    onValueChangeFinished = { saveIdleIntervalMs(appContext, idleInterval.toLong()) },
                    valueRange = 3000f..60000f,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动作语音", fontWeight = FontWeight.SemiBold)
                    Text("动作触发时是否播放台词音频（台词内容由此开关产生）", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var actionVoiceEnabled by remember { mutableStateOf(loadDesktopVoiceEnabled(appContext)) }
                Switch(checked = actionVoiceEnabled, onCheckedChange = { actionVoiceEnabled = it; saveDesktopVoiceEnabled(appContext, it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("口型同步", fontWeight = FontWeight.SemiBold)
                    Text("桌面播放语音时，角色嘴部随语音开合", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var lipSyncEnabled by remember { mutableStateOf(loadDesktopLipSyncEnabled(appContext)) }
                Switch(checked = lipSyncEnabled, onCheckedChange = { lipSyncEnabled = it; saveDesktopLipSyncEnabled(appContext, it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("壁纸文字气泡", fontWeight = FontWeight.SemiBold)
                    Text("有台词/回复时以悬浮气泡显示文本（纯显示开关，不自行产生内容）", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var bubbleEnabled by remember { mutableStateOf(loadBubbleEnabled(appContext)) }
                Switch(checked = bubbleEnabled, onCheckedChange = { bubbleEnabled = it; saveBubbleEnabled(appContext, it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("气泡展示：${bubbleDurationSeconds} 秒", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = bubbleDurationSeconds.toFloat(),
                    onValueChange = { bubbleDurationSeconds = it.toInt() },
                    onValueChangeFinished = { saveBubbleDurationSeconds(appContext, bubbleDurationSeconds) },
                    valueRange = 1f..30f,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "提示：双击模型弹出输入框对话；长按桌面任意位置停止当前对话/语音。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnimationMultiSelect(
    choices: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    val labels = mapOf(
        "smile" to "微笑", "kandou" to "感动", "kime" to "决胜", "sad" to "难过",
        "cry" to "哭泣", "serious" to "认真", "thinking" to "思考", "surprised" to "惊讶",
        "angry" to "生气", "shame" to "害羞", "sing" to "唱歌", "nf" to "NF",
        "nnf" to "NNF", "bye" to "再见",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ANIMATION_CHOICES.chunked(7).forEach { chunk ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEach { name ->
                    val selected = name in choices
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { onToggle(name) },
                        enabled = enabled,
                        label = { Text(labels[name] ?: name, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }
    }
}


@Composable
private fun VoiceSettingsCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var settings by remember { mutableStateOf(VoiceSettings.load(appContext)) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var replyVoiceEnabled by remember { mutableStateOf(loadReplyVoiceEnabled(appContext)) }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("语音合成（音色克隆 TTS）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("服务商", fontWeight = FontWeight.SemiBold)
                    Text(if (settings.provider == VOICE_PROVIDER_MIMO) "mimo 预设（免费）" else "自定义 OpenAI 兼容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { providerMenuExpanded = true }) {
                        Text(if (settings.provider == VOICE_PROVIDER_MIMO) "mimo" else "自定义")
                    }
                    DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("mimo 预设（免费）") }, onClick = {
                            settings = settings.copy(
                                provider = VOICE_PROVIDER_MIMO,
                                baseUrl = "https://api.xiaomimimo.com/v1",
                                model = "mimo-v2.5-tts-voiceclone",
                            )
                            saved = false
                            providerMenuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("自定义 OpenAI 兼容") }, onClick = {
                            settings = settings.copy(provider = VOICE_PROVIDER_CUSTOM)
                            saved = false
                            providerMenuExpanded = false
                        })
                    }
                }
            }
            OutlinedTextField(
                value = settings.model,
                onValueChange = { settings = settings.copy(model = it); saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (settings.provider == VOICE_PROVIDER_MIMO) "mimo 模型名称" else "模型名称") },
                supportingText = { Text(if (settings.provider == VOICE_PROVIDER_MIMO) "默认 mimo-v2.5-tts-voiceclone，防止未来变动可自行改" else "如 deepseek-v4-tts-voiceclone / gpt-4o-audio") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { settings = settings.copy(apiKey = it); saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密钥") },
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                    }
                },
                singleLine = true,
            )
            if (settings.provider == VOICE_PROVIDER_CUSTOM) {
                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { settings = settings.copy(baseUrl = it); saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL（OpenAI 兼容）") },
                    singleLine = true,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.apiKey.isNotBlank() && settings.model.isNotBlank() && settings.baseUrl.isNotBlank(),
                onClick = {
                    settings.normalized().save(appContext)
                    saved = true
                    Toast.makeText(appContext, "语音设置已保存", Toast.LENGTH_SHORT).show()
                },
            ) { Text(if (saved) "已保存" else "保存") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("回复后播放语音", fontWeight = FontWeight.SemiBold)
                    Text("模型回复完成后用当前音色合成并播放", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = replyVoiceEnabled,
                    onCheckedChange = { enabled ->
                        replyVoiceEnabled = enabled
                        saveReplyVoiceEnabled(appContext, enabled)
                    },
                )
            }
            Text(
                "文档：docs/mimo-tts-voiceclone.txt（样本 ≤10MB，mp3/wav；返回 WAV）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VoiceSamplesCard(selectedModel: ModelChoice?) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var samples by remember { mutableStateOf(emptyList<com.bangdream.pet.voice.VoiceSampleInfo>()) }
    var refreshTick by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedModel != null) {
            val ok = VoiceSamples.importSample(appContext, selectedModel.characterId, uri)
            Toast.makeText(appContext, if (ok) "已导入并设为当前音色" else "导入失败（仅支持 mp3/wav，≤10MB）", Toast.LENGTH_SHORT).show()
            refreshTick++
        }
    }
    LaunchedEffect(selectedModel?.characterId, refreshTick) {
        if (selectedModel != null) {
            samples = withContext(Dispatchers.IO) {
                VoiceSamples.listSamples(appContext, selectedModel.characterId)
            }
        }
    }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                selectedModel?.let { "语音样本 · ${it.characterName}" } ?: "语音样本",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (selectedModel == null) {
                Text("请先在模型页选择角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (samples.isEmpty()) {
                Text("暂无样本，导入一个音频作为克隆音色", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                samples.forEach { sample ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sample.name, fontWeight = if (sample.active) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                if (sample.active) "当前音色" else "未启用",
                                color = if (sample.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!sample.active) {
                            TextButton(onClick = {
                                VoiceSamples.setActiveSample(appContext, selectedModel.characterId, sample.file.name)
                                refreshTick++
                            }) { Text("选择") }
                        }
                        TextButton(onClick = {
                            VoiceSamples.deleteSample(appContext, selectedModel.characterId, sample.file.name)
                            refreshTick++
                        }) { Text("删除") }
                    }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedModel != null,
                onClick = { launcher.launch("audio/*") },
            ) { Text("导入音色样本") }
            Text(
                "支持 mp3/wav，≤10MB；一个角色可存多个样本，选择其中一个作为当前克隆音色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuiltinVoiceCard(selectedModel: ModelChoice?) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var language by remember { mutableStateOf(loadBuiltinVoiceLanguage(appContext)) }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("内置语音", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BuiltinVoiceLanguage.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = language == option,
                        onClick = {
                            language = option
                            saveBuiltinVoiceLanguage(appContext, option)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BuiltinVoiceLanguage.entries.size),
                    ) { Text(option.label) }
                }
            }
            Text(
                selectedModel?.let {
                    "使用 ${it.characterName} 的${if (language == BuiltinVoiceLanguage.JA) "日语" else "中文"}台词（共 ${BuiltinVoiceManager.loadLines(appContext, it.characterId, language).size} 条，预生成语音随版本内置）"
                } ?: "请先选择角色",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "提示：在「桌面壁纸 → 壁纸交互与动画」开启「动作语音」后，桌面模型触发动作会按动作播放以上台词（待机说话与「待机随机动画」开关相互独立）；「壁纸文字气泡」只控制是否显示台词文本，「口型同步」控制说话时嘴部开合。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

