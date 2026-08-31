package com.bangdream.pet.ui.live2d

import android.graphics.Rect
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bangdream.pet.I18n
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.llm.ChatConversationSummary
import com.bangdream.pet.llm.ChatMessage
import com.bangdream.pet.llm.ChatTextSearch
import com.bangdream.pet.llm.ChatUiState
import com.bangdream.pet.llm.Live2DChatViewModel
import com.bangdream.pet.llm.LlmSettings
import com.bangdream.pet.loadLineUiEnabled
import com.bangdream.pet.AvatarManager
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import com.bangdream.pet.ui.chat.PickedImage
import com.bangdream.pet.ui.chat.PickedImageThumb
import com.bangdream.pet.ui.chat.contentUriToImageDataUrl
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Live2DChatOverlay(
    model: ModelChoice,
    viewModel: Live2DChatViewModel,
    expanded: Boolean,
    launcherVisible: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val focusManager = LocalFocusManager.current
    val settings = remember { LlmSettings.load(context.applicationContext) }
    val input = remember(model.characterId) { mutableStateOf("") }
    var showingHistory by remember(model.characterId) { mutableStateOf(false) }
    var pendingDelete by remember(model.characterId) { mutableStateOf<ChatConversationSummary?>(null) }
    var overlayBottomOnScreenPx by remember { mutableStateOf(0f) }

    LaunchedEffect(model.characterId, expanded) { viewModel.selectCharacter(model, force = expanded) }
    LaunchedEffect(expanded) {
        if (!expanded) focusManager.clearFocus()
    }
    LaunchedEffect(showingHistory) {
        if (showingHistory) focusManager.clearFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayBottomOnScreenPx = coordinates.positionOnScreen().y + coordinates.size.height
            },
    ) {
        val density = LocalDensity.current
        val imeHeightPx = WindowInsets.ime.getBottom(density)
        val visibleWindowFrame = Rect().also(hostView::getWindowVisibleDisplayFrame)
        val imeOverlap = with(density) {
            calculateImeOverlapPx(
                containerBottomOnScreenPx = overlayBottomOnScreenPx,
                imeTopOnScreenPx = visibleWindowFrame.bottom,
                imeHeightPx = imeHeightPx,
            ).toDp()
        }
        // Keep the compact input-only design whenever the keyboard is visible.
        val compactForIme = imeHeightPx > 0

        val transition = updateTransition(targetState = expanded, label = "chatContainerMorph")
        val morphProgress by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
            label = "chatContainerMorphProgress",
        ) { isExpanded -> if (isExpanded) 1f else 0f }
        val panelContentAlpha = ((morphProgress - 0.28f) / 0.52f).coerceIn(0f, 1f)
        val launcherContentAlpha = (1f - morphProgress / 0.34f).coerceIn(0f, 1f)
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val panelInteractionSource = remember { MutableInteractionSource() }

        if (transition.currentState || transition.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f * morphProgress))
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null,
                    ) { onExpandedChange(false) },
            )
        }

        AnimatedVisibility(
            visible = expanded || launcherVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val launcherSize = 52.dp
                val panelWidth = minOf(maxWidth * 0.92f, 560.dp)
                val panelHeight = if (compactForIme) 80.dp else minOf(maxHeight * 0.60f, 620.dp)
                val containerWidth = launcherSize + (panelWidth - launcherSize) * morphProgress
                val containerHeight = launcherSize + (panelHeight - launcherSize) * morphProgress
                val bottomPadding = 18.dp + ((16.dp + imeOverlap) - 18.dp) * morphProgress
                val cornerRadius = 26.dp + (28.dp - 26.dp) * morphProgress
                val launcherColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
                val panelColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f)

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding)
                        .size(containerWidth, containerHeight)
                        .clickable(
                            interactionSource = panelInteractionSource,
                            indication = null,
                        ) {
                            if (!expanded && !transition.isRunning) onExpandedChange(true)
                        },
                    shape = RoundedCornerShape(cornerRadius),
                    color = lerp(launcherColor, panelColor, morphProgress),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp + 4.dp * morphProgress,
                    shadowElevation = 6.dp + 4.dp * morphProgress,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!transition.currentState || !transition.targetState) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = I18n.t("chat_open", model.characterName),
                                modifier = Modifier.graphicsLayer { alpha = launcherContentAlpha },
                            )
                        }

                        if (transition.currentState || transition.targetState) {
                            ChatPanelContent(
                                model = model,
                                viewModel = viewModel,
                                settings = settings,
                                compactForIme = compactForIme,
                                input = input,
                                showingHistory = showingHistory,
                                onHistoryRequest = { showingHistory = true },
                                onHistoryBack = { showingHistory = false },
                                onNewConversation = {
                                    input.value = ""
                                    viewModel.startNewConversation(model.characterId)
                                    showingHistory = false
                                },
                                onConversationSelected = { conversationId ->
                                    input.value = ""
                                    viewModel.selectConversation(model.characterId, conversationId)
                                    showingHistory = false
                                },
                                onDeleteRequest = { pendingDelete = it },
                                onCollapse = { onExpandedChange(false) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = panelContentAlpha }
                                    .padding(if (compactForIme) 12.dp else 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(I18n.t("chat_delete_conversation")) },
            text = {
                Text(
                    I18n.t(
                        "chat_delete_conversation_confirm",
                        conversation.title.ifBlank { I18n.t("chat_new_conversation") },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(model.characterId, conversation.id)
                    pendingDelete = null
                }) { Text(I18n.t("confirm")) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(I18n.t("cancel")) } },
        )
    }
}

