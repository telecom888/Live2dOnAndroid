package com.bangdream.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bangdream.pet.data.DataRepository
import com.bangdream.pet.data.ModelChoice
import com.bangdream.pet.live2d.MultiLive2DRenderView
import com.bangdream.pet.ui.design.VisualGuard
import com.bangdream.pet.ui.design.appEntrance
import com.bangdream.pet.ui.design.appHazeSource
import com.bangdream.pet.ui.design.appLiquidGlass
import com.bangdream.pet.ui.design.appPressScale
import com.bangdream.pet.ui.design.rememberLiquidGlassState
import com.bangdream.pet.ui.live2d.ContentUriImage
import com.bangdream.pet.ui.theme.BangDreamPetTheme
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
    var status by remember { mutableStateOf<String?>(null) }
    val backgroundUri = remember { loadWallpaperBackgroundUri(appContext) }
    val renderSettings = remember { RenderSettings.load(appContext) }

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
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶栏
            ElevatedCard(
                Modifier
                    .fillMaxWidth()
                    .appEntrance()
                    .appLiquidGlass(hazeState, enabled = glassEnabled),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("桌面模型管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "预览中可直接拖动模型调整位置、双指缩放大小；下方列表可添加/移除/启停。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 实时预览
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Box(Modifier.fillMaxSize()) {
                    ContentUriImage(
                        uri = backgroundUri,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            MultiLive2DRenderView(viewContext).apply {
                                statusChanged = { status = it }
                                placementsChanged = { list -> models = list }
                                setRenderOptions(renderSettings.fpsLimit, renderSettings.vsyncEnabled)
                                setRenderResolution(renderSettings.renderResolution)
                                setPlacements(models)
                            }
                        },
                        update = { view ->
                            view.statusChanged = { status = it }
                            view.placementsChanged = { list -> models = list }
                            view.setRenderOptions(renderSettings.fpsLimit, renderSettings.vsyncEnabled)
                            view.setRenderResolution(renderSettings.renderResolution)
                            view.setPlacements(models)
                        },
                        onRelease = MultiLive2DRenderView::release,
                    )
                    status?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                        ) {
                            Text(text = message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                        }
                    }
                }
            }

            // 模型列表
            if (models.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .appLiquidGlass(hazeState, enabled = glassEnabled),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(models, key = { it.id }) { placement ->
                            val index = models.indexOfFirst { it.id == placement.id }
                            PlacementRow(
                                placement = placement,
                                title = if (placement.characterName.isBlank()) placement.characterId else "${placement.characterName} / ${placement.costumeName}",
                                modifier = Modifier.appEntrance(delayMillis = (index * 22).coerceAtMost(160)),
                                onChanged = { updated -> models = models.mapIndexed { i, item -> if (i == index) updated else item } },
                                onRemove = { models = models.filterNot { it.id == placement.id } },
                            )
                        }
                    }
                }
            }

            // 添加 + 操作
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
            ) { Text(if (models.isEmpty()) "还没有桌面模型，点击添加" else "添加模型") }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(modifier = Modifier.weight(1f).appPressScale(), onClick = onClose) { Text("取消") }
                Button(modifier = Modifier.weight(1f).appPressScale(), onClick = { saveAndClose() }) { Text("保存") }
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
private fun PlacementRow(
    placement: WallpaperModelPlacement,
    title: String,
    modifier: Modifier = Modifier,
    onChanged: (WallpaperModelPlacement) -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (placement.enabled) "显示中" else "已隐藏",
                        color = if (placement.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(checked = placement.enabled, onCheckedChange = { onChanged(placement.copy(enabled = it)) })
                TextButton(modifier = Modifier.appPressScale(), onClick = onRemove) { Text("移除") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("大小", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
                Slider(
                    modifier = Modifier.weight(1f),
                    value = placement.scale,
                    onValueChange = { onChanged(placement.copy(scale = it)) },
                    valueRange = 0.4f..2.5f,
                )
                Text("%.2f".format(placement.scale), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("重置", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
                TextButton(
                    onClick = { onChanged(placement.copy(offsetX = 0f, offsetY = 0f, scale = 1f)) },
                ) { Text("恢复居中") }
                Spacer(Modifier.weight(1f))
            }
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
