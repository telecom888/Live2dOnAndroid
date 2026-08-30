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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bangdream.pet.data.Band
import com.bangdream.pet.data.CharacterInfo
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
    var status by remember { mutableStateOf<String?>(null) }
    val backgroundUri = remember { loadWallpaperBackgroundUri(appContext) }
    val renderSettings = remember { RenderSettings.load(appContext) }

    // 参数面板折叠
    var listExpanded by remember { mutableStateOf(true) }

    // 添加模型选择器（两级：角色 -> 模型）
    var showPicker by remember { mutableStateOf(false) }
    var pickerCharacters by remember { mutableStateOf<List<Pair<Band, CharacterInfo>>>(emptyList()) }
    var pickerCharacter by remember { mutableStateOf<CharacterInfo?>(null) }
    var pickerModels by remember { mutableStateOf<List<ModelChoice>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }

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

            // 模型参数面板（半透明 + 可折叠）
            if (models.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appLiquidGlass(hazeState, enabled = glassEnabled, backgroundAlpha = 0.42f),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "模型参数（${models.count { it.enabled }}/${models.size}）",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (listExpanded) "折叠" else "展开",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            IconButton(onClick = { listExpanded = !listExpanded }) {
                                Icon(
                                    if (listExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (listExpanded) "折叠" else "展开",
                                )
                            }
                        }
                        if (listExpanded) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
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
                }
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().appPressScale().appEntrance(delayMillis = 44),
                onClick = {
                    scope.launch {
                        val repo = DataRepository(appContext)
                        val data = repo.load()
                        val pairs = mutableListOf<Pair<Band, CharacterInfo>>()
                        for (band in data.bands) {
                            for (cid in band.characters) {
                                data.characters[cid]?.let { ch -> pairs.add(band to ch) }
                            }
                        }
                        pickerCharacters = pairs.distinctBy { it.second.id }
                        pickerCharacter = null
                        pickerModels = emptyList()
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
            characters = pickerCharacters,
            selectedCharacter = pickerCharacter,
            models = pickerModels,
            loading = pickerLoading,
            onPickCharacter = { character ->
                pickerCharacter = character
                pickerLoading = true
                scope.launch {
                    pickerModels = withContext(Dispatchers.IO) {
                        DataRepository(appContext).availableModels(character)
                    }
                    pickerLoading = false
                }
            },
            onBackToCharacters = {
                pickerCharacter = null
                pickerModels = emptyList()
            },
            onPickModel = { choice ->
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
            onDismiss = { showPicker = false },
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    characters: List<Pair<Band, CharacterInfo>>,
    selectedCharacter: CharacterInfo?,
    models: List<ModelChoice>,
    loading: Boolean,
    onPickCharacter: (CharacterInfo) -> Unit,
    onBackToCharacters: () -> Unit,
    onPickModel: (ModelChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (selectedCharacter == null) {
                Text("选择角色")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToCharacters) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                    Text("选择 ${selectedCharacter.display} 的模型", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            if (selectedCharacter == null) {
                if (characters.isEmpty()) {
                    Text("没有可用角色。")
                } else {
                    LazyColumn(Modifier.height(380.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(characters, key = { it.second.id }) { (band, character) ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onPickCharacter(character) },
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(character.display, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        band.display,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                    models.isEmpty() -> Text("该角色暂无可用模型，请先在模型页下载。")
                    else -> LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(models, key = { it.modelAssetPath }) { choice ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onPickModel(choice) },
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(choice.costumeName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        choice.costumeId,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedCharacter == null) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            } else {
                TextButton(onClick = onBackToCharacters) { Text("返回") }
            }
        },
    )
}