@Composable
private fun ChatPanelContent(
    model: ModelChoice,
    viewModel: Live2DChatViewModel,
    settings: LlmSettings,
    compactForIme: Boolean,
    input: MutableState<String>,
    showingHistory: Boolean,
    onHistoryRequest: () -> Unit,
    onHistoryBack: () -> Unit,
    onNewConversation: () -> Unit,
    onConversationSelected: (String) -> Unit,
    onDeleteRequest: (ChatConversationSummary) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageInputEnabled = settings.imageInputEnabled
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

    if (showingHistory) {
        ChatHistoryPanel(
            state = state,
            onBack = onHistoryBack,
            onNewConversation = onNewConversation,
            onConversationSelected = onConversationSelected,
            onDeleteRequest = onDeleteRequest,
            modifier = modifier,
        )
        return
    }

    Column(modifier) {
        if (!compactForIme) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.characterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "本机模式 · ${settings.model.ifBlank { I18n.t("chat_not_configured_short") }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onHistoryRequest) {
                    Icon(Icons.Outlined.History, contentDescription = I18n.t("chat_history"))
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = I18n.t("chat_minimize"))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.isHistoryLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!settings.isConfigured) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    I18n.t("chat_not_configured"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (!compactForIme) {
                ChatMessageList(
                    messages = state.messages,
                    streamingText = state.streamingText,
                    thinking = state.isThinking,
                    onReplay = null,
                    characterId = model.characterId,
                    lineMode = loadLineUiEnabled(context),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                state.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                chatErrorText(error),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (isRetryableChatError(error)) {
                                IconButton(onClick = { viewModel.retry(model) }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = I18n.t("chat_retry"))
                                }
                            }
                        }
                    }
                }
            }
            if (!compactForIme && selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
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
                modifier = Modifier.fillMaxWidth(),
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
                    maxLines = if (compactForIme) 1 else 4,
                    enabled = !state.isGenerating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val message = input.value
                        val images = selectedImages.map { it.dataUrl }
                        if ((message.isNotBlank() || images.isNotEmpty()) && viewModel.send(model, message, images)) {
                            input.value = ""
                            selectedImages = emptyList()
                        }
                    }),
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val message = input.value
                        val images = selectedImages.map { it.dataUrl }
                        if (state.isGenerating) {
                            viewModel.stop()
                        } else if (message.isNotBlank() || images.isNotEmpty()) {
                            if (viewModel.send(model, message, images)) {
                                input.value = ""
                                selectedImages = emptyList()
                            }
                        }
                    },
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
}

