package com.bangdream.pet.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.loadCharacterCustomPrompt
import com.bangdream.pet.saveCharacterCustomPrompt
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
    topInset: Dp = 0.dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var promptDraft by remember(character.characterId) {
        mutableStateOf(loadCharacterCustomPrompt(appContext, character.characterId).orEmpty())
    }
    var samples by remember(character.characterId) { mutableStateOf(emptyList<VoiceSampleInfo>()) }
    var refreshTick by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }

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

    Column(modifier.fillMaxSize().padding(top = topInset)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
