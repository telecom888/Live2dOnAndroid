@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bangdream.pet.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangdream.pet.data.AppData
import com.bangdream.pet.ChatDisplayPreferences
import com.bangdream.pet.I18n
import com.bangdream.pet.loadLineUiEnabled
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.data.CharacterInfo
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.llm.ChatConversationSummary
import com.bangdream.pet.llm.ChatTextSearch
import com.bangdream.pet.llm.ChatUiState
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.llm.Live2DChatViewModel
import com.bangdream.pet.llm.TimeContextOverride
import com.bangdream.pet.ui.design.emphasizedTween
import com.bangdream.pet.ui.design.expressiveTween
import com.bangdream.pet.ui.live2d.ChatMessageList
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 角色对话管理：一级=角色列表，二级=该角色对话列表，三级=对话页（输入框同普通 chatbox）。
 * 对话列表支持搜索/长按详情（消息数、总字数、重命名）；对话页支持消息搜索（带匹配算法）与思维链折叠。
 */
@Composable
fun ConversationManagerScreen(
    appData: AppData?,
    repository: DataRepository,
    onDetailActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: Live2DChatViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedCharacter by remember { mutableStateOf<ModelChoice?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var showCharacterSettings by remember { mutableStateOf(false) }

    val level = when {
        selectedCharacter == null -> 0
        showCharacterSettings -> 3
        openConversationId == null -> 1
        else -> 2
    }
    LaunchedEffect(level) { onDetailActiveChange(level == 2) }
    BackHandler(enabled = level != 0) {
        when (level) {
            3 -> showCharacterSettings = false
            2 -> openConversationId = null
            else -> selectedCharacter = null
        }
    }
    AnimatedContent(
        targetState = level,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(animationSpec = emphasizedTween(), initialOffsetX = { it }) +
                    fadeIn(animationSpec = emphasizedTween()))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = expressiveTween(), targetOffsetX = { -it / 4 }) +
                            fadeOut(animationSpec = expressiveTween()),
                    )
            } else {
                (slideInHorizontally(animationSpec = emphasizedTween(), initialOffsetX = { -it / 4 }) +
                    fadeIn(animationSpec = emphasizedTween()))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = expressiveTween(), targetOffsetX = { it }) +
                            fadeOut(animationSpec = expressiveTween()),
                    )
            }
        },
        modifier = modifier,
        label = "conversationLevel",
    ) { target ->
        val character = selectedCharacter
        if (target != 0 && character == null) return@AnimatedContent
        when (target) {
            0 -> CharacterListScreen(
                appData = appData,
                onCharacterClick = { character ->
                    scope.launch {
                        val model = withContext(Dispatchers.IO) {
                            runCatching { repository.availableModels(character).firstOrNull() }.getOrNull()
                        }
                        // 未下载/未使用角色也允许进入（占位 ModelChoice，历史可浏览）
                        val choice = model ?: ModelChoice(
                            characterId = character.id,
                            characterName = character.display,
                            costumeId = "",
                            costumeName = I18n.t("chat_not_downloaded"),
                            modelAssetPath = "",
                        )
                        selectedCharacter = choice
                        openConversationId = null
                        showCharacterSettings = false
                        viewModel.selectCharacter(choice, force = true)
                    }
                },
            )
            1 -> ConversationListScreen(
                state = state,
                character = character!!,
                viewModel = viewModel,
                onBack = { selectedCharacter = null },
                onOpen = { id ->
                    viewModel.selectConversation(character.characterId, id)
                    openConversationId = id
                    showCharacterSettings = false
                },
                onNew = {
                    viewModel.startNewConversation(selectedCharacter!!.characterId)
                    openConversationId = "new"
                    showCharacterSettings = false
                },
                onCharacterSettings = { showCharacterSettings = true },
            )
            3 -> CharacterSettingsScreen(
                character = character!!,
                onBack = { showCharacterSettings = false },
            )
            else -> ConversationDetailScreen(
                state = state,
                character = character!!,
                viewModel = viewModel,
                onBack = { openConversationId = null },
            )
        }
    }
}