@Composable
private fun ChatHistoryPanel(
    state: ChatUiState,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
    onConversationSelected: (String) -> Unit,
    onDeleteRequest: (ChatConversationSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = I18n.t("back"))
            }
            Text(
                I18n.t("chat_history"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onNewConversation) {
                Icon(Icons.Outlined.AddComment, contentDescription = I18n.t("chat_new_conversation"))
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.isHistoryLoading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.conversations.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    I18n.t("chat_history_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.conversations,
                    key = { it.id },
                    contentType = { "conversation" },
                ) { conversation ->
                    val selected = conversation.id == state.conversationId
                    Surface(
                        onClick = { onConversationSelected(conversation.id) },
                        modifier = Modifier.fillMaxWidth(),
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
                                    conversation.title.ifBlank { I18n.t("chat_new_conversation") },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                                Text(
                                    conversation.preview.ifBlank { I18n.t("chat_empty_conversation") },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    formatConversationTimestamp(conversation.updatedAt),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = I18n.t("chat_current_conversation"),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { onDeleteRequest(conversation) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = I18n.t("chat_delete_conversation"),
                                )
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                chatErrorText(error),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// DateFormat 创建很贵（要读 locale 数据），而这两个方法在消息列表里逐条、逐次重组调用。
private val conversationTimestampFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
}
private val messageTimeFormat by lazy { DateFormat.getTimeInstance(DateFormat.SHORT) }

private fun formatConversationTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return conversationTimestampFormat.format(Date(timestamp))
}

private fun chatErrorText(error: String): String = when (error) {
    Live2DChatViewModel.ERROR_LLM_NOT_CONFIGURED -> I18n.t("chat_not_configured")
    Live2DChatViewModel.ERROR_HISTORY_LOAD -> I18n.t("chat_history_load_failed")
    Live2DChatViewModel.ERROR_HISTORY_SAVE -> I18n.t("chat_history_save_failed")
    Live2DChatViewModel.ERROR_HISTORY_DELETE -> I18n.t("chat_history_delete_failed")
    else -> error
}

private fun isRetryableChatError(error: String): Boolean = error !in setOf(
    Live2DChatViewModel.ERROR_LLM_NOT_CONFIGURED,
    Live2DChatViewModel.ERROR_HISTORY_LOAD,
    Live2DChatViewModel.ERROR_HISTORY_SAVE,
    Live2DChatViewModel.ERROR_HISTORY_DELETE,
)

private fun calculateImeOverlapPx(
    containerBottomOnScreenPx: Float,
    imeTopOnScreenPx: Int,
    imeHeightPx: Int,
): Float {
    if (imeHeightPx <= 0 || containerBottomOnScreenPx <= 0f) return 0f
    return (containerBottomOnScreenPx - imeTopOnScreenPx).coerceIn(0f, imeHeightPx.toFloat())
}

@Composable
internal fun ChatMessageList(
    messages: List<ChatMessage>,
    streamingText: String,
    thinking: Boolean,
    streamingReasoning: String = "",
    onReplay: ((ChatMessage) -> Unit)? = null,
    highlightQuery: String? = null,
    scrollToMessageId: String? = null,
    characterId: String? = null,
    lineMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val avatar = if (lineMode) rememberChatAvatar(characterId) else null
    val userAvatar = if (lineMode) rememberUserAvatar() else null
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (streamingText.isNotBlank() || thinking) 1 else 0
    val streamScrollBucket = streamingText.length / 24
    var previousItemCount by remember { mutableIntStateOf(0) }
    var consumedJump by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToMessageId, messages.size) {
        val targetId = scrollToMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
            // 标记本次跳转已被处理，避免随后被「自动跟随到底部」覆盖
            consumedJump = true
        }
    }
    LaunchedEffect(itemCount, streamScrollBucket) {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasNearBottom = shouldFollowNewChatContent(previousItemCount, lastVisibleIndex)
        previousItemCount = itemCount
        if (itemCount > 0 && wasNearBottom && !consumedJump) {
            listState.scrollToItem(itemCount - 1)
        }
        consumedJump = false
    }
    BoxWithConstraints(modifier = modifier) {
        val bubbleMaxWidth = (maxWidth * 0.82f).coerceAtMost(560.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = messages,
                key = { it.id },
                contentType = { "message" },
            ) { message ->
                val replay = if (onReplay != null && message.role == "assistant") {
                    { onReplay(message) }
                } else {
                    null
                }
                ChatBubble(
                    message.role,
                    message.content,
                    reasoning = message.reasoning,
                    highlightQuery = highlightQuery,
                    onReplay = replay,
                    timestamp = message.timestamp,
                    images = message.images,
                    avatar = avatar,
                    userAvatar = userAvatar,
                    lineMode = lineMode,
                    read = message.read,
                    maxBubbleWidth = bubbleMaxWidth,
                )
            }
            if (streamingText.isNotBlank() || thinking) {
                item(key = "streaming", contentType = "message") {
                    ChatBubble(
                        "assistant",
                        streamingText.ifBlank { I18n.t("chat_thinking") },
                        thinking,
                        reasoning = streamingReasoning,
                        avatar = avatar,
                        userAvatar = userAvatar,
                        lineMode = lineMode,
                        maxBubbleWidth = bubbleMaxWidth,
                    )
                }
            }
        }
    }
}

