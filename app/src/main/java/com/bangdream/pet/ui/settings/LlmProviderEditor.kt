package com.bangdream.pet.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bangdream.pet.I18n
import com.bangdream.pet.llm.LlmHeader
import com.bangdream.pet.llm.LlmModel
import com.bangdream.pet.llm.LlmProtocol
import com.bangdream.pet.llm.LlmProvider
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.llm.ThinkingMode
import com.bangdream.pet.llm.catalogUrl
import com.bangdream.pet.llm.llmBaseUrl
import com.bangdream.pet.llm.vertexAiModelsBaseUrl
import com.bangdream.pet.ui.design.standardTween
import kotlin.math.roundToInt

private data class ProviderPreset(val name: String, val baseUrl: String, val protocol: LlmProtocol = LlmProtocol.OPENAI)

private val presets = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
    ProviderPreset("Anthropic", "https://api.anthropic.com", LlmProtocol.ANTHROPIC),
    ProviderPreset("Gemini", "https://generativelanguage.googleapis.com", LlmProtocol.GOOGLE),
    ProviderPreset(
        "Vertex AI",
        "https://aiplatform.googleapis.com/v1/projects/PROJECT_ID/locations/global/publishers/google/models",
        LlmProtocol.VERTEX_AI,
    ),
    ProviderPreset("Xiaomi MiMo", "https://api.xiaomimimo.com/v1"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderEditor(
    draft: LlmProvider,
    isNew: Boolean,
    topInset: Dp,
    onBack: () -> Unit,
    maxTokens: String,
    onMaxTokens: (String) -> Unit,
    onDraft: (LlmProvider) -> Unit,
    onSave: (LlmProvider) -> Unit,
) {
    val settings = draft.connection
    val tokenValue = maxTokens.toLongOrNull()?.takeIf { it > 0 }
    val validCatalogUrl = settings.protocol == LlmProtocol.VERTEX_AI ||
        draft.modelsUrl.isBlank() || draft.catalogUrl() != null
    var advanced by rememberSaveable { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var editedModelId by rememberSaveable { mutableStateOf<String?>(null) }

    fun change(settings: LlmSettings) = onDraft(draft.copy(connection = settings))

    LlmPage(I18n.t(if (isNew) "llm_provider_add" else "llm_provider_edit"), topInset, onBack) {
        if (isNew) item("presets") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    FilledTonalButton(onClick = {
                        val sameHost = llmBaseUrl(settings.baseUrl)?.host == llmBaseUrl(preset.baseUrl)?.host
                        onDraft(draft.copy(
                            name = preset.name,
                            modelsUrl = "",
                            connection = settings.copy(
                                baseUrl = preset.baseUrl,
                                protocol = preset.protocol,
                                apiKey = settings.apiKey.takeIf { sameHost }.orEmpty(),
                                headers = settings.headers.takeIf { sameHost }.orEmpty(),
                            ),
                        ))
                    }) { Text(preset.name) }
                }
            }
        }
        item("connection") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LlmField(draft.name, { onDraft(draft.copy(name = it)) }, "llm_provider_name")
                ChoiceRow("settings_llm_protocol", settings.protocol, LlmProtocol.entries, LlmProtocol::label) {
                    change(settings.copy(protocol = it))
                }
                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { change(settings.copy(baseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_base_url")) },
                    supportingText = if (settings.protocol == LlmProtocol.VERTEX_AI) ({
                        Text("Replace PROJECT_ID with your Google Cloud project ID")
                    }) else null,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { change(settings.copy(apiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(I18n.t("settings_llm_api_key")) },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                I18n.t(if (keyVisible) "llm_hide_key" else "llm_show_key"),
                            )
                        }
                    },
                    singleLine = true,
                )
            }
        }
        item("advanced") {
            Column {
                TextButton(onClick = { advanced = !advanced }) {
                    Text(I18n.t(if (advanced) "llm_hide_advanced" else "llm_show_advanced"))
                }
                AnimatedVisibility(
                    advanced,
                    enter = expandVertically(standardTween()) + fadeIn(standardTween()),
                    exit = shrinkVertically(standardTween()) + fadeOut(standardTween()),
                ) {
                    ProviderAdvanced(draft, maxTokens, tokenValue != null, validCatalogUrl, onDraft, onMaxTokens)
                }
            }
        }
        item("models") {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(I18n.t("llm_model_count", draft.models.size), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showPicker = true }) { Text(I18n.t("llm_model_add")) }
            }
        }
        if (isNew) {
            items(draft.models, key = LlmModel::id) { model ->
                ListItem(
                    modifier = Modifier.animateItem(),
                    headlineContent = { Text(model.label) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editedModelId = model.id }) { Icon(Icons.Outlined.Edit, I18n.t("llm_model_edit")) }
                            IconButton(onClick = { onDraft(draft.copy(models = draft.models - model)) }) {
                                Icon(Icons.Outlined.Delete, I18n.t("llm_model_delete"))
                            }
                        }
                    },
                )
            }
        }
        item("save") {
            Button(
                onClick = { onSave(draft.copy(connection = settings.copy(maxTokens = requireNotNull(tokenValue))).normalized()) },
                enabled = draft.name.isNotBlank() && llmBaseUrl(settings.baseUrl) != null &&
                    settings.apiKey.isNotBlank() && validCatalogUrl && tokenValue != null &&
                    (settings.protocol != LlmProtocol.VERTEX_AI || settings.vertexAiModelsBaseUrl() != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(I18n.t("settings_llm_save")) }
        }
    }

    if (showPicker) {
        ModelPicker(draft, onDismiss = { showPicker = false }) { models ->
            onDraft(draft.copy(models = draft.models + models))
        }
    }
    val edited = draft.models.firstOrNull { it.id == editedModelId }
    if (edited != null) {
        ModelEditor(draft, edited, onDismiss = { editedModelId = null }) { model ->
            onDraft(draft.copy(models = draft.models.map { if (it.id == edited.id) model else it }))
            editedModelId = null
        }
    }
}