@Composable
private fun CharacterListScreen(
    appData: AppData?,
    onCharacterClick: (CharacterInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val characters = remember(appData) {
        appData?.characters?.values?.sortedBy { it.display }.orEmpty()
    }
    var filter by remember { mutableStateOf("") }
    val filtered = remember(characters, filter) {
        val query = filter.trim()
        if (query.isEmpty()) {
            characters
        } else {
            characters.filter { it.display.contains(query, ignoreCase = true) }
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                I18n.t("chat_characters"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text(I18n.t("chat_search_characters")) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(I18n.t(if (characters.isEmpty()) "chat_no_characters" else "chat_no_character_match"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(filtered, key = { _, item -> item.id }) { index, character ->
                    Surface(
                        onClick = { onCharacterClick(character) },
                        modifier = Modifier
                            .fillMaxWidth()
                            // 原来用 filtered.indexOfFirst{...} 求入场延迟，逐项 O(n) → 整列表 O(n²)
                            .appEntrance(delayMillis = (index * 22).coerceAtMost(160)),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    character.display,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    I18n.t("chat_costume_count", character.costumes.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationListScreen(
    state: ChatUiState,
    character: ModelChoice,
    viewModel: Live2DChatViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onCharacterSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var detailConversation by remember { mutableStateOf<ChatConversationSummary?>(null) }
    var actionConversation by remember { mutableStateOf<ChatConversationSummary?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatConversationSummary?>(null) }
    var stats by remember { mutableStateOf<Live2DChatViewModel.ConversationStats?>(null) }
    var filter by remember { mutableStateOf("") }
    var renameDraft by remember(detailConversation) { mutableStateOf(detailConversation?.title.orEmpty()) }

    LaunchedEffect(detailConversation) {
        stats = null
        val conversation = detailConversation ?: return@LaunchedEffect
        stats = viewModel.conversationStats(character, conversation.id)
    }

    val filtered = remember(state.conversations, filter) {
        val query = filter.trim()
        if (query.isEmpty()) {
            state.conversations
        } else {
            val lower = query.lowercase()
            state.conversations.filter {
                it.title.contains(lower, ignoreCase = true) ||
                    it.preview.contains(lower, ignoreCase = true) ||
                    it.searchableContent.contains(lower, ignoreCase = true)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = I18n.t("back"))
            }
            Text(
                character.characterName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCharacterSettings) {
                Icon(Icons.Outlined.Tune, contentDescription = I18n.t("chat_character_settings"))
            }
            IconButton(onClick = onNew) {
                Icon(Icons.Outlined.AddComment, contentDescription = I18n.t("chat_new_conversation"))
            }
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text(I18n.t("chat_search_conversations")) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        when {
            state.isHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(I18n.t("chat_history_empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filtered, key = { _, item -> item.id }) { index, conversation ->
                    val selected = conversation.id == state.conversationId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(conversation.id) },
                                onLongClick = {
                                    actionConversation = conversation
                                },
                            )
                            .appEntrance(delayMillis = (index * 22).coerceAtMost(160)),
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val queryText = filter.trim()
                            val contentSnippet = remember(conversation.id, queryText, conversation.searchableContent) {
                                if (queryText.isBlank()) null else messageContentSnippet(conversation.searchableContent, queryText)
                            }
                            Column(Modifier.weight(1f)) {
                                HighlightText(
                                    text = conversation.title.ifBlank { I18n.t("chat_untitled_conversation") },
                                    query = queryText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                                HighlightText(
                                    text = contentSnippet ?: conversation.preview.ifBlank { I18n.t("chat_empty_conversation") },
                                    query = queryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    formatTimestamp(conversation.updatedAt),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                pendingDelete = conversation
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = I18n.t("settings_action_delete"))
                            }
                        }
                    }
                }
            }
        }
    }

    detailConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { detailConversation = null },
            title = { Text(I18n.t("chat_conversation_details")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(I18n.t("chat_stats_messages", stats?.messageCount ?: "…"))
                    Text(I18n.t("chat_stats_characters", stats?.totalChars ?: "…"))
                    Text(I18n.t("chat_stats_prompt_chars", stats?.systemPromptChars ?: "…"))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text(I18n.t("chat_rename_title")) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameDraft.isNotBlank() && renameDraft != conversation.title) {
                        viewModel.renameConversation(character.characterId, conversation.id, renameDraft)
                    }
                    detailConversation = null
                }) { Text(I18n.t("settings_action_save")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { detailConversation = null }) { Text(I18n.t("cancel")) }
                    TextButton(onClick = {
                        viewModel.deleteConversation(character.characterId, conversation.id)
                        detailConversation = null
                    }) { Text(I18n.t("settings_action_delete"), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    actionConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { actionConversation = null },
            title = { Text(I18n.t("chat_conversation_actions")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        conversation.title.ifBlank { I18n.t("chat_untitled_conversation") },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        I18n.t("chat_load_memory_desc", character.characterName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.loadConversationAsMemory(
                        character.characterId,
                        character.characterName,
                        conversation.id,
                    )
                    Toast.makeText(context, I18n.t("chat_memory_loaded"), Toast.LENGTH_SHORT).show()
                    actionConversation = null
                }) { Text(I18n.t("chat_load_memory")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { actionConversation = null }) { Text(I18n.t("cancel")) }
                    TextButton(onClick = {
                        renameDraft = conversation.title
                        detailConversation = conversation
                        actionConversation = null
                    }) { Text(I18n.t("chat_conversation_details")) }
                }
            },
        )
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(I18n.t("chat_delete_conversation")) },
            text = {
                Text(
                    I18n.t("chat_delete_conversation_confirm", conversation.title.ifBlank { I18n.t("chat_untitled_conversation") }),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(character.characterId, conversation.id)
                    pendingDelete = null
                }) { Text(I18n.t("settings_action_delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(I18n.t("cancel")) }
            },
        )
    }
}

@Composable
private fun ConversationDetailScreen(
    state: ChatUiState,
    character: ModelChoice,
    viewModel: Live2DChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChatTextSearch.MessageMatch>>(emptyList()) }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var timeMenuExpanded by remember { mutableStateOf(false) }
    val input = remember { mutableStateOf("") }
    val context = LocalContext.current
    val lineMode = loadLineUiEnabled(context)
    val displayPreferences = ChatDisplayPreferences.load(context)
    val lineBackgroundColor = Color(displayPreferences.lineBackgroundColor)
    val lineForegroundColor = if (lineBackgroundColor.luminance() < 0.45f) Color.White else Color(0xFF1B1B1B)
    val scope = rememberCoroutineScope()
    val contextTokens = remember { LlmSettings.load(context.applicationContext).contextTokens }
    val usedTokens = remember(state.messages) {
        state.messages.sumOf { ChatTextSearch.estimateTokens(it.content) }
    }
    val usageRatio = (usedTokens.toFloat() / contextTokens.toFloat()).coerceIn(0f, 1f)
    val imageInputEnabled = remember { LlmSettings.load(context.applicationContext).imageInputEnabled }
    var selectedImages by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                // 读取 + base64 编码可能是几 MB，之前直接跑在主线程协程里会卡住输入框
                val picked = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        contentUriToImageDataUrl(context, uri)?.let { PickedImage(uri, it) }
                    }
                }
                selectedImages = (selectedImages + picked).distinctBy { it.uri }
            }
        }
    }
    BackHandler(enabled = searchMode) {
        searchMode = false
        searchQuery = ""
        scrollTarget = null
    }

    LaunchedEffect(searchQuery, state.messages) {
        searchResults = if (searchQuery.isBlank()) {
            emptyList()
        } else {
            ChatTextSearch.searchMessages(state.messages, searchQuery)
        }
    }

    fun send() {
        val text = input.value.trim()
        val images = selectedImages.map { it.dataUrl }
        if (
            (text.isNotEmpty() || images.isNotEmpty()) &&
            viewModel.send(character, text, images)
        ) {
            input.value = ""
            selectedImages = emptyList()
        }
    }

    CompositionLocalProvider(LocalContentColor provides if (lineMode) lineForegroundColor else LocalContentColor.current) {
    Column(modifier.fillMaxSize().then(if (lineMode) Modifier.background(lineBackgroundColor) else Modifier).navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = I18n.t("back"))
            }
            if (searchMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(I18n.t("chat_search_messages")) },
                    singleLine = true,
                    colors = if (lineMode) OutlinedTextFieldDefaults.colors(
                        focusedTextColor = lineForegroundColor, unfocusedTextColor = lineForegroundColor,
                        focusedBorderColor = lineForegroundColor, unfocusedBorderColor = lineForegroundColor.copy(alpha = 0.75f),
                    ) else OutlinedTextFieldDefaults.colors(),
                )
                IconButton(onClick = { searchMode = false; searchQuery = ""; scrollTarget = null }) {
                    Icon(Icons.Outlined.Close, contentDescription = I18n.t("chat_close_search"))
                }
            } else {
                Text(
                    state.conversationTitle.ifBlank { I18n.t("chat_new_conversation") },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (lineMode) lineForegroundColor else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    renameDraft = state.conversationTitle
                    renameDialog = true
                }) { Text(I18n.t("chat_rename_short"), style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = { searchMode = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = I18n.t("chat_search"))
                }
            }
        }
        if (displayPreferences.showContextUsage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    I18n.t("chat_context_usage_estimate"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lineMode) lineForegroundColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatTokens(usedTokens)} / ${formatTokens(contextTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lineMode) lineForegroundColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (displayPreferences.showModelTimeControl) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { timeMenuExpanded = true }, enabled = !state.isGenerating) {
                    Text(I18n.t("chat_time_mode", I18n.t(when (state.timeContextOverride) {
                        TimeContextOverride.INHERIT -> "chat_time_inherit"
                        TimeContextOverride.ENABLED -> "chat_time_enabled"
                        TimeContextOverride.DISABLED -> "chat_time_disabled"
                    })))
                }
                DropdownMenu(expanded = timeMenuExpanded, onDismissRequest = { timeMenuExpanded = false }) {
                    listOf(
                        TimeContextOverride.INHERIT to "chat_time_inherit",
                        TimeContextOverride.ENABLED to "chat_time_this_enabled",
                        TimeContextOverride.DISABLED to "chat_time_this_disabled",
                    ).forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(I18n.t(label)) }, onClick = {
                            viewModel.setTimeContextOverride(value)
                            timeMenuExpanded = false
                        })
                    }
                }
            }
        }
        if (displayPreferences.showContextUsage) {
            LinearProgressIndicator(
                progress = { usageRatio },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(5.dp),
                color = if (usageRatio >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (state.isHistoryLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (searchMode && searchQuery.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(searchResults, key = { it.messageId }) { match ->
                    Surface(
                        onClick = {
                            scrollTarget = match.messageId
                            searchMode = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                match.content,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                I18n.t("chat_search_result_meta", match.messageIndex + 1, match.score),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (searchResults.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(I18n.t("chat_no_message_match"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            ChatMessageList(
                messages = state.messages,
                streamingText = state.streamingText,
                thinking = state.isThinking || (state.isGenerating && state.streamingText.isBlank()),
                revealingReplyId = state.revealingReplyId,
                revealedSegmentCount = state.revealedSegmentCount,
                streamingReasoning = state.streamingReasoning,
                highlightQuery = searchQuery.takeIf { it.isNotBlank() },
                scrollToMessageId = scrollTarget,
                onReplay = { message -> viewModel.replayMessage(character.characterId, message.content) },
                characterId = character.characterId,
                characterName = character.characterName,
                lineMode = lineMode,
                displayPreferences = displayPreferences,
                versionPositions = state.versionPositions,
                restoreReplyId = state.restoreReplyId,
                onSwitchVersion = if (!state.isGenerating) viewModel::switchMessageVersion else null,
                onEditMessage = if (!state.isGenerating) { message, text, keep ->
                    viewModel.editAndResend(character, message.id, text, keep)
                    Unit
                } else null,
                onRegenerateMessage = if (!state.isGenerating) { message ->
                    viewModel.regenerate(character, message.id)
                    Unit
                } else null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        if (selectedImages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedImages.forEach { image ->
                    PickedImageThumb(
                        uri = image.uri,
                        onRemove = { selectedImages = selectedImages - image },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (imageInputEnabled) {
                IconButton(
                    onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.isGenerating,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = I18n.t("chat_add_image"))
                }
            }
            OutlinedTextField(
                value = input.value,
                onValueChange = { input.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(I18n.t("chat_input_hint")) },
                maxLines = 4,
                enabled = !state.isGenerating,
                colors = if (lineMode) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1B1B1B), unfocusedTextColor = Color(0xFF1B1B1B),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF435B69), unfocusedBorderColor = Color(0xFF435B69),
                ) else OutlinedTextFieldDefaults.colors(),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { if (state.isGenerating) viewModel.stop() else send() },
                enabled = state.isGenerating || input.value.isNotBlank() || selectedImages.isNotEmpty(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (state.isGenerating) Icons.Outlined.Stop else Icons.Outlined.Send,
                    contentDescription = I18n.t(if (state.isGenerating) "chat_stop" else "chat_send"),
                )
            }
        }
    }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text(I18n.t("chat_rename_conversation")) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.conversationId?.let { id ->
                        viewModel.renameConversation(character.characterId, id, renameDraft)
                    }
                    renameDialog = false
                }) { Text(I18n.t("settings_action_save")) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text(I18n.t("cancel")) }
            },
        )
    }

}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}

private fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000f)
    tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000f)
    else -> tokens.toString()
}

