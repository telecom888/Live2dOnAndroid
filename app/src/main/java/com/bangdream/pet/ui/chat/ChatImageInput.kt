package com.bangdream.pet.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** 用户选择的待发送图片：uri 用于预览，dataUrl（JPEG base64）用于请求。 */
data class PickedImage(val uri: Uri, val dataUrl: String)

private const val IMAGE_MAX_EDGE = 1600
private const val IMAGE_JPEG_QUALITY = 82

/** 把 content Uri 压缩为 JPEG base64 data URL（IO 线程）。失败返回 null。 */
suspend fun contentUriToImageDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val sourceEdge = max(bounds.outWidth, bounds.outHeight)
        while (sourceEdge > 0 && sourceEdge / (sample * 2) >= IMAGE_MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null
        val scaled = scaleToMaxEdge(bitmap, IMAGE_MAX_EDGE)
        val output = java.io.ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, output)
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        if (!ok) return@runCatching null
        val b64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }.getOrNull()
}

private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val edge = max(bitmap.width, bitmap.height)
    if (edge <= maxEdge) return bitmap
    val scale = maxEdge.toFloat() / edge.toFloat()
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

/** 已选图片缩略图，右上角可删除。 */
@Composable
fun PickedImageThumb(uri: Uri, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val key = "picked-thumb:$uri"
    var bitmap by remember(key) { mutableStateOf(ImageBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap != null || ImageBitmapCache.isKnownMissing(key)) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            SampledImageDecoder.decodeContentUri(appContext, uri, 256)
        }
        if (decoded == null) ImageBitmapCache.markMissing(key) else ImageBitmapCache.put(key, decoded)
        bitmap = decoded
    }
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "移除图片", modifier = Modifier.size(14.dp))
        }
    }
}