@Composable
private fun ProviderAdvanced(
    draft: LlmProvider,
    maxTokens: String,
    validMaxTokens: Boolean,
    validCatalogUrl: Boolean,
    onDraft: (LlmProvider) -> Unit,
    onMaxTokens: (String) -> Unit,
) {
    val settings = draft.connection
    fun change(settings: LlmSettings) = onDraft(draft.copy(connection = settings))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (settings.protocol != LlmProtocol.VERTEX_AI) {
            OutlinedTextField(
                value = draft.modelsUrl,
                onValueChange = { onDraft(draft.copy(modelsUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(I18n.t("llm_models_url")) },
                supportingText = { Text(I18n.t("llm_models_url_hint")) },
                isError = !validCatalogUrl,
                singleLine = true,
            )
        }
        ChoiceRow("settings_llm_thinking", settings.thinkingMode, ThinkingMode.entries, { mode ->
            I18n.t(when (mode) {
                ThinkingMode.Auto -> "settings_llm_thinking_auto"
                ThinkingMode.Enabled -> "settings_llm_thinking_on"
                ThinkingMode.Disabled -> "settings_llm_thinking_off"
            })
        }) { change(settings.copy(thinkingMode = it)) }
        AnimatedVisibility(settings.protocol == LlmProtocol.ANTHROPIC) {
            SettingsSwitchRow(
                I18n.t("settings_llm_adaptive"), I18n.t("settings_llm_adaptive_hint"),
                settings.anthropicAdaptiveThinking, { change(settings.copy(anthropicAdaptiveThinking = it)) }, divider = false,
            )
        }
        AnimatedVisibility(settings.protocol.usesGeminiSchema) {
            Text(I18n.t("settings_llm_google_thinking_hint"), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(I18n.t("settings_llm_temperature"))
            Text(String.format("%.1f", settings.temperature), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = settings.temperature,
            onValueChange = { change(settings.copy(temperature = (it * 10).roundToInt() / 10f)) },
            valueRange = 0f..2f,
            steps = 19,
        )
        OutlinedTextField(
            value = maxTokens,
            onValueChange = { onMaxTokens(it.filter { char -> char in '0'..'9' }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(I18n.t("settings_llm_max_tokens")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = if (validMaxTokens) null else ({ Text(I18n.t("llm_max_tokens_invalid")) }),
            isError = !validMaxTokens,
            singleLine = true,
        )
        ChoiceRow("settings_llm_context_window", settings.contextTokens, contextTokenOptions, ::contextLabel) {
            change(settings.copy(contextTokens = it))
        }
        Text(I18n.t("settings_llm_context_window_desc"), style = MaterialTheme.typography.bodySmall)
        LlmHeadersEditor(settings.headers) { change(settings.copy(headers = it)) }
    }
}

private val contextTokenOptions = listOf(32_768, 65_536, 131_072, 262_144, 524_288, 1_048_576, 2_097_152)
private fun contextLabel(tokens: Int): String =
    if (tokens % 1_048_576 == 0) "${tokens / 1_048_576}M" else "${tokens / 1024}K"

@Composable
private fun <T> ChoiceRow(label: String, current: T, values: List<T>, labelFor: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(I18n.t(label), modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(labelFor(current)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                values.forEach { value ->
                    DropdownMenuItem(text = { Text(labelFor(value)) }, onClick = { onSelect(value); open = false })
                }
            }
        }
    }
}

@Composable
internal fun LlmField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        label = { Text(I18n.t(label)) }, singleLine = true,
    )
}

@Composable
private fun LlmHeadersEditor(headers: List<LlmHeader>, onChange: (List<LlmHeader>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(I18n.t("settings_headers_title"), style = MaterialTheme.typography.titleMedium)
        Text(I18n.t("settings_headers_desc"), style = MaterialTheme.typography.bodySmall)
        headers.forEachIndexed { index, header ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LlmField(header.name, { name ->
                    onChange(headers.mapIndexed { i, item -> if (i == index) item.copy(name = name) else item })
                }, "settings_headers_name", Modifier.weight(1f))
                LlmField(header.value, { value ->
                    onChange(headers.mapIndexed { i, item -> if (i == index) item.copy(value = value) else item })
                }, "settings_headers_value", Modifier.weight(1f))
                IconButton(onClick = { onChange(headers.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Outlined.Delete, I18n.t("settings_action_delete"))
                }
            }
        }
        TextButton(onClick = { onChange(headers + LlmHeader()) }) { Text(I18n.t("settings_headers_add")) }
    }
}
