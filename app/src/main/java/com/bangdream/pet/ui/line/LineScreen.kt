package com.bangdream.pet.ui.line

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bangdream.pet.AvatarManager
import com.bangdream.pet.data.AppData
import com.bangdream.pet.data.CharacterInfo
import com.bangdream.pet.line.LineConversation
import com.bangdream.pet.line.LineConversationStatus
import com.bangdream.pet.line.LineConversationType
import com.bangdream.pet.line.LineMessage
import com.bangdream.pet.line.LineOrchestrator
import com.bangdream.pet.line.LineSceneRepository
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.ui.design.appPressScale
import com.bangdream.pet.ui.design.emphasizedTween
import com.bangdream.pet.ui.design.expressiveTween
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Line 旁观者：选角色 Line 账号 → 该角色的对话列表 → 旁观详情（先设主题再开始讨论）。 */
@Composable
fun LineScreen(appData: AppData?, modifier: Modifier = Modifier) {
    val characters = remember(appData) {
        appData?.characters?.values?.sortedBy { it.display }.orEmpty()
    }
    var selectedRole by remember { mutableStateOf<CharacterInfo?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    val level = when {
        openConversationId != null -> 2
        selectedRole != null -> 1
        else -> 0
    }
    BackHandler(enabled = level != 0) {
        when (level) {
            2 -> openConversationId = null
            else -> selectedRole = null
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
        label = "lineLevel",
    ) { target ->
        when (target) {
            2 -> {
                val id = openConversationId ?: return@AnimatedContent
                LineConversationDetailScreen(
                    conversationId = id,
                    onBack = { openConversationId = null },
                )
            }
            1 -> {
                val role = selectedRole ?: return@AnimatedContent
                LineAccountScreen(
                    role = role,
                    characters = characters,
                    onBack = { selectedRole = null },
                    onOpen = { openConversationId = it },
                )
            }
            else -> LineRolePickerScreen(
                characters = characters,
                onSelectRole = { selectedRole = it },
            )
        }
    }
}

@Composable
private fun LineRolePickerScreen(
    characters: List<CharacterInfo>,
    onSelectRole: (CharacterInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Line", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "（选择观看的角色账号）",
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
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
                        onClick = { onSelectRole(character) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .appEntrance(delayMillis = (characters.indexOfFirst { it.id == character.id } * 22).coerceAtMost(160))
                            .appPressScale(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LineRoleAvatar(character.id, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(character.display, fontWeight = FontWeight.Medium)
                                Text(
                                    "${character.costumes.size} 套素材",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineAccountScreen(
    role: CharacterInfo,
    characters: List<CharacterInfo>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { LineSceneRepository(appContext) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LineConversation?>(null) }
    val conversations = remember(role.id, refresh) { repository.listForRole(role.id) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "${role.display} 的 Line",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.AddComment, contentDescription = "新建")
            }
        }
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无对话，点右上角 + 新建",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    Surface(
                        onClick = { onOpen(conv.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .appEntrance(delayMillis = (conversations.indexOfFirst { it.id == conv.id } * 22).coerceAtMost(160))
                            .appPressScale(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conv.title.ifBlank { "未命名" },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (conv.topic.isBlank()) "尚未设置主题" else "主题：${conv.topic}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${conv.type.name.replace("_", " ")} · ${if (conv.status == LineConversationStatus.ENDED) "已截止" else "进行中"} · ${conv.messages.size} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { pendingDelete = conv }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateLineDialog(
            ownerRole = role,
            characters = characters,
            onDismiss = { showCreate = false },
            onCreate = { type, title, members ->
                val conversation = LineConversation(
                    id = LineSceneRepository.newId(),
                    type = type,
                    title = title,
                    topic = "",
                    memberRoleIds = members,
                    status = LineConversationStatus.ACTIVE,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                repository.save(conversation)
                showCreate = false
                refresh++
                onOpen(conversation.id)
            },
        )
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除对话") },
            text = { Text("确定删除「${conv.title.ifBlank { "未命名" }}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(conv.id)
                    pendingDelete = null
                    refresh++
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CreateLineDialog(
    ownerRole: CharacterInfo,
    characters: List<CharacterInfo>,
    onDismiss: () -> Unit,
    onCreate: (LineConversationType, String, List<String>) -> Unit,
) {
    val context = LocalContext.current
    var isGroup by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var otherId by remember { mutableStateOf<String?>(null) }
    var groupMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    val others = remember(characters) { characters.filter { it.id != ownerRole.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "新建群组" else "新建私聊") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isGroup, onClick = { isGroup = false }, label = { Text("私聊") })
                    FilterChip(selected = isGroup, onClick = { isGroup = true }, label = { Text("群组") })
                }
                if (isGroup) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("群名（可选）") },
                        singleLine = true,
                    )
                    Text("选择参与角色（含 ${ownerRole.display}），可滑动", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(others, key = { it.id }) { c ->
                            val selected = c.id in groupMembers
                            Surface(
                                onClick = {
                                    groupMembers = if (selected) groupMembers - c.id else groupMembers + c.id
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.display, modifier = Modifier.weight(1f))
                                    if (selected) {
                                        Text(
                                            "已选",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("选择私聊对象，可滑动", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(others, key = { it.id }) { c ->
                            val selected = otherId == c.id
                            Surface(
                                onClick = { otherId = c.id },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.display, modifier = Modifier.weight(1f))
                                    if (selected) {
                                        Text(
                                            "已选",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "创建后进入对话，先设置主题再开始讨论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val members = if (isGroup) {
                    if (groupMembers.isEmpty()) {
                        Toast.makeText(context, "请至少选择一位群成员", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    (groupMembers + ownerRole.id).toList()
                } else {
                    val other = otherId
                    if (other == null) {
                        Toast.makeText(context, "请选择私聊对象", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    listOf(ownerRole.id, other)
                }
                val convTitle = if (isGroup) {
                    title.trim().ifBlank { "新群组" }
                } else {
                    characters.firstOrNull { it.id == members.firstOrNull { it != ownerRole.id } }?.display.orEmpty()
                }
                onCreate(
                    if (isGroup) LineConversationType.GROUP else LineConversationType.ONE_TO_ONE,
                    convTitle,
                    members,
                )
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LineConversationDetailScreen(
    conversationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { LineSceneRepository(appContext) }
    val engine = remember { LineOrchestrator(appContext) }
    val scope = rememberCoroutineScope()
    val roleNames = remember {
        com.bangdream.pet.data.DataRepository(appContext).load().characters.mapValues { it.value.display }
    }
    var conversation by remember { mutableStateOf(repository.get(conversationId)) }
    var running by remember { mutableStateOf(false) }
    var showTopicDialog by remember { mutableStateOf(false) }
    var topicDraft by remember { mutableStateOf("") }

    val conv = conversation
    if (conv == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("对话不存在") }
        return
    }

    fun startDiscussion() {
        running = true
        scope.launch {
            val updated = engine.runConversation(conversation!!) { latest ->
                conversation = latest
                repository.save(latest)
            }
            conversation = updated
            repository.save(updated)
            running = false
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(conv.title.ifBlank { "未命名" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (conv.topic.isBlank()) "尚未设置主题" else "主题：${conv.topic}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = {
                            topicDraft = conv.topic
                            showTopicDialog = true
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "设置主题", modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (conv.status == LineConversationStatus.ACTIVE && !running) {
                FilledTonalButton(onClick = {
                    if (conv.topic.isBlank()) {
                        topicDraft = ""
                        showTopicDialog = true
                    } else {
                        startDiscussion()
                    }
                }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("开始讨论")
                }
            } else if (conv.status == LineConversationStatus.ACTIVE) {
                IconButton(onClick = {
                    conversation = conv.copy(status = LineConversationStatus.ENDED)
                    repository.save(conversation!!)
                    running = false
                }) {
                    Icon(Icons.Outlined.Stop, contentDescription = "截止")
                }
            } else {
                Text("已截止", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(conv.messages, key = { it.id }) { message ->
                LineMessageRow(message = message, roleNames = roleNames)
            }
            if (conv.messages.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "设置主题后点“开始讨论”，角色们将围绕主题展开对话，直到结束标记。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (showTopicDialog) {
        AlertDialog(
            onDismissRequest = { showTopicDialog = false },
            title = { Text("设置讨论主题") },
            text = {
                OutlinedTextField(
                    value = topicDraft,
                    onValueChange = { topicDraft = it },
                    label = { Text("主题") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val topic = topicDraft.trim()
                    if (topic.isEmpty()) {
                        Toast.makeText(context, "请输入主题", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val updated = conversation!!.copy(topic = topic)
                    conversation = updated
                    repository.save(updated)
                    showTopicDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showTopicDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LineMessageRow(message: LineMessage, roleNames: Map<String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        LineRoleAvatar(message.fromRoleId, size = 38.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                roleNames[message.fromRoleId] ?: message.fromRoleId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f),
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val readCount = message.readBy.count { it != message.fromRoleId }
                        Text(
                            if (readCount > 0) "已读 $readCount" else "未读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRoleAvatar(roleId: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val customFile = remember(roleId) { AvatarManager.customAvatarFile(appContext, roleId) }
    val defaultPath = remember(roleId) { AvatarManager.defaultAvatarAssetPath(appContext, roleId) }
    val key = remember(roleId, customFile?.lastModified()) {
        if (customFile != null) "line-avatar:custom:$roleId:${customFile.lastModified()}" else "line-avatar:default:$roleId"
    }
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            when {
                customFile != null -> SampledImageDecoder.decodeBytes(customFile.readBytes(), 128)
                defaultPath != null -> SampledImageDecoder.decodeAsset(appContext, defaultPath, 128)
                else -> null
            }
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        bitmap = decoded
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Text("无", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
