package com.bangdream.pet.ui.chat

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bangdream.pet.ui.ImageBitmapCache
import com.bangdream.pet.ui.SampledImageDecoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LineChatBackground(path: String?, modifier: Modifier = Modifier) {
    if (path.isNullOrBlank()) return
    val context = LocalContext.current.applicationContext
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(ImageBitmapCache[path]) }
    LaunchedEffect(path) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                val file = File(path)
                if (file.isFile) SampledImageDecoder.decodeContentUri(context, Uri.fromFile(file), 1600)
                else null
            }
            bitmap?.let { ImageBitmapCache.put(path, it) }
        }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}
