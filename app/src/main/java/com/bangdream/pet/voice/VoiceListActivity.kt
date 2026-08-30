package com.bangdream.pet.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangdream.pet.BuiltinVoiceLanguage
import com.bangdream.pet.I18n
import com.bangdream.pet.ThemeSettings
import com.bangdream.pet.loadBuiltinVoiceLanguage
import com.bangdream.pet.resolveDarkTheme
import com.bangdream.pet.ui.design.VisualGuard
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.ui.design.appHazeSource
import com.bangdream.pet.ui.design.appLiquidGlass
import com.bangdream.pet.ui.design.appPressScale
import com.bangdream.pet.ui.design.rememberLiquidGlassState
import com.bangdream.pet.ui.theme.BangDreamPetTheme

/**
 * 内置语音列表：展示台词文本（显示文本保留汉字/乐队名），点击条目手动播放对应音频。
 */
class VoiceListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(applicationContext)
        val characterId = intent.getStringExtra(EXTRA_CHARACTER_ID) ?: "tomori"
        val characterName = intent.getStringExtra(EXTRA_CHARACTER_NAME) ?: characterId
        setContent {
            val themeSettings = remember { ThemeSettings.load(applicationContext) }
            BangDreamPetTheme(
                darkTheme = themeSettings.darkMode.resolveDarkTheme(isSystemInDarkTheme()),
                dynamicColor = themeSettings.dynamicColorEnabled,
            ) {
                VoiceListScreen(characterId = characterId, characterName = characterName, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "character_id"
        const val EXTRA_CHARACTER_NAME = "character_name"
    }
}

@Composable
private fun VoiceListScreen(
    characterId: String,
    characterName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val hazeState = rememberLiquidGlassState()
    val glassEnabled = ThemeSettings.load(appContext).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(appContext)
    var language by remember { mutableStateOf(loadBuiltinVoiceLanguage(appContext)) }
    val lines = remember(characterId, language) {
        BuiltinVoiceManager.loadLines(appContext, characterId, language)
    }
    var playingIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .appHazeSource(hazeState)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .appLiquidGlass(hazeState, enabled = glassEnabled)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "内置语音 · $characterName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "共 ${lines.count { it.hasWav(appContext) }} 条预生成语音",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            BuiltinVoiceLanguage.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = language == option,
                    onClick = { language = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BuiltinVoiceLanguage.entries.size),
                ) { Text(option.label) }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(lines, key = { _, item -> item.index }) { index, line ->
                val hasAudio = line.hasWav(appContext)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appEntrance(delayMillis = (index * 22).coerceAtMost(160)),
                    shape = MaterialTheme.shapes.medium,
                    color = if (playingIndex == line.index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.display,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (hasAudio) "动作：${line.motion}" else "无预生成音频",
                                color = if (hasAudio) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (hasAudio) {
                            IconButton(
                                modifier = Modifier.appPressScale(),
                                onClick = {
                                    val bytes = line.readWav(appContext) ?: return@IconButton
                                    playingIndex = line.index
                                    VoicePlayer.play(appContext, bytes)
                                },
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "播放")
                            }
                        }
                    }
                }
            }
        }
    }
}
