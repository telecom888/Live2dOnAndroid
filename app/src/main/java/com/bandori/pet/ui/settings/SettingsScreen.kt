package com.bandori.pet.ui.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
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
import com.bandori.pet.DarkModeSetting
import com.bandori.pet.FloatingLive2DItem
import com.bandori.pet.FloatingOverlaySettings
import com.bandori.pet.I18n
import com.bandori.pet.RenderResolution
import com.bandori.pet.RenderSettings
import com.bandori.pet.ui.design.VisualGuard
import com.bandori.pet.ui.design.appEntrance
import com.bandori.pet.ui.design.appHazeSource
import com.bandori.pet.ui.design.appLiquidGlass
import com.bandori.pet.ui.design.appPressScale
import com.bandori.pet.ui.design.rememberLiquidGlassState
import com.bandori.pet.ThemeSettings
import android.widget.Toast
import com.bandori.pet.ANIMATION_CHOICES
import com.bandori.pet.VOICE_PROVIDER_CUSTOM
import com.bandori.pet.VOICE_PROVIDER_MIMO
import com.bandori.pet.VoiceSettings
import com.bandori.pet.loadBubbleEnabled
import com.bandori.pet.BuiltinVoiceLanguage
import com.bandori.pet.loadBuiltinVoiceEnabled
import com.bandori.pet.loadBuiltinVoiceLanguage
import com.bandori.pet.saveBuiltinVoiceLanguage
import com.bandori.pet.saveBuiltinVoiceEnabled
import com.bandori.pet.saveBubbleEnabled
import com.bandori.pet.addFloatingLive2DItem
import com.bandori.pet.data.ModelChoice
import com.bandori.pet.floating.FloatingLive2DOverlayService
import com.bandori.pet.isWallpaperEnabled
import com.bandori.pet.llm.ChatHistoryRepository
import com.bandori.pet.llm.LlmSettings
import com.bandori.pet.llm.ThinkingMode
import com.bandori.pet.companion.CompanionSettings
import com.bandori.pet.loadIdleAnimationEnabled
import com.bandori.pet.loadMimoApiKey
import com.bandori.pet.loadIdleAnimations
import com.bandori.pet.loadIdleIntervalMs
import com.bandori.pet.loadSwipeAnimationEnabled
import com.bandori.pet.loadTouchAnimationEnabled
import com.bandori.pet.loadTouchAnimations
import com.bandori.pet.loadWallpaperBackgroundUri
import com.bandori.pet.persistBackgroundUri
import com.bandori.pet.removeFloatingLive2DItem
import com.bandori.pet.resetFloatingLive2DItemPositions
import com.bandori.pet.saveIdleAnimationEnabled
import com.bandori.pet.saveMimoApiKey
import com.bandori.pet.saveIdleAnimations
import com.bandori.pet.saveIdleIntervalMs
import com.bandori.pet.saveSwipeAnimationEnabled
import com.bandori.pet.saveTouchAnimationEnabled
import com.bandori.pet.saveTouchAnimations
import com.bandori.pet.saveWallpaperBackgroundUri
import com.bandori.pet.setWallpaperEnabled
import com.bandori.pet.voice.BuiltinVoiceManager
import com.bandori.pet.voice.VoiceSamples
import com.bandori.pet.wallpaper.Live2DWallpaperService
import com.bandori.pet.wallpaper.WallpaperBackup
import com.bandori.pet.wallpaper.WallpaperUtils
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
    var floatingOverlaySettings by remember { mutableStateOf(FloatingOverlaySettings.load(appContext)) }
    fun updateFloatingOverlaySettings(settings: FloatingOverlaySettings) {
        val latestItemsById = FloatingOverlaySettings.load(appContext).items.associateBy { it.id }
        val nextSettings = settings.copy(
            items = settings.items.map { item ->
                latestItemsById[item.id]?.let { latest ->
                    item.copy(
                        x = latest.x,
                        y = latest.y,
                        width = latest.width,
                        height = latest.height,
                    )
                } ?: item
            },
        )
        floatingOverlaySettings = nextSettings
        nextSettings.save(appContext)
        FloatingLive2DOverlayService.sync(appContext)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "theme") {
            ThemeSettingsCard(
                settings = themeSettings,
                onSettingsChanged = onThemeSettingsChanged,
            )
        }
        item(key = "render") {
            RenderSettingsCard(
                settings = renderSettings,
                onSettingsChanged = { settings ->
                    onRenderSettingsChanged(settings)
                    FloatingLive2DOverlayService.sync(appContext)
                },
            )
        }
        item(key = "llm") {
            LlmSettingsEntryCard()
        }
        item(key = "voice_settings") {
            VoiceSettingsCard()
        }
        item(key = "companion") {
            CompanionSettingsEntryCard()
        }
        // 悬浮窗模式已按需求移除：模型只通过动态壁纸显示。
        item(key = "wallpaper") {
            WallpaperSettingsCard(
                enabled = wallpaperEnabled,
                backgroundUri = wallpaperBackgroundUri,
                onEnabledChanged = { enabled ->
                    wallpaperEnabled = enabled
                    setWallpaperEnabled(appContext, enabled)
                },
                onBackgroundChanged = { uri ->
                    wallpaperBackgroundUri = uri
                    saveWallpaperBackgroundUri(appContext, uri)
                },
                onAdjustPosition = {
                    context.startActivity(Intent(context, com.bandori.pet.WallpaperAdjustActivity::class.java))
                },
            )
        }
        item(key = "builtin_voice") {
            BuiltinVoiceCard(selectedModel = selectedModel)
        }
        item(key = "voice_samples") {
            VoiceSamplesCard(selectedModel = selectedModel)
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
        item(key = "info") {
            InfoCard(
                I18n.t("settings_about"),
                I18n.t("settings_about_text"),
            )
        }
    }
}

