package com.bangdream.pet.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.loadCharacterCustomPrompt
import com.bangdream.pet.loadCharacterMemory
import com.bangdream.pet.clearCharacterMemory
import com.bangdream.pet.saveCharacterCustomPrompt
import com.bangdream.pet.AvatarManager
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import com.bangdream.pet.llm.CharacterPromptRepository
import com.bangdream.pet.voice.VoiceSampleInfo
import com.bangdream.pet.voice.VoiceSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 角色设定页：系统提示词（预填内置人物设定，可编辑/载入默认/保存）+
 * 音色样本（导入/选择/删除），与人物设定提示词放在一起。
 */
@Composable
fun CharacterSettingsScreen(
    character: ModelChoice,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var promptDraft by remember(character.characterId) {
        mutableStateOf(loadCharacterCustomPrompt(appContext, character.characterId).orEmpty())
    }
    var memoryText by remember(character.characterId) {
        mutableStateOf(loadCharacterMemory(appContext, character.characterId))
    }
    var samples by remember(character.characterId) { mutableStateOf(emptyList<VoiceSampleInfo>()) }
    var avatarTick by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = AvatarManager.importAvatar(appContext, character.characterId, uri)
            Toast.makeText(
                appContext,
                if (ok) "Line 头像已设置" else "头像导入失败",
                Toast.LENGTH_SHORT,
            ).show()
            avatarTick++
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = VoiceSamples.importSample(appContext, character.characterId, uri)
            Toast.makeText(
                appContext,
                if (ok) "已导入并设为当前音色" else "导入失败（仅支持 mp3/wav，≤10MB）",
                Toast.LENGTH_SHORT,
            ).show()
            refreshTick++
        }
    }

    LaunchedEffect(character.characterId, refreshTick) {
        samples = VoiceSamples.listSamples(appContext, character.characterId)
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
                "角色设定 · ${character.characterName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("人物设定提示词（系统提示词）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "留空使用内置角色设定；填写后作为该角色每次对话的系统提示词。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = promptDraft,
                onValueChange = { promptDraft = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("系统提示词") },
                minLines = 8,
                maxLines = 18,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            val default = withContext(Dispatchers.IO) {
                                CharacterPromptRepository(appContext).buildSystemPrompt(character).text
                            }
                            promptDraft = default
                            saved = false
                        }
                    },
                ) { Text("载入默认设定") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        saveCharacterCustomPrompt(appContext, character.characterId, promptDraft)
                        saved = true
                        Toast.makeText(appContext, "人物设定已保存", Toast.LENGTH_SHORT).show()
                    },
                ) { Text(if (saved) "已保存" else "保存") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("角色记忆", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (memoryText.isNotBlank()) {
                    TextButton(onClick = {
                        clearCharacterMemory(appContext, character.characterId)
                        memoryText = ""
                        Toast.makeText(appContext, "已清空角色记忆", Toast.LENGTH_SHORT).show()
                    }) { Text("清空") }
                }
            }
            if (memoryText.isBlank()) {
                Text(
                    "暂无记忆。在对话详情中长按消息气泡可「载入记忆」。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        memoryText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Line 头像", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (AvatarManager.customAvatarFile(appContext, character.characterId) != null) {
                    TextButton(onClick = {
                        AvatarManager.clearAvatar(appContext, character.characterId)
                        avatarTick++
                        Toast.makeText(appContext, "已恢复默认头像", Toast.LENGTH_SHORT).show()
                    }) { Text("恢复默认") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineAvatarPreview(character.characterId, avatarTick)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "仅 Line UI 模式显示；未设置时使用内置角色头像",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "支持 jpg/png，选择后立即生效",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalButton(onClick = { avatarLauncher.launch("image/*") }) { Text("选择头像") }
            }
            Spacer(Modifier.height(8.dp))
            Text("语音样本（克隆音色）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (samples.isEmpty()) {
                Text("暂无样本，导入一个音频作为克隆音色", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                samples.forEach { sample ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                    VoiceSamples.setActiveSample(appContext, character.characterId, sample.file.name)
                                    refreshTick++
                                }) { Text("选择") }
                            }
                            TextButton(onClick = {
                                VoiceSamples.deleteSample(appContext, character.characterId, sample.file.name)
                                refreshTick++
                            }) { Text("删除") }
                        }
                    }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, com.bangdream.pet.voice.VoiceListActivity::class.java)
                            .putExtra(com.bangdream.pet.voice.VoiceListActivity.EXTRA_CHARACTER_ID, character.characterId)
                            .putExtra(com.bangdream.pet.voice.VoiceListActivity.EXTRA_CHARACTER_NAME, character.characterName)
                    )
                },
            ) { Text("内置语音列表（试听）") }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { importLauncher.launch("audio/*") },
            ) { Text("导入音色样本") }
            Text(
                "支持 mp3/wav，≤10MB；一个角色可存多个样本，选择其中一个作为当前克隆音色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LineAvatarPreview(characterId: String, tick: Int, size: Dp = 48.dp) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val customFile = remember(characterId, tick) { AvatarManager.customAvatarFile(appContext, characterId) }
    val defaultPath = remember(characterId, tick) { AvatarManager.defaultAvatarAssetPath(appContext, characterId) }
    val key = remember(characterId, tick, customFile?.lastModified()) {
        if (customFile != null) {
            "avatar-preview:custom:$characterId:${customFile.lastModified()}"
        } else {
            "avatar-preview:default:$characterId"
        }
    }
    var bitmap by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
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
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("无", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
