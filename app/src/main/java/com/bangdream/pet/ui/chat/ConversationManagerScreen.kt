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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
                            costumeName = "未下载",
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
                "角色",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("搜索角色") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (characters.isEmpty()) "暂无角色数据" else "未找到角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { character ->
                    Surface(
                        onClick = { onCharacterClick(character) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .appEntrance(delayMillis = (filtered.indexOfFirst { it.id == character.id } * 22).coerceAtMost(160)),
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
                                    "${character.costumes.size} 套服装",
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
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                character.characterName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCharacterSettings) {
                Icon(Icons.Outlined.Tune, contentDescription = "角色设定")
            }
            IconButton(onClick = onNew) {
                Icon(Icons.Outlined.AddComment, contentDescription = "新建对话")
            }
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("搜索对话") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        when {
            state.isHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { conversation ->
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
                            .appEntrance(delayMillis = (filtered.indexOfFirst { it.id == conversation.id } * 22).coerceAtMost(160)),
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
                                    text = conversation.title.ifBlank { "未命名对话" },
                                    query = queryText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                                HighlightText(
                                    text = contentSnippet ?: conversation.preview.ifBlank { "空对话" },
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
                                viewModel.deleteConversation(character.characterId, conversation.id)
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除")
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
            title = { Text("对话详情") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("消息数：${stats?.messageCount ?: "…"}")
                    Text("消息总字数：${stats?.totalChars ?: "…"}")
                    Text("角色设定字数：${stats?.systemPromptChars ?: "…"}")
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("重命名标题") },
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
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { detailConversation = null }) { Text("取消") }
                    TextButton(onClick = {
                        viewModel.deleteConversation(character.characterId, conversation.id)
                        detailConversation = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    actionConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { actionConversation = null },
            title = { Text("对话操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        conversation.title.ifBlank { "未命名对话" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "载入后，该对话将作为「${character.characterName}」的长期记忆，之后的对话会自然地带上它。",
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
                    Toast.makeText(context, "已载入角色记忆", Toast.LENGTH_SHORT).show()
                    actionConversation = null
                }) { Text("载入角色记忆") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { actionConversation = null }) { Text("取消") }
                    TextButton(onClick = {
                        renameDraft = conversation.title
                        detailConversation = conversation
                        actionConversation = null
                    }) { Text("对话详情") }
                }
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
    val input = remember { mutableStateOf("") }
    val context = LocalContext.current
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
                val picked = uris.mapNotNull { uri ->
                    contentUriToImageDataUrl(context, uri)?.let { PickedImage(uri, it) }
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

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            if (searchMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索消息…") },
                    singleLine = true,
                )
                IconButton(onClick = { searchMode = false; searchQuery = ""; scrollTarget = null }) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
                }
            } else {
                Text(
                    state.conversationTitle.ifBlank { "新对话" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    renameDraft = state.conversationTitle
                    renameDialog = true
                }) { Text("改", style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = { searchMode = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "上下文占用（消息内容预估）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatTokens(usedTokens)} / ${formatTokens(contextTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { usageRatio },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(5.dp),
            color = if (usageRatio >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(6.dp))
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
                                "第 ${match.messageIndex + 1} 条 · 匹配度 ${match.score}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (searchResults.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("未找到匹配消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            ChatMessageList(
                messages = state.messages,
                streamingText = state.streamingText,
                thinking = state.isThinking,
                streamingReasoning = state.streamingReasoning,
                highlightQuery = searchQuery.takeIf { it.isNotBlank() },
                scrollToMessageId = scrollTarget,
                onReplay = { message -> viewModel.replayMessage(character.characterId, message.content) },
                characterId = character.characterId,
                lineMode = loadLineUiEnabled(context),
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
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "添加图片")
                }
            }
            OutlinedTextField(
                value = input.value,
                onValueChange = { input.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                maxLines = 4,
                enabled = !state.isGenerating,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { if (state.isGenerating) viewModel.stop() else send() },
                enabled = state.isGenerating || input.value.isNotBlank() || selectedImages.isNotEmpty(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (state.isGenerating) Icons.Outlined.Stop else Icons.Outlined.Send,
                    contentDescription = if (state.isGenerating) "停止" else "发送",
                )
            }
        }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("重命名对话") },
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
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("取消") }
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