/** 在对话全文里定位第一个命中点，取上下文片段（首尾加省略号）。未命中返回 null。 */
private fun messageContentSnippet(content: String, query: String): String? {
    val q = query.trim()
    if (q.isEmpty()) return null
    val lower = content.lowercase()
    val pos = lower.indexOf(q.lowercase())
    if (pos < 0) return null
    val radius = 26
    val start = (pos - radius).coerceAtLeast(0)
    val end = (pos + q.length + radius).coerceAtMost(content.length)
    val raw = content.substring(start, end)
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    return buildString {
        if (start > 0) append("…")
        append(raw)
        if (end < content.length) append("…")
    }
}

/** 带关键字高亮（黄底加粗）的文本。 */
@Composable
private fun HighlightText(
    text: String,
    query: String,
    style: TextStyle,
    color: Color? = null,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val ranges = remember(text, query) {
        if (query.isBlank()) emptyList() else ChatTextSearch.findHighlightRanges(text, query)
    }
    if (ranges.isEmpty()) {
        Text(
            text = text,
            style = style,
            color = color ?: Color.Unspecified,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }
    val annotated = buildAnnotatedString {
        var last = 0
        for (range in ranges.sortedBy { it.first }) {
            val start = range.first.coerceIn(0, text.length)
            val end = range.last.plus(1).coerceIn(start, text.length)
            if (start > last) append(text.substring(last, start))
            withStyle(
                SpanStyle(
                    background = Color(0x66FFD54F),
                    fontWeight = FontWeight.Bold,
                )
            ) {
                append(text.substring(start, end))
            }
            last = end
        }
        if (last < text.length) append(text.substring(last))
    }
    Text(
        text = annotated,
        style = style,
        color = color ?: Color.Unspecified,
        maxLines = maxLines,
        overflow = overflow,
    )
}