internal fun shouldFollowNewChatContent(previousItemCount: Int, lastVisibleIndex: Int): Boolean =
    previousItemCount <= 0 || lastVisibleIndex < 0 || lastVisibleIndex >= previousItemCount - 2

@Composable
internal fun ChatBubble(
    role: String,
    content: String,
    thinking: Boolean = false,
    reasoning: String? = null,
    highlightQuery: String? = null,
    onReplay: (() -> Unit)? = null,
    timestamp: Long = 0L,
    images: List<String> = emptyList(),
    avatar: ImageBitmap? = null,
    userAvatar: ImageBitmap? = null,
    lineMode: Boolean = false,
    read: Boolean = false,
    maxBubbleWidth: Dp = 420.dp,
) {
    val fromUser = role == "user"
    var previewImage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        if (!reasoning.isNullOrBlank()) {
            ReasoningFold(reasoning, maxBubbleWidth)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!fromUser && lineMode) {
                LineAvatar(avatar, isUser = false)
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (fromUser) 20.dp else 6.dp,
                        bottomEnd = if (fromUser) 6.dp else 20.dp,
                    ),
                    color = if (fromUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (thinking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    I18n.t("chat_thinking"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val ranges = remember(content, highlightQuery) {
                            if (highlightQuery.isNullOrBlank()) {
                                emptyList()
                            } else {
                                ChatTextSearch.findHighlightRanges(content, highlightQuery)
                            }
                        }
                        SelectionContainer {
                            if (ranges.isEmpty()) {
                                Text(content, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                val annotated = buildAnnotatedString {
                                    var last = 0
                                    for (range in ranges.sortedBy { it.first }) {
                                        val start = range.first.coerceIn(0, content.length)
                                        val end = range.last.plus(1).coerceIn(start, content.length)
                                        if (start > last) append(content.substring(last, start))
                                        withStyle(
                                            SpanStyle(
                                                background = Color(0x66FFD54F),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        ) {
                                            append(content.substring(start, end))
                                        }
                                        last = end
                                    }
                                    if (last < content.length) append(content.substring(last))
                                }
                                Text(annotated, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (images.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                images.take(9).forEach { dataUrl ->
                                    MessageImageThumb(dataUrl = dataUrl, onClick = { previewImage = dataUrl })
                                }
                            }
                        }
                        if (lineMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (fromUser && read) {
                                    Text(
                                        I18n.t("chat_read_receipt"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    )
                                    if (timestamp > 0L) Spacer(Modifier.width(4.dp))
                                }
                                if (timestamp > 0L) {
                                    Text(
                                        formatMessageTime(timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                if (onReplay != null) {
                                    if (timestamp > 0L || read) Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = onReplay,
                                        modifier = Modifier.size(22.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.VolumeUp,
                                            contentDescription = I18n.t("chat_speak"),
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (!lineMode) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (fromUser && read) {
                            Text(
                                I18n.t("chat_read_receipt"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                            if (timestamp > 0L) Spacer(Modifier.width(6.dp))
                        }
                        if (timestamp > 0L) {
                            Text(
                                formatMessageTime(timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onReplay != null) {
                            if (timestamp > 0L || read) Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = onReplay,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.VolumeUp,
                                    contentDescription = I18n.t("chat_speak"),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (fromUser && lineMode && userAvatar != null) {
                Spacer(Modifier.width(8.dp))
                LineAvatar(userAvatar, isUser = true)
            }
        }
    }

    previewImage?.let { dataUrl ->
        MessageImagePreview(dataUrl = dataUrl, onDismiss = { previewImage = null })
    }
}

@Composable
private fun rememberChatAvatar(characterId: String?): ImageBitmap? {
    if (characterId == null) return null
    val context = LocalContext.current
    val appContext = context.applicationContext
    val customFile = remember(characterId) { AvatarManager.customAvatarFile(appContext, characterId) }
    val defaultPath = remember(characterId) { AvatarManager.defaultAvatarAssetPath(appContext, characterId) }
    val key = remember(characterId, customFile?.lastModified()) {
        if (customFile != null) {
            "chat-avatar:custom:$characterId:${customFile.lastModified()}"
        } else {
            "chat-avatar:default:$characterId"
        }
    }
    var avatar by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (avatar != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            when {
                customFile != null -> SampledImageDecoder.decodeBytes(customFile.readBytes(), 128)
                defaultPath != null -> SampledImageDecoder.decodeAsset(appContext, defaultPath, 128)
                else -> null
            }
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        avatar = decoded
    }
    return avatar
}

@Composable
private fun rememberUserAvatar(): ImageBitmap? {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val file = remember { AvatarManager.userAvatarFile(appContext) }
    val key = remember(file?.lastModified()) {
        if (file != null) "chat-avatar:user:${file.lastModified()}" else "chat-avatar:user:none"
    }
    var avatar by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (avatar != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val decoded = file?.let {
            withContext(Dispatchers.IO) { SampledImageDecoder.decodeBytes(it.readBytes(), 128) }
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        avatar = decoded
    }
    return avatar
}

@Composable
private fun LineAvatar(bitmap: ImageBitmap?, isUser: Boolean) {
    val background = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else if (isUser) {
            Text(
                "我",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "AI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageImageThumb(dataUrl: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        DecodedImage(dataUrl = dataUrl, maxEdge = 256, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun MessageImagePreview(dataUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            DecodedImage(
                dataUrl = dataUrl,
                maxEdge = 2048,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = I18n.t("close"), tint = Color.White)
            }
        }
    }
}

@Composable
private fun DecodedImage(
    dataUrl: String,
    maxEdge: Int,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val key = remember(dataUrl, maxEdge) { ImageBitmapCache.shortKey("chat-img:$maxEdge", dataUrl) }
    var bitmap by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val bytes = dataUrlToBytes(dataUrl)
        val decoded = bytes?.let {
            withContext(Dispatchers.IO) { SampledImageDecoder.decodeBytes(it, maxEdge) }
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        bitmap = decoded
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!, contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

private fun dataUrlToBytes(dataUrl: String): ByteArray? {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return null
    return runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.NO_WRAP) }.getOrNull()
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return messageTimeFormat.format(Date(timestamp))
}

/** 可折叠的深度思考（思维链）块。 */
@Composable
private fun ReasoningFold(reasoning: String, maxBubbleWidth: Dp = 420.dp) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.widthIn(max = maxBubbleWidth),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "深度思考",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Text(
                    reasoning,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
