@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bangdream.pet.ui.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangdream.pet.data.AppData
import com.bangdream.pet.data.CharacterInfo
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.llm.ChatConversationSummary
import com.bangdream.pet.llm.ChatTextSearch
import com.bangdream.pet.llm.ChatUiState
import com.bangdream.pet.llm.Live2DChatViewModel
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
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val viewModel: Live2DChatViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedCharacter by remember { mutableStateOf<ModelChoice?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }

    when {
        selectedCharacter == null -> CharacterListScreen(
            appData = appData,
            topInset = topInset,
            modifier = modifier,
            onCharacterClick = { character ->
                scope.launch {
                    val model = withContext(Dispatchers.IO) {
                        repository.availableModels(character).firstOrNull()
                    }
                    if (model != null) {
                        selectedCharacter = model
                        openConversationId = null
                        viewModel.selectCharacter(model, force = true)
                    }
                }
            },
        )
        openConversationId == null -> ConversationListScreen(
            state = state,
            character = selectedCharacter!!,
            topInset = topInset,
            modifier = modifier,
            viewModel = viewModel,
            onBack = { selectedCharacter = null },
            onOpen = { id -> openConversationId = id },
            onNew = {
                viewModel.startNewConversation(selectedCharacter!!.characterId)
                openConversationId = "new"
            },
        )
        else -> ConversationDetailScreen(
            state = state,
            character = selectedCharacter!!,
            topInset = topInset,
            modifier = modifier,
            viewModel = viewModel,
            onBack = { openConversationId = null },
        )
    }
}

@Composable
private fun CharacterListScreen(
    appData: AppData?,
    topInset: Dp,
    onCharacterClick: (CharacterInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val characters = remember(appData) {
        appData?.characters?.values?.sortedBy { it.display }.orEmpty()
    }
    Column(modifier.fillMaxSize().padding(top = topInset)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "角色",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        if (characters.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无角色数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    Surface(
                        onClick = { onCharacterClick(character) },
                        modifier = Modifier.fillMaxWidth(),
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
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
    topInset: Dp,
    viewModel: Live2DChatViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var detailConversation by remember { mutableStateOf<ChatConversationSummary?>(null) }
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
                it.title.contains(lower, ignoreCase = true) || it.preview.contains(lower, ignoreCase = true)
            }
        }
    }

    Column(modifier.fillMaxSize().padding(top = topInset)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                character.characterName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
                                    renameDraft = conversation.title
                                    detailConversation = conversation
                                },
                            ),
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
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conversation.title.ifBlank { "未命名对话" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                )
                                Text(
                                    conversation.preview.ifBlank { "空对话" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
private fun ConversationDetailScreen(
    state: ChatUiState,
    character: ModelChoice,
    topInset: Dp,
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery, state.messages) {
        searchResults = if (searchQuery.isBlank()) {
            emptyList()
        } else {
            ChatTextSearch.searchMessages(state.messages, searchQuery)
        }
    }

    fun send() {
        val text = input.value.trim()
        if (text.isNotEmpty() && viewModel.send(character, text)) input.value = ""
    }

    Column(modifier.fillMaxSize().padding(top = topInset)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
        if (searchMode && searchQuery.isNotBlank()) {
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
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
                enabled = state.isGenerating || input.value.isNotBlank(),
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
