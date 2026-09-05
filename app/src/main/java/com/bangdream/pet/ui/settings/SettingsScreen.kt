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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyListScope
import androidx.activity.compose.BackHandler
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
import com.bangdream.pet.llm.OPENCODE_SESSION_HEADER
import com.bangdream.pet.llm.isOpencodeSessionHeader
import com.bangdream.pet.llm.LlmHeader
import com.bangdream.pet.llm.LlmSettings
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
    onDetailActiveChange: (Boolean) -> Unit = {},
) {
    var route by remember { mutableStateOf<SettingsDetail?>(null) }
    LaunchedEffect(route) { onDetailActiveChange(route != null) }
    BackHandler(enabled = route != null) { route = null }
    when (route) {
        SettingsDetail.WALLPAPER -> DesktopWallpaperPage(topInset = topInset, onBack = { route = null })
        SettingsDetail.INTERACTION -> DesktopInteractionPage(topInset = topInset, onBack = { route = null })
        SettingsDetail.RENDER -> RenderPerformancePage(
            settings = renderSettings,
            onSettingsChanged = onRenderSettingsChanged,
            topInset = topInset,
            onBack = { route = null },
        )
        SettingsDetail.VOICE -> VoicePage(selectedModel = selectedModel, topInset = topInset, onBack = { route = null })
        null -> SettingsHomeList(
            themeSettings = themeSettings,
            onThemeSettingsChanged = onThemeSettingsChanged,
            onOpenDetail = { route = it },
            topInset = topInset,
        )
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
            Toast.makeText(appContext, if (ok) I18n.t("settings_avatar_set_done") else I18n.t("settings_avatar_import_failed"), Toast.LENGTH_SHORT).show()
            userAvatarTick++
        }
    }
    SettingsSectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp)) {
                Text(I18n.t("settings_chat_ui_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            SettingsSwitchRow(
                title = I18n.t("settings_line_ui"),
                subtitle = I18n.t("settings_line_ui_desc"),
                checked = lineUiEnabled,
                onCheckedChange = {
                    lineUiEnabled = it
                    saveLineUiEnabled(appContext, it)
                },
            )
            SettingsSwitchRow(
                title = I18n.t("settings_line_nav"),
                subtitle = I18n.t("settings_line_nav_desc"),
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatarPreview(userAvatarTick)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(I18n.t("settings_avatar_title"), fontWeight = FontWeight.SemiBold)
                    Text(
                        I18n.t("settings_avatar_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (remember(userAvatarTick) { AvatarManager.userAvatarFile(appContext) } != null) {
                    TextButton(onClick = {
                        AvatarManager.clearUserAvatar(appContext)
                        userAvatarTick++
                        Toast.makeText(appContext, I18n.t("settings_avatar_cleared"), Toast.LENGTH_SHORT).show()
                    }) { Text(I18n.t("settings_action_clear")) }
                }
                FilledTonalButton(onClick = { userAvatarLauncher.launch("image/*") }) { Text(I18n.t("settings_action_set")) }
            }
        }
    }

    if (showLineNavWarning) {
        AlertDialog(
            onDismissRequest = { showLineNavWarning = false },
            title = { Text(I18n.t("settings_experimental_title")) },
            text = {
                Text(I18n.t("settings_experimental_line_body"))
            },
            confirmButton = {
                TextButton(onClick = {
                    lineNavEnabled = true
                    saveLineNavEnabled(appContext, true)
                    showLineNavWarning = false
                }) { Text(I18n.t("settings_action_enable")) }
            },
            dismissButton = {
                TextButton(onClick = { showLineNavWarning = false }) { Text(I18n.t("cancel")) }
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

/** LLM 自定义请求头编辑。
 * - OpenCode Go 预设会自动补一行 x-opencode-session（值留空 = 按每条对话自动生成稳定会话 ID）；
 * - 该行值可填固定值覆盖，也可删除（删除后不再发送该请求头）；
 * - 其余自定义请求头（任意 provider）手动增删，名称/值均可编辑。
 */
@Composable
private fun LlmHeadersEditor(
    headers: List<LlmHeader>,
    onHeadersChange: (List<LlmHeader>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(I18n.t("settings_headers_title"), fontWeight = FontWeight.SemiBold)
        Text(
            I18n.t("settings_headers_desc"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (headers.isEmpty()) {
            Text(I18n.t("settings_not_set"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        headers.forEachIndexed { index, header ->
            // x-opencode-session 且值为空：应用按对话自动生成稳定 ID，值框留空并显示占位说明（可填固定值覆盖）
            val isAutoSession = isOpencodeSessionHeader(header.name) && header.value.isBlank()
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
                    label = { Text(I18n.t("settings_headers_name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = header.value,
                    onValueChange = { value ->
                        onHeadersChange(headers.mapIndexed { i, h -> if (i == index) h.copy(value = value) else h })
                    },
                    modifier = Modifier.weight(1f),
                    label = if (isAutoSession) null else ({ Text(I18n.t("settings_headers_value")) }),
                    placeholder = if (isAutoSession) {
                        ({ Text(I18n.t("settings_headers_auto_hint")) })
                    } else null,
                    singleLine = true,
                )
                IconButton(onClick = {
                    onHeadersChange(headers.filterIndexed { i, _ -> i != index })
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = I18n.t("settings_action_delete"), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        FilledTonalButton(onClick = { onHeadersChange(headers + LlmHeader()) }) {
            Text(I18n.t("settings_headers_add"))
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
                                    // OpenCode Go：自动补一行 x-opencode-session（值留空 = 按对话自动生成稳定 ID），已存在则不重复添加
                                    val hasSessionHeader = next.headers.any { isOpencodeSessionHeader(it.name) }
                                    if (!hasSessionHeader) {
                                        next = next.copy(headers = next.headers + LlmHeader(name = OPENCODE_SESSION_HEADER))
                                    }
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
                        Text(I18n.t("settings_llm_context_window"), fontWeight = FontWeight.SemiBold)
                        Text(
                            I18n.t("settings_llm_context_window_desc"),
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
                        Text(I18n.t("settings_llm_image_input"), fontWeight = FontWeight.SemiBold)
                        Text(
                            I18n.t("settings_llm_image_input_desc"),
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
                    label = { Text(I18n.t("settings_voice_mimo_key")) },
                    supportingText = { Text(I18n.t("settings_voice_mimo_key_desc")) },
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
    SettingsSectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp)) {
                Text(I18n.t("settings_theme_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    I18n.t("settings_theme_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SettingsDropdownRow(
                title = I18n.t("settings_theme_accent"),
                subtitle = null,
                selected = settings.accentColor,
                options = ThemeSettings.ACCENT_OPTIONS.map { accent -> accent to ThemeSettings.accentLabel(accent) },
                onSelected = { accent ->
                    onSettingsChanged(
                        settings.copy(
                            accentColor = accent,
                            dynamicColorEnabled = accent == ThemeSettings.ACCENT_MONET,
                        ),
                    )
                },
            )
            SettingsSwitchRow(
                title = I18n.t("settings_dynamic_color"),
                subtitle = if (settings.dynamicColorEnabled) I18n.t("settings_dynamic_color_on") else I18n.t("settings_dynamic_color_off"),
                checked = settings.dynamicColorEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChanged(settings.copy(dynamicColorEnabled = enabled))
                },
            )
            SettingsDropdownRow(
                title = I18n.t("settings_dark_mode"),
                subtitle = darkModeDescription(settings.darkMode),
                selected = settings.darkMode,
                options = DarkModeSetting.entries.map { mode -> mode to darkModeLabel(mode) },
                onSelected = { mode -> onSettingsChanged(settings.copy(darkMode = mode)) },
            )
            SettingsSwitchRow(
                title = I18n.t("settings_liquid_glass"),
                subtitle = if (settings.liquidGlassEnabled) I18n.t("settings_liquid_glass_on") else I18n.t("settings_liquid_glass_off"),
                checked = settings.liquidGlassEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChanged(settings.copy(liquidGlassEnabled = enabled))
                },
                divider = false,
            )
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
    showMaster: Boolean = true,
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
            if (showMaster) {
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
                                        I18n.t("settings_wallpaper_restore_failed"),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
            }            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_wallpaper_mode"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) I18n.t("settings_wallpaper_mode_multi_desc") else I18n.t("settings_wallpaper_mode_single_desc"),
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
                        ) { Text(wallpaperModeLabel(option)) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_wallpaper_status"), fontWeight = FontWeight.SemiBold)
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
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) I18n.t("settings_wallpaper_manage_title") else I18n.t("settings_wallpaper_adjust"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (mode == com.bangdream.pet.WallpaperMode.MULTI) I18n.t("settings_wallpaper_manage_desc") else I18n.t("settings_wallpaper_adjust_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = onAdjustPosition) {
                    Text(if (mode == com.bangdream.pet.WallpaperMode.MULTI) I18n.t("settings_wallpaper_manage_btn") else I18n.t("settings_wallpaper_adjust_btn"))
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
                                I18n.t("settings_allfiles_goto_failed"),
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


private fun wallpaperModeLabel(mode: com.bangdream.pet.WallpaperMode): String = when (mode) {
    com.bangdream.pet.WallpaperMode.SINGLE -> I18n.t("settings_wallpaper_mode_single")
    com.bangdream.pet.WallpaperMode.MULTI -> I18n.t("settings_wallpaper_mode_multi")
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
        I18n.t("settings_wallpaper_picker_not_found"),
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
            Text(I18n.t("settings_original_wallpaper_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                I18n.t("settings_original_wallpaper_desc"),
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
                                            I18n.t("settings_original_wallpaper_reused_backup")
                                        } else {
                                            I18n.t("settings_original_wallpaper_captured")
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
                                            I18n.t("settings_wallpaper_live_detected", info.component.flattenToShortString())
                                        } else {
                                            I18n.t("settings_original_wallpaper_unreadable")
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    Text(I18n.t("settings_original_wallpaper_capture_btn"))
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                WallpaperBackup.restoreSystemWallpaper(appContext)
                            }
                            Toast.makeText(appContext, if (ok) I18n.t("settings_original_wallpaper_restored") else I18n.t("settings_original_wallpaper_no_backup"), Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(I18n.t("settings_original_wallpaper_restore_btn"))
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
                                    I18n.t("settings_allfiles_goto_failed"),
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
        title = { Text(I18n.t("settings_allfiles_title")) },
        text = {
            Text(
                I18n.t("settings_allfiles_dialog_text"),
            )
        },
        confirmButton = {
            TextButton(onClick = onGrant) { Text(I18n.t("settings_allfiles_grant_btn")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(I18n.t("cancel")) }
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
            Text(I18n.t("settings_interaction_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_touch_animation"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_touch_animation_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                    Text(I18n.t("settings_swipe_animation"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_swipe_animation_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = swipeEnabled, onCheckedChange = { swipeEnabled = it; saveSwipeAnimationEnabled(appContext, it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_idle_animation"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_idle_animation_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                Text(I18n.t("settings_idle_interval_sec", (idleInterval / 1000).toInt()), modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
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
                    Text(I18n.t("settings_action_voice"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_action_voice_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var actionVoiceEnabled by remember { mutableStateOf(loadDesktopVoiceEnabled(appContext)) }
                Switch(checked = actionVoiceEnabled, onCheckedChange = { actionVoiceEnabled = it; saveDesktopVoiceEnabled(appContext, it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_lip_sync"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_lip_sync_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var lipSyncEnabled by remember { mutableStateOf(loadDesktopLipSyncEnabled(appContext)) }
                Switch(checked = lipSyncEnabled, onCheckedChange = { lipSyncEnabled = it; saveDesktopLipSyncEnabled(appContext, it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_bubble"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_bubble_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var bubbleEnabled by remember { mutableStateOf(loadBubbleEnabled(appContext)) }
                Switch(checked = bubbleEnabled, onCheckedChange = { bubbleEnabled = it; saveBubbleEnabled(appContext, it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(I18n.t("settings_bubble_duration_sec", bubbleDurationSeconds), modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = bubbleDurationSeconds.toFloat(),
                    onValueChange = { bubbleDurationSeconds = it.toInt() },
                    onValueChangeFinished = { saveBubbleDurationSeconds(appContext, bubbleDurationSeconds) },
                    valueRange = 1f..30f,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                I18n.t("settings_interaction_hint"),
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
        "smile" to I18n.t("settings_anim_smile"), "kandou" to I18n.t("settings_anim_kandou"), "kime" to I18n.t("settings_anim_kime"), "sad" to I18n.t("settings_anim_sad"),
        "cry" to I18n.t("settings_anim_cry"), "serious" to I18n.t("settings_anim_serious"), "thinking" to I18n.t("settings_anim_thinking"), "surprised" to I18n.t("settings_anim_surprised"),
        "angry" to I18n.t("settings_anim_angry"), "shame" to I18n.t("settings_anim_shame"), "sing" to I18n.t("settings_anim_sing"), "nf" to "NF",
        "nnf" to "NNF", "bye" to I18n.t("settings_anim_bye"),
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
            Text(I18n.t("settings_voice_tts_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_voice_provider"), fontWeight = FontWeight.SemiBold)
                    Text(if (settings.provider == VOICE_PROVIDER_MIMO) I18n.t("settings_voice_provider_mimo_preset") else I18n.t("settings_voice_provider_custom"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { providerMenuExpanded = true }) {
                        Text(if (settings.provider == VOICE_PROVIDER_MIMO) "mimo" else I18n.t("settings_voice_custom_label"))
                    }
                    DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(I18n.t("settings_voice_provider_mimo_preset")) }, onClick = {
                            settings = settings.copy(
                                provider = VOICE_PROVIDER_MIMO,
                                baseUrl = "https://api.xiaomimimo.com/v1",
                                model = "mimo-v2.5-tts-voiceclone",
                            )
                            saved = false
                            providerMenuExpanded = false
                        })
                        DropdownMenuItem(text = { Text(I18n.t("settings_voice_provider_custom")) }, onClick = {
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
                label = { Text(if (settings.provider == VOICE_PROVIDER_MIMO) I18n.t("settings_voice_mimo_model") else I18n.t("settings_voice_model")) },
                supportingText = { Text(if (settings.provider == VOICE_PROVIDER_MIMO) I18n.t("settings_voice_mimo_model_hint") else I18n.t("settings_voice_model_hint")) },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { settings = settings.copy(apiKey = it); saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(I18n.t("settings_voice_api_key")) },
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
                    label = { Text(I18n.t("settings_voice_base_url")) },
                    singleLine = true,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.apiKey.isNotBlank() && settings.model.isNotBlank() && settings.baseUrl.isNotBlank(),
                onClick = {
                    settings.normalized().save(appContext)
                    saved = true
                    Toast.makeText(appContext, I18n.t("settings_voice_saved"), Toast.LENGTH_SHORT).show()
                },
            ) { Text(if (saved) I18n.t("settings_saved") else I18n.t("settings_action_save")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("settings_reply_voice"), fontWeight = FontWeight.SemiBold)
                    Text(I18n.t("settings_reply_voice_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                I18n.t("settings_voice_docs_hint"),
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
            Toast.makeText(appContext, if (ok) I18n.t("settings_voice_sample_imported") else I18n.t("settings_voice_sample_import_failed"), Toast.LENGTH_SHORT).show()
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
                selectedModel?.let { I18n.t("settings_voice_samples_title_char", it.characterName) } ?: I18n.t("settings_voice_samples_title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (selectedModel == null) {
                Text(I18n.t("settings_voice_select_model_first"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (samples.isEmpty()) {
                Text(I18n.t("settings_voice_samples_empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                samples.forEach { sample ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sample.name, fontWeight = if (sample.active) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                if (sample.active) I18n.t("settings_voice_current_voice") else I18n.t("settings_voice_sample_inactive"),
                                color = if (sample.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!sample.active) {
                            TextButton(onClick = {
                                VoiceSamples.setActiveSample(appContext, selectedModel.characterId, sample.file.name)
                                refreshTick++
                            }) { Text(I18n.t("settings_select")) }
                        }
                        TextButton(onClick = {
                            VoiceSamples.deleteSample(appContext, selectedModel.characterId, sample.file.name)
                            refreshTick++
                        }) { Text(I18n.t("settings_action_delete")) }
                    }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedModel != null,
                onClick = { launcher.launch("audio/*") },
            ) { Text(I18n.t("settings_voice_import_sample")) }
            Text(
                I18n.t("settings_voice_samples_hint"),
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
            Text(I18n.t("settings_builtin_voice_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    I18n.t(
                        "settings_builtin_voice_summary",
                        it.characterName,
                        I18n.t(if (language == BuiltinVoiceLanguage.JA) "settings_lang_ja" else "settings_lang_zh"),
                        BuiltinVoiceManager.loadLines(appContext, it.characterId, language).size,
                    )
                } ?: I18n.t("settings_select_character"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                I18n.t("settings_builtin_voice_hint"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


// ==================== 设置页重排：路由 / 行组件 / 详情子页 ====================

private enum class SettingsDetail { WALLPAPER, INTERACTION, RENDER, VOICE }

@Composable
private fun SettingsHomeList(
    themeSettings: ThemeSettings,
    onThemeSettingsChanged: (ThemeSettings) -> Unit,
    onOpenDetail: (SettingsDetail) -> Unit,
    topInset: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "section_appearance") { SettingsSectionHeader(I18n.t("settings_section_appearance")) }
        item(key = "theme") { ThemeSettingsCard(settings = themeSettings, onSettingsChanged = onThemeSettingsChanged) }
        item(key = "section_chat_ai") { SettingsSectionHeader(I18n.t("settings_section_chat_ai")) }
        item(key = "llm") { LlmSettingsEntryCard() }
        item(key = "line_ui") { LineUiSettingsCard() }
        item(key = "section_desktop") { SettingsSectionHeader(I18n.t("settings_section_desktop")) }
        item(key = "desktop") {
            DesktopHomeCard(
                onOpenWallpaper = { onOpenDetail(SettingsDetail.WALLPAPER) },
                onOpenInteraction = { onOpenDetail(SettingsDetail.INTERACTION) },
                onOpenRender = { onOpenDetail(SettingsDetail.RENDER) },
            )
        }
        item(key = "section_voice") { SettingsSectionHeader(I18n.t("settings_section_voice")) }
        item(key = "voice") { VoiceHomeCard(onOpenVoice = { onOpenDetail(SettingsDetail.VOICE) }) }
        item(key = "info") { InfoCard(I18n.t("settings_about"), I18n.t("settings_about_text")) }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun SettingsRowBase(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (divider) SettingsDivider()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    divider: Boolean = true,
) {
    SettingsRowBase(
        title = title,
        subtitle = subtitle,
        onClick = null,
        divider = divider,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    divider: Boolean = true,
) {
    SettingsRowBase(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        divider = divider,
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun <T> SettingsDropdownRow(
    title: String,
    subtitle: String? = null,
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    divider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (selectedLabel != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(selectedLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (divider) SettingsDivider()
    }
}

@Composable
private fun DesktopHomeCard(
    onOpenWallpaper: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenRender: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var wallpaperEnabled by remember { mutableStateOf(isWallpaperEnabled(appContext)) }
    var showAllFilesAccessDialog by remember { mutableStateOf(false) }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesAccessDialog = false
    }
    SettingsSectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            SettingsSwitchRow(
                title = I18n.t("settings_wallpaper_enable"),
                subtitle = if (wallpaperEnabled) I18n.t("settings_wallpaper_enable_on") else I18n.t("settings_wallpaper_enable_off"),
                checked = wallpaperEnabled,
                onCheckedChange = { newEnabled ->
                    wallpaperEnabled = newEnabled
                    setWallpaperEnabled(appContext, newEnabled)
                    if (newEnabled) {
                        scope.launch {
                            if (loadWallpaperBackgroundUri(appContext).isNullOrBlank()) {
                                when (val result = withContext(Dispatchers.IO) {
                                    WallpaperBackup.captureAndUseAsBackgroundResult(appContext)
                                }) {
                                    is WallpaperBackup.WallpaperCaptureResult.Success ->
                                        saveWallpaperBackgroundUri(appContext, result.uri)
                                    WallpaperBackup.WallpaperCaptureResult.NeedAllFilesAccess ->
                                        showAllFilesAccessDialog = true
                                    WallpaperBackup.WallpaperCaptureResult.Failed -> Unit
                                }
                            }
                            openLiveWallpaperPicker(context)
                        }
                    } else {
                        scope.launch {
                            val restored = withContext(Dispatchers.IO) {
                                WallpaperBackup.restoreSystemWallpaper(appContext)
                            }
                            if (!restored) {
                                Toast.makeText(
                                    appContext,
                                    I18n.t("settings_wallpaper_restore_failed"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )
            SettingsNavRow(
                title = I18n.t("settings_desktop_wallpaper_entry"),
                subtitle = I18n.t("settings_desktop_wallpaper_entry_desc"),
                onClick = onOpenWallpaper,
            )
            SettingsNavRow(
                title = I18n.t("settings_desktop_interaction_entry"),
                subtitle = I18n.t("settings_desktop_interaction_entry_desc"),
                onClick = onOpenInteraction,
            )
            SettingsNavRow(
                title = I18n.t("settings_desktop_render_entry"),
                subtitle = I18n.t("settings_desktop_render_entry_desc"),
                onClick = onOpenRender,
                divider = false,
            )
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
                            I18n.t("settings_allfiles_goto_failed"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            },
        )
    }
}

@Composable
private fun VoiceHomeCard(onOpenVoice: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val voice = remember { VoiceSettings.load(appContext) }
    SettingsSectionCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            SettingsNavRow(
                title = I18n.t("settings_voice_entry"),
                subtitle = if (voice.provider == VOICE_PROVIDER_MIMO) {
                    I18n.t("settings_voice_provider_mimo_preset")
                } else {
                    I18n.t("settings_voice_provider_custom")
                },
                onClick = onOpenVoice,
                divider = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailPage(
    title: String,
    topInset: Dp,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val hazeState = rememberLiquidGlassState()
    val glassEnabled = remember(appContext) {
        ThemeSettings.load(appContext).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(appContext)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().appHazeSource(hazeState),
            contentPadding = PaddingValues(start = 16.dp, top = topInset + 72.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        CenterAlignedTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter).appLiquidGlass(hazeState, enabled = glassEnabled),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = I18n.t("back"))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun DesktopWallpaperPage(topInset: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var wallpaperEnabled by remember { mutableStateOf(isWallpaperEnabled(appContext)) }
    var wallpaperBackgroundUri by remember { mutableStateOf(loadWallpaperBackgroundUri(appContext)) }
    var wallpaperMode by remember { mutableStateOf(loadWallpaperMode(appContext)) }
    SettingsDetailPage(
        title = I18n.t("settings_desktop_wallpaper_entry"),
        subtitle = I18n.t("settings_desktop_wallpaper_entry_desc"),
        topInset = topInset,
        onBack = onBack,
    ) {
        item(key = "wallpaper") {
            WallpaperSettingsCard(
                enabled = wallpaperEnabled,
                backgroundUri = wallpaperBackgroundUri,
                mode = wallpaperMode,
                onModeChanged = { mode ->
                    wallpaperMode = mode
                    saveWallpaperMode(appContext, mode)
                },
                onEnabledChanged = { wallpaperEnabled = it },
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
                showMaster = false,
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
    }
}

@Composable
private fun DesktopInteractionPage(topInset: Dp, onBack: () -> Unit) {
    SettingsDetailPage(
        title = I18n.t("settings_desktop_interaction_entry"),
        subtitle = I18n.t("settings_desktop_interaction_entry_desc"),
        topInset = topInset,
        onBack = onBack,
    ) {
        item(key = "interaction") { InteractionSettingsCard() }
    }
}

@Composable
private fun RenderPerformancePage(
    settings: RenderSettings,
    onSettingsChanged: (RenderSettings) -> Unit,
    topInset: Dp,
    onBack: () -> Unit,
) {
    SettingsDetailPage(
        title = I18n.t("settings_desktop_render_entry"),
        subtitle = I18n.t("settings_live2d_desc"),
        topInset = topInset,
        onBack = onBack,
    ) {
        item(key = "render") {
            RenderSettingsCard(settings = settings, onSettingsChanged = onSettingsChanged)
        }
    }
}

@Composable
private fun VoicePage(selectedModel: ModelChoice?, topInset: Dp, onBack: () -> Unit) {
    SettingsDetailPage(
        title = I18n.t("settings_voice_entry"),
        subtitle = I18n.t("settings_voice_entry_desc"),
        topInset = topInset,
        onBack = onBack,
    ) {
        item(key = "voice_settings") { VoiceSettingsCard() }
        item(key = "voice_samples") { VoiceSamplesCard(selectedModel = selectedModel) }
        item(key = "builtin_voice") { BuiltinVoiceCard(selectedModel = selectedModel) }
    }
}
