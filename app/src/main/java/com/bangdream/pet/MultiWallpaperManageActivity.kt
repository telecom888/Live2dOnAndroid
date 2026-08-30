package com.bangdream.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.ui.design.VisualGuard
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.ui.design.appHazeSource
import com.bangdream.pet.ui.design.appLiquidGlass
import com.bangdream.pet.ui.design.appPressScale
import com.bangdream.pet.ui.design.rememberLiquidGlassState
import com.bangdream.pet.ui.theme.BangDreamPetTheme
import com.bangdream.pet.wallpaper.WallpaperHitArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiWallpaperManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(applicationContext)
        setContent {
            val themeSettings = remember { ThemeSettings.load(applicationContext) }
            BangDreamPetTheme(
                darkTheme = themeSettings.darkMode.resolveDarkTheme(isSystemInDarkTheme()),
                dynamicColor = themeSettings.dynamicColorEnabled,
            ) {
                MultiWallpaperManageScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun MultiWallpaperManageScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val hazeState = rememberLiquidGlassState()
    val glassEnabled = ThemeSettings.load(appContext).liquidGlassEnabled && VisualGuard.supportsLiquidGlass(appContext)
    var models by remember { mutableStateOf(WallpaperMultiModelSettings.load(appContext).models) }
    var showPicker by remember { mutableStateOf(false) }
    var availableChoices by remember { mutableStateOf<List<ModelChoice>>(emptyList()) }
    fun saveAndClose() {
        WallpaperMultiModelSettings(models).save(appContext)
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appHazeSource(hazeState)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .appEntrance()
                        .appLiquidGlass(hazeState, enabled = glassEnabled),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("桌面模型管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "开启「多模型」模式后，这里摆放的角色会同时显示在桌面上。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (models.isNotEmpty()) {
                item {
                    SchematicPreview(models)
                }
            }
            item {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth().appPressScale().appEntrance(delayMillis = 44),
                    onClick = {
                        scope.launch {
                            availableChoices = withContext(Dispatchers.IO) {
                                val repo = DataRepository(appContext)
                                val data = repo.load()
                                data.bands.flatMap { band ->
                                    band.characters.flatMap { cid ->
                                        data.characters[cid]?.let { ch -> repo.availableModels(ch) } ?: emptyList()
                                    }
                                }.distinctBy { it.modelAssetPath }
                            }
                            showPicker = true
                        }
                    },
                ) { Text("添加模型") }
            }
            if (models.isEmpty()) {
                item {
                    Text(
                        "还没有桌面模型。点击「添加模型」选择角色与服装。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(models, key = { it.id }) { placement ->
                val index = models.indexOfFirst { it.id == placement.id }
                PlacementCard(
                    modifier = Modifier.appEntrance(delayMillis = (index * 22).coerceAtMost(160)),
                    placement = placement,
                    title = if (placement.characterName.isBlank()) placement.characterId else "${placement.characterName} / ${placement.costumeName}",
                    onChanged = { updated ->
                        models = models.mapIndexed { i, item -> if (i == index) updated else item }
                    },
                    onRemove = {
                        models = models.filterNot { it.id == placement.id }
                    },
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(modifier = Modifier.weight(1f).appPressScale(), onClick = onClose) { Text("取消") }
                    Button(modifier = Modifier.weight(1f).appPressScale(), onClick = { saveAndClose() }) { Text("保存") }
                }
            }
        }
    }

    if (showPicker) {
        ModelPickerDialog(
            choices = availableChoices,
            onDismiss = { showPicker = false },
            onPick = { choice ->
                models = models + WallpaperModelPlacement(
                    id = "m_${System.currentTimeMillis()}",
                    characterId = choice.characterId,
                    characterName = choice.characterName,
                    costumeId = choice.costumeId,
                    costumeName = choice.costumeName,
                    modelAssetPath = choice.modelAssetPath,
                    offsetX = (models.size % 3 - 1) * 0.25f,
                    offsetY = (models.size % 3 - 1) * 0.18f,
                    scale = 0.9f,
                )
                showPicker = false
            },
        )
    }
}

@Composable
private fun SchematicPreview(models: List<WallpaperModelPlacement>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("摆放示意图", fontWeight = FontWeight.SemiBold)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1720)),
            ) {
                val scaleX = maxWidth.value / 1080f
                val scaleY = maxHeight.value / 2400f
                models.filter { it.enabled }.forEach { placement ->
                    val rect = WallpaperHitArea.computeRect(
                        surfaceWidth = 1080,
                        surfaceHeight = 2400,
                        transform = placement.toTransform(),
                        canvas = null,
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (rect.left * scaleX).dp, y = (rect.top * scaleY).dp)
                            .size(width = (rect.width() * scaleX).dp, height = (rect.height() * scaleY).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (if (placement.characterName.isBlank()) placement.characterId else placement.characterName).take(2),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                "预览为示意图（位置/大小近似），实际以桌面渲染为准。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlacementCard(
    modifier: Modifier = Modifier,
    placement: WallpaperModelPlacement,
    title: String,
    onChanged: (WallpaperModelPlacement) -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (placement.enabled) "显示中" else "已隐藏",
                        color = if (placement.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = placement.enabled, onCheckedChange = { onChanged(placement.copy(enabled = it)) })
                TextButton(modifier = Modifier.appPressScale(), onClick = onRemove) { Text("移除") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("左右", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    modifier = Modifier.weight(1f),
                    value = placement.offsetX,
                    onValueChange = { onChanged(placement.copy(offsetX = it)) },
                    valueRange = -1.2f..1.2f,
                )
                Text("%.2f".format(placement.offsetX), modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("上下", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    modifier = Modifier.weight(1f),
                    value = placement.offsetY,
                    onValueChange = { onChanged(placement.copy(offsetY = it)) },
                    valueRange = -1.2f..1.2f,
                )
                Text("%.2f".format(placement.offsetY), modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("大小", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    modifier = Modifier.weight(1f),
                    value = placement.scale,
                    onValueChange = { onChanged(placement.copy(scale = it)) },
                    valueRange = 0.4f..2.5f,
                )
                Text("%.2f".format(placement.scale), modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { onChanged(placement.copy(offsetX = 0f, offsetY = 0f, scale = 1f)) },
            ) { Text("重置位置") }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    choices: List<ModelChoice>,
    onDismiss: () -> Unit,
    onPick: (ModelChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择桌面模型") },
        text = {
            if (choices.isEmpty()) {
                Text("没有可用模型，请先在模型页下载/选择角色。")
            } else {
                LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(choices, key = { it.modelAssetPath }) { choice ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(choice) },
                        ) {
                            Text(
                                "${choice.characterName} / ${choice.costumeName}",
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
