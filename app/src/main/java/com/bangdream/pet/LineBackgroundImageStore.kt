package com.bangdream.pet

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies a picked image into app-owned storage so it remains available after the picker grant ends. */
object LineBackgroundImageStore {
    private const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L

    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = directory(context)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create background directory" }
        val target = File(directory, "background_${UUID.randomUUID()}.img")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= MAX_IMAGE_BYTES) { "Image is larger than 20 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Cannot open selected image")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
            target.absolutePath
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun deleteOwned(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path).canonicalFile
            if (file.parentFile == directory(context).canonicalFile) file.delete()
        }
    }

    private fun directory(context: Context): File = File(context.filesDir, "line_chat_background")
}