@Composable
private fun CompanionSettingsEntryCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var desktopName by remember { mutableStateOf(CompanionSettings.load(appContext)?.name) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        desktopName = CompanionSettings.load(appContext)?.name
    }
    Card(
        onClick = { launcher.launch(Intent(context, CompanionSettingsActivity::class.java)) },
        modifier = Modifier.fillMaxWidth().appPressScale(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("桌面互联", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    desktopName?.let { "已配对：$it" } ?: "扫描桌面二维码，安全同步私聊与 TTS",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LlmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var draft by remember { mutableStateOf(LlmSettings.load(appContext)) }
    var maxTokensText by remember { mutableStateOf(draft.maxTokens.toString()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var thinkingMenuExpanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var clearAllFailed by remember { mutableStateOf(false) }
    var mimoKey by remember { mutableStateOf(loadMimoApiKey(appContext)) }
    val scope = rememberCoroutineScope()
    val hazeState = rememberLiquidGlassState()
    val glassEnabled = ThemeSettings.load(appContext).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(appContext)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DeepSeek" to Pair("https://api.deepseek.com", "deepseek-v4-flash"),
                        "OpenCode Go" to Pair("https://opencode.ai/zen/go/v1", "mimo-v2.5"),
                        "小米 mimo" to Pair("https://api.xiaomimimo.com/v1", "mimo-v2.5"),
                    ).forEach { (label, pair) ->
                        FilledTonalButton(onClick = {
                            draft = draft.copy(baseUrl = pair.first, model = pair.second)
                            saved = false
                        }) { Text(label) }
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
                OutlinedTextField(
                    value = draft.customPrompt,
                    onValueChange = { draft = draft.copy(customPrompt = it); saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_custom_prompt")) },
                    supportingText = { Text(I18n.t("settings_llm_custom_prompt_desc")) },
                    placeholder = { Text(I18n.t("settings_llm_custom_prompt_hint")) },
                    minLines = 4,
                    maxLines = 10,
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
                    Text("液态玻璃（毛玻璃）", fontWeight = FontWeight.SemiBold)
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
private fun FloatingOverlaySettingsCard(
    selectedModel: ModelChoice?,
    settings: FloatingOverlaySettings,
    onSettingsChanged: (FloatingOverlaySettings) -> Unit,
    onRefreshSettings: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var hasOverlayPermission by remember { mutableStateOf(FloatingLive2DOverlayService.canDrawOverlays(appContext)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasOverlayPermission = FloatingLive2DOverlayService.canDrawOverlays(appContext)
        onRefreshSettings()
        FloatingLive2DOverlayService.sync(appContext)
    }

    fun requestPermission() {
        permissionLauncher.launch(FloatingLive2DOverlayService.permissionIntent(context))
    }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(I18n.t("settings_floating_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    I18n.t("settings_floating_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!hasOverlayPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(I18n.t("settings_floating_permission"), fontWeight = FontWeight.SemiBold)
                        Text(
                            I18n.t("settings_floating_permission_desc"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    FilledTonalButton(onClick = ::requestPermission) { Text(I18n.t("settings_floating_auth")) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_floating_enable"), fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !hasOverlayPermission -> I18n.t("settings_floating_no_permission")
                            settings.items.isEmpty() -> I18n.t("settings_floating_no_items")
                            settings.enabled -> I18n.t("settings_floating_count", settings.items.size)
                            else -> I18n.t("settings_floating_enable_desc")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    enabled = hasOverlayPermission,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(enabled = enabled))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_floating_lock"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.locked) I18n.t("settings_floating_lock_on") else I18n.t("settings_floating_lock_off"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.locked,
                    onCheckedChange = { locked -> onSettingsChanged(settings.copy(locked = locked)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_floating_touch"), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.touchThrough) {
                            I18n.t("settings_floating_touch_on")
                        } else {
                            I18n.t("settings_floating_touch_off")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.touchThrough,
                    onCheckedChange = { touchThrough ->
                        onSettingsChanged(settings.copy(touchThrough = touchThrough))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_floating_add"), fontWeight = FontWeight.SemiBold)
                    Text(
                        selectedModel?.title ?: I18n.t("settings_floating_no_model"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    enabled = selectedModel != null,
                    onClick = {
                        selectedModel?.let { model ->
                            val next = addFloatingLive2DItem(appContext, model)
                            onSettingsChanged(next)
                        }
                    },
                ) {
                    Text(I18n.t("settings_floating_add_btn"))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(I18n.t("settings_floating_reset"), fontWeight = FontWeight.SemiBold)
                    Text(
                        I18n.t("settings_floating_reset_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(
                    enabled = settings.items.isNotEmpty(),
                    onClick = {
                        val next = resetFloatingLive2DItemPositions(appContext)
                        onSettingsChanged(next)
                    },
                ) {
                    Text(I18n.t("settings_floating_reset_btn"))
                }
            }
            if (settings.items.isEmpty()) {
                Text(
                    I18n.t("settings_floating_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.items.forEachIndexed { index, item ->
                        FloatingOverlayItemRow(
                            index = index,
                            item = item,
                            onRemove = {
                                val next = removeFloatingLive2DItem(appContext, item.id)
                                onSettingsChanged(next)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingOverlayItemRow(
    index: Int,
    item: FloatingLive2DItem,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${index + 1}. ${item.model.title}",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = I18n.t("settings_floating_pos", item.x, item.y, item.width, item.height),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRemove) { Text(I18n.t("settings_floating_remove")) }
        }
    }
}

@Composable
private fun WallpaperSettingsCard(
    enabled: Boolean,
    backgroundUri: String?,
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
    val wallpaperStatus = remember { WallpaperBackup.wallpaperStatus(context.applicationContext) }
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
                                openLiveWallpaperPicker(context)
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
                    Text(I18n.t("settings_wallpaper_adjust"), fontWeight = FontWeight.SemiBold)
                    Text(
                        I18n.t("settings_wallpaper_adjust_desc"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = onAdjustPosition) {
                    Text(I18n.t("settings_wallpaper_adjust_btn"))
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
        "未找到系统壁纸选择器，请到：系统设置 → 壁纸 → 动态壁纸 → 选择 Bandori Pet",
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
                        val ok = WallpaperBackup.restoreSystemWallpaper(appContext)
                        Toast.makeText(appContext, if (ok) "已恢复系统壁纸" else "没有可恢复的备份", Toast.LENGTH_SHORT).show()
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
                    Text("壁纸文字气泡", fontWeight = FontWeight.SemiBold)
                    Text("对话回复/台词以悬浮气泡显示", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                var bubbleEnabled by remember { mutableStateOf(loadBubbleEnabled(appContext)) }
                Switch(checked = bubbleEnabled, onCheckedChange = { bubbleEnabled = it; saveBubbleEnabled(appContext, it) })
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
    var samples by remember { mutableStateOf(emptyList<com.bandori.pet.voice.VoiceSampleInfo>()) }
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
            samples = VoiceSamples.listSamples(appContext, selectedModel.characterId)
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
    var generated by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var builtinEnabled by remember { mutableStateOf(loadBuiltinVoiceEnabled(appContext)) }
    var refreshTick by remember { mutableStateOf(0) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedModel?.characterId, language, refreshTick) {
        if (selectedModel != null) {
            generated = BuiltinVoiceManager.generatedCount(appContext, selectedModel.characterId, language)
        }
    }

    SettingsSectionCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("内置语音（台词转语音）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    "使用 ${it.characterName} 的${if (language == BuiltinVoiceLanguage.JA) "日语" else "中文"}台词（共 ${BuiltinVoiceManager.loadLines(appContext, it.characterId, language).size} 条）批量合成缓存"
                } ?: "请先选择角色",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (busy) {
                Text("生成中：$progress", color = MaterialTheme.colorScheme.primary)
            } else {
                Text("已生成 $generated 条语音（${language.label}）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    enabled = selectedModel != null && !busy && generated < 60,
                    onClick = {
                        val charId = selectedModel!!.characterId
                        busy = true
                        progress = "准备中…"
                        job = scope.launch {
                            val done = BuiltinVoiceManager.generate(appContext, charId, 30, language) { d, t ->
                                progress = "$d/$t"
                            }
                            busy = false
                            progress = ""
                            refreshTick++
                            Toast.makeText(appContext, if (done > 0) "已生成 $done 条${language.label}语音" else "生成失败：请检查语音设置/样本/网络", Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text(if (generated > 0) "再生成 30 条" else "生成前 30 条") }
                if (busy) {
                    TextButton(onClick = { job?.cancel(); busy = false }) { Text("取消") }
                }
                TextButton(
                    enabled = selectedModel != null && !busy && generated > 0,
                    onClick = {
                        BuiltinVoiceManager.clear(appContext, selectedModel!!.characterId, language)
                        refreshTick++
                        Toast.makeText(appContext, "已清空（${language.label}）", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("清空") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("待机播放内置语音", fontWeight = FontWeight.SemiBold)
                    Text("待机时随机播台词（动作+语音+气泡），需先生成", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = builtinEnabled, onCheckedChange = { builtinEnabled = it; saveBuiltinVoiceEnabled(appContext, it) })
            }
            Text(
                "提示：中文/日本語缓存相互独立，切换语言后需分别生成。生成使用「语音合成」卡片配置的服务商与「语音样本」卡片选择的音色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

