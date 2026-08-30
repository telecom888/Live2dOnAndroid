package com.bandori.pet.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bandori.pet.loadBubbleEnabled
import com.bandori.pet.loadPersistedModelChoice
import com.bandori.pet.live2d.NativeLive2D
import com.bandori.pet.voice.VoicePlayer
import com.bandori.pet.wallpaper.Live2DWallpaperService
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 桌面双击弹出的小型对话输入窗（透明 Activity + 底部面板）。 */
class WallpaperChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallpaperChatPanel(onDismiss = { finish() })
        }
    }

    companion object {
        fun open(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, WallpaperChatActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

@Composable
private fun WallpaperChatPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val model = remember { loadPersistedModelChoice(appContext) }

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // 说话口型驱动
    LaunchedEffect(busy) {
        while (isActive) {
            if (VoicePlayer.isPlaying()) {
                val t = System.currentTimeMillis() / 90.0
                val open = (0.2 + 0.6 * abs(sin(t))).toFloat()
                NativeLive2D.setLipSync(Live2DWallpaperService.activeHandle, open, 1f)
            }
            delay(90)
        }
        NativeLive2D.setLipSync(Live2DWallpaperService.activeHandle, 0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(enabled = !busy) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model?.characterName ?: "未选择模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }
                if (model == null) {
                    Text("请先在应用内选择 Live2D 模型。")
                } else {
                    if (output.isNotBlank()) {
                        Text(
                            output,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(scrollState),
                        )
                    }
                    if (status.isNotBlank()) {
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("想对我说什么…") },
                            maxLines = 3,
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.width(28.dp).height(28.dp), strokeWidth = 3.dp)
                        } else {
                            IconButton(
                                onClick = {
                                    val text = input.trim()
                                    if (text.isNotEmpty()) {
                                        input = ""
                                        busy = true
                                        output = ""
                                        status = "思考中…"
                                        PetRuntime.onStopped = {
                                            status = "已停止"
                                            busy = false
                                        }
                                        PetRuntime.activeJob = scope.launch {
                                            val engine = WallpaperChatEngine(appContext)
                                            val result = engine.send(model, text) { streaming ->
                                                output = streaming
                                            }
                                            if (result.error != null) {
                                                status = result.error
                                            } else {
                                                output = result.text
                                                status = if (result.ttsSpoken) "已回复（语音）" else "已回复"
                                            }
                                            if (result.actionTag != null) {
                                                NativeLive2D.playAction(Live2DWallpaperService.activeHandle, result.actionTag)
                                            }
                                            if (loadBubbleEnabled(appContext)) {
                                                WallpaperBubbleService.show(appContext, result.text)
                                            }
                                            busy = false
                                            delay(1600)
                                            onDismiss()
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Send, contentDescription = "发送")
                            }
                        }
                    }
                }
            }
        }
    }
}

