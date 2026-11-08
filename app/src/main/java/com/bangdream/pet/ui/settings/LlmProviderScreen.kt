package com.bangdream.pet.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangdream.pet.I18n
import com.bangdream.pet.ThemeSettings
import com.bangdream.pet.llm.ChatHistoryRepository
import com.bangdream.pet.llm.LlmModel
import com.bangdream.pet.llm.LlmModelRef
import com.bangdream.pet.llm.LlmProvider
import com.bangdream.pet.llm.LlmProviderSettings
import com.bangdream.pet.llm.LlmProviderStore
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.llm.modelId
import com.bangdream.pet.ui.design.VisualGuard
import com.bangdream.pet.ui.design.appHazeSource
import com.bangdream.pet.ui.design.appLiquidGlass
import com.bangdream.pet.ui.design.emphasizedTween
import com.bangdream.pet.ui.design.expressiveTween
import com.bangdream.pet.ui.design.rememberLiquidGlassState
import com.bangdream.pet.ui.design.standardTween
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberLlmProviders(): LlmProviderSettings {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LlmProviderStore.get(context) }
    return store.state.collectAsStateWithLifecycle().value
}

@Composable
internal fun rememberActiveLlmSettings(): LlmSettings {
    val providers = rememberLlmProviders()
    return remember(providers) { providers.activeSettings() }
}

internal class LlmProviderEditorState : ViewModel() {
    var draft by mutableStateOf<LlmProvider?>(null)
    var maxTokens by mutableStateOf("")
    private var original: LlmProvider? = null

    val dirty: Boolean get() = draft != original || maxTokens != original?.connection?.maxTokens?.toString().orEmpty()

    fun open(provider: LlmProvider) {
        original = provider
        draft = provider
        maxTokens = provider.connection.maxTokens.toString()
    }
}

@Composable
internal fun LlmSettingsScreen(onBack: () -> Unit, topInsetOverride: Dp? = null) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LlmProviderStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    val editor: LlmProviderEditorState = viewModel()
    val stateHolder = rememberSaveableStateHolder()
    val topInset = topInsetOverride ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var page by rememberSaveable { mutableStateOf("list") }
    var discard by rememberSaveable { mutableStateOf(false) }
    val providerId = page.substringAfter('/', "")
    fun backFromEditor() {
        page = if (settings.provider(providerId) == null) "list" else "detail/$providerId"
    }
    fun back() {
        when {
            page.startsWith("edit/") -> if (editor.dirty) discard = true else backFromEditor()
            page.startsWith("detail/") -> page = "list"
            else -> onBack()
        }
    }

    BackHandler(enabled = page != "list") { back() }
    LaunchedEffect(page) {
        if (page.startsWith("edit/") && editor.draft?.id != providerId) {
            editor.open(settings.provider(providerId) ?: LlmProvider(providerId))
        }
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (pageDepth(targetState) > pageDepth(initialState)) {
                (slideInHorizontally(emphasizedTween()) { it } + fadeIn(emphasizedTween()))
                    .togetherWith(slideOutHorizontally(expressiveTween()) { -it / 4 } + fadeOut(expressiveTween()))
            } else {
                (slideInHorizontally(emphasizedTween()) { -it / 4 } + fadeIn(emphasizedTween()))
                    .togetherWith(slideOutHorizontally(expressiveTween()) { it } + fadeOut(expressiveTween()))
            }
        },
        label = "llmProviderPage",
    ) { target ->
        stateHolder.SaveableStateProvider(target) {
            val id = target.substringAfter('/', "")
            when {
                target == "list" -> ProviderList(
                    settings, topInset, ::back,
                    onAdd = {
                        if (page == target) {
                            val provider = LlmProvider(UUID.randomUUID().toString())
                            editor.open(provider)
                            page = "edit/${provider.id}"
                        }
                    },
                    onOpen = { if (page == target) page = "detail/${it.id}" },
                    store = store,
                )
                target.startsWith("detail/") -> {
                    val provider = settings.provider(id)
                    // Keep the outgoing page available while its removal animation finishes.
                    var displayed by remember(id) { mutableStateOf(provider) }
                    LaunchedEffect(provider) { if (provider != null) displayed = provider }
                    val visibleProvider = provider ?: displayed
                    if (visibleProvider != null) {
                        ProviderDetail(
                            visibleProvider, settings, topInset, ::back,
                            onEdit = {
                                if (page == target && provider != null) {
                                    editor.open(provider)
                                    page = "edit/$id"
                                }
                            },
                            store = store,
                            onDeleted = { if (page == target) page = "list" },
                        )
                    }
                }
                else -> {
                    val draft = editor.draft?.takeIf { it.id == id }
                    if (draft != null) {
                        val isNew = settings.provider(id) == null
                        ProviderEditor(
                            draft = draft,
                            isNew = isNew,
                            topInset = topInset,
                            onBack = ::back,
                            maxTokens = editor.maxTokens,
                            onMaxTokens = { editor.maxTokens = it },
                            onDraft = { editor.draft = it },
                            onSave = { provider ->
                                if (page == target) {
                                    store.update { current ->
                                        val active = current.defaultModel?.let {
                                            if (it.providerId == provider.id) it.copy(modelId = modelId(provider.connection.protocol, it.modelId))
                                            else it
                                        }
                                        current.replace(provider, active)
                                    }
                                    page = "detail/$id"
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(I18n.t("llm_discard_title")) },
            text = { Text(I18n.t("llm_discard_message")) },
            confirmButton = {
                TextButton(onClick = {
                    discard = false
                    backFromEditor()
                }) { Text(I18n.t("llm_discard")) }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text(I18n.t("cancel")) } },
        )
    }
}

private fun pageDepth(page: String): Int = when {
    page == "list" -> 0
    page.startsWith("detail/") -> 1
    else -> 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LlmPage(
    title: String,
    topInset: Dp,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val haze = rememberLiquidGlassState()
    val glassEnabled = remember(context) {
        ThemeSettings.load(context).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(context)
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().appHazeSource(haze),
            contentPadding = PaddingValues(start = 16.dp, top = topInset + 72.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        CenterAlignedTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter).appLiquidGlass(haze, enabled = glassEnabled),
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, I18n.t("back")) } },
            actions = { actions() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ProviderList(
    settings: LlmProviderSettings,
    topInset: Dp,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (LlmProvider) -> Unit,
    store: LlmProviderStore,
) {
    val active = settings.defaultModel
    val activeModel = settings.model(active)
    val activeProvider = active?.let { settings.provider(it.providerId) }
    fun updateActiveConnection(change: (LlmSettings) -> LlmSettings) {
        val id = activeProvider?.id ?: return
        store.update { current ->
            val provider = current.provider(id) ?: return@update current
            if (current.defaultModel?.providerId != id) return@update current
            current.replace(provider.copy(connection = change(provider.connection)), current.defaultModel)
        }
    }
    LlmPage(I18n.t("settings_llm_title"), topInset, onBack, actions = {
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, I18n.t("llm_provider_add")) }
    }) {
        item("default") {
            SettingsSectionCard {
                ListItem(
                    headlineContent = { Text(I18n.t("llm_default_model")) },
                    supportingContent = {
                        AnimatedContent(activeModel?.label, transitionSpec = {
                            fadeIn(standardTween()) togetherWith fadeOut(standardTween())
                        }, label = "defaultModel") { label ->
                            Text(label ?: I18n.t("settings_llm_not_configured"))
                        }
                    },
                )
            }
        }
        item("heading") { Text(I18n.t("llm_providers"), style = MaterialTheme.typography.titleMedium) }
        items(settings.providers, key = LlmProvider::id) { provider ->
            Card(onClick = { onOpen(provider) }, modifier = Modifier.fillMaxWidth().animateItem()) {
                ListItem(
                    headlineContent = { Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${provider.connection.protocol.label} · ${I18n.t("llm_model_count", provider.models.size)}") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DefaultBadge(active?.providerId == provider.id)
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    },
                )
            }
        }
        if (settings.providers.isEmpty()) {
            item("empty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(I18n.t("llm_provider_empty"))
                    FilledTonalButton(onClick = onAdd) { Text(I18n.t("llm_provider_add")) }
                }
            }
        }
        item("chatPreferences") {
            SettingsSectionCard {
                SettingsSwitchRow(
                    I18n.t("settings_llm_show_reasoning"), I18n.t("settings_llm_show_reasoning_desc"),
                    settings.showReasoning, { enabled -> store.update { it.copy(showReasoning = enabled) } },
                )
                Text(
                    activeProvider?.let { I18n.t("llm_chat_preferences_provider", it.name) }
                        ?: I18n.t("llm_chat_preferences_no_default"),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingsSwitchRow(
                    I18n.t("settings_llm_image_input"), I18n.t("settings_llm_image_input_desc"),
                    activeProvider?.connection?.imageInputEnabled == true,
                    { enabled -> updateActiveConnection { it.copy(imageInputEnabled = enabled) } },
                    enabled = activeProvider != null,
                )
                SettingsSwitchRow(
                    I18n.t("settings_llm_send_time"), I18n.t("settings_llm_send_time_desc"),
                    activeProvider?.connection?.sendMessageTime == true,
                    { enabled -> updateActiveConnection { it.copy(sendMessageTime = enabled) } },
                    divider = false,
                    enabled = activeProvider != null,
                )
            }
        }
        item("history") { ClearChatHistory() }
    }
}

@Composable
private fun DefaultBadge(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(standardTween()), exit = fadeOut(standardTween())) {
        Text(
            I18n.t("llm_default_badge"),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ProviderDetail(
    provider: LlmProvider,
    settings: LlmProviderSettings,
    topInset: Dp,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    store: LlmProviderStore,
    onDeleted: () -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var editedModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteProvider by rememberSaveable { mutableStateOf(false) }
    val active = settings.defaultModel

    LlmPage(provider.name, topInset, onBack, actions = {
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, I18n.t("llm_provider_edit")) }
        IconButton(onClick = { deleteProvider = true }) { Icon(Icons.Outlined.Delete, I18n.t("llm_provider_delete")) }
    }) {
        item("connection") {
            SettingsSectionCard {
                ListItem(
                    headlineContent = { Text(provider.connection.protocol.label) },
                    supportingContent = { Text(provider.connection.baseUrl) },
                )
            }
        }
        item("add") {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(I18n.t("llm_models"), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showPicker = true }) { Text(I18n.t("llm_model_add")) }
            }
        }
        if (provider.models.isEmpty()) item("empty") { Text(I18n.t("llm_models_empty")) }
        items(provider.models, key = LlmModel::id) { model ->
            val ref = LlmModelRef(provider.id, model.id)
            val isDefault = active == ref
            var menuOpen by remember { mutableStateOf(false) }
            SettingsSectionCard(modifier = Modifier.animateItem()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(model.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            AnimatedVisibility(isDefault, enter = fadeIn(standardTween()), exit = fadeOut(standardTween())) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        I18n.t("llm_default_badge"),
                                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = if (model.label == model.id) null else ({
                        Text(model.id, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }),
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreHoriz, I18n.t("llm_model_actions", model.label))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!isDefault) {
                                    DropdownMenuItem(
                                        text = { Text(I18n.t("llm_set_default")) },
                                        enabled = settings.settings(ref)?.isConfigured == true,
                                        onClick = {
                                            menuOpen = false
                                            store.update { it.copy(defaultModel = ref) }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(I18n.t("llm_model_edit")) },
                                    onClick = { menuOpen = false; editedModelId = model.id },
                                )
                                DropdownMenuItem(
                                    text = { Text(I18n.t("llm_model_delete")) },
                                    onClick = { menuOpen = false; deleteTarget = model.id },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showPicker) {
        ModelPicker(provider, onDismiss = { showPicker = false }) { added ->
            store.update { current ->
                val live = current.provider(provider.id) ?: return@update current
                current.replace(live.copy(models = live.models + added).normalized(), current.defaultModel)
            }
        }
    }
    val edited = provider.models.firstOrNull { it.id == editedModelId }
    if (edited != null) {
        ModelEditor(provider, edited, onDismiss = { editedModelId = null }) { replacement ->
            store.update { current ->
                val live = current.provider(provider.id) ?: return@update current
                val ref = LlmModelRef(provider.id, edited.id)
                val nextDefault = if (current.defaultModel == ref) ref.copy(modelId = replacement.id) else current.defaultModel
                current.replace(live.copy(models = live.models.map { if (it.id == edited.id) replacement else it }), nextDefault)
            }
            editedModelId = null
        }
    }
    val removed = provider.models.firstOrNull { it.id == deleteTarget }
    if (deleteProvider || removed != null) {
        val deletingProvider = deleteProvider
        val removesDefault = active?.providerId == provider.id && (deletingProvider || active.modelId == removed?.id)
        DeleteLlmItemDialog(
            title = I18n.t(if (deletingProvider) "llm_provider_delete" else "llm_model_delete"),
            name = if (deletingProvider) provider.name else requireNotNull(removed).label,
            settings = settings,
            removesDefault = removesDefault,
            exclude = { ref -> ref.providerId == provider.id && (deletingProvider || ref.modelId == removed?.id) },
            onDismiss = { deleteProvider = false; deleteTarget = null },
        ) { replacement ->
            store.update { current ->
                val live = current.provider(provider.id) ?: return@update current
                val currentDefault = current.defaultModel
                val affected = currentDefault?.providerId == provider.id && (deletingProvider || currentDefault.modelId == removed?.id)
                val next = if (affected) replacement else currentDefault
                if (deletingProvider) current.copy(providers = current.providers.filterNot { it.id == live.id }, defaultModel = next)
                else current.replace(live.copy(models = live.models.filterNot { it.id == removed?.id }), next)
            }
            deleteProvider = false
            deleteTarget = null
            if (deletingProvider) onDeleted()
        }
    }
}

@Composable
private fun DeleteLlmItemDialog(
    title: String,
    name: String,
    settings: LlmProviderSettings,
    removesDefault: Boolean,
    exclude: (LlmModelRef) -> Boolean,
    onDismiss: () -> Unit,
    onDelete: (LlmModelRef?) -> Unit,
) {
    var replacementProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var replacementModel by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = replacementProvider?.let { provider -> replacementModel?.let { LlmModelRef(provider, it) } }
    val options = if (removesDefault) settings.providers.flatMap { provider ->
        provider.models.mapNotNull { model ->
            val ref = LlmModelRef(provider.id, model.id)
            if (!exclude(ref) && settings.settings(ref)?.isConfigured == true) ref to "${provider.name} · ${model.label}" else null
        }
    } else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(I18n.t("llm_delete_confirm", name))
                if (removesDefault) {
                    Text(I18n.t("llm_delete_default"))
                    LazyColumn(Modifier.heightIn(max = 224.dp)) {
                        item { ReplacementRow(I18n.t("llm_clear_default"), selected == null) { replacementProvider = null; replacementModel = null } }
                        items(options) { (ref, label) ->
                            ReplacementRow(label, selected == ref) {
                                replacementProvider = ref.providerId
                                replacementModel = ref.modelId
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(selected) }) { Text(I18n.t("settings_action_delete")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(I18n.t("cancel")) } },
    )
}

@Composable
private fun ReplacementRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

private const val CLEAR_CHAT_HISTORY_PHRASE = "确认清空全部聊天记录"

private enum class ClearHistoryStep { WARNING, TYPE_PHRASE, FAILED }

@Composable
private fun ClearChatHistory() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf<ClearHistoryStep?>(null) }
    var typedPhrase by rememberSaveable { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    fun dismiss() {
        step = null
        typedPhrase = ""
    }
    TextButton(onClick = { step = ClearHistoryStep.WARNING }, enabled = !clearing) {
        Text(I18n.t("settings_llm_clear_all"))
    }
    val current = step
    if (current != null) {
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(I18n.t("settings_llm_clear_all")) },
            text = {
                when (current) {
                    ClearHistoryStep.WARNING -> Text(I18n.t("settings_llm_clear_all_confirm"))
                    ClearHistoryStep.TYPE_PHRASE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(I18n.t("llm_clear_history_phrase_prompt", CLEAR_CHAT_HISTORY_PHRASE))
                        LlmField(typedPhrase, { typedPhrase = it }, "llm_clear_history_phrase_label")
                    }
                    ClearHistoryStep.FAILED -> Text(I18n.t("chat_history_delete_failed"))
                }
            },
            confirmButton = {
                when (current) {
                    ClearHistoryStep.WARNING -> TextButton(onClick = { step = ClearHistoryStep.TYPE_PHRASE }) {
                        Text(I18n.t("llm_clear_history_continue"))
                    }
                    ClearHistoryStep.TYPE_PHRASE -> TextButton(
                        enabled = typedPhrase == CLEAR_CHAT_HISTORY_PHRASE && !clearing,
                        onClick = {
                            if (step != ClearHistoryStep.TYPE_PHRASE || typedPhrase != CLEAR_CHAT_HISTORY_PHRASE || clearing) return@TextButton
                            clearing = true
                            dismiss()
                            scope.launch {
                                val cleared = withContext(Dispatchers.IO) {
                                    runCatching { ChatHistoryRepository(context).clearAll() }.isSuccess
                                }
                                clearing = false
                                if (!cleared) step = ClearHistoryStep.FAILED
                            }
                        }
                    ) { Text(I18n.t("settings_action_delete")) }
                    ClearHistoryStep.FAILED -> TextButton(onClick = ::dismiss) { Text(I18n.t("confirm")) }
                }
            },
            dismissButton = if (current == ClearHistoryStep.FAILED) null else ({
                TextButton(onClick = ::dismiss) { Text(I18n.t("cancel")) }
            }),
        )
    }
}
