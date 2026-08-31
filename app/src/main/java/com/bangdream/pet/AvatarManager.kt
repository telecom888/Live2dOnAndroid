package com.bangdream.pet

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Line UI 角色头像管理：
 * - 默认头像：assets/avatars/<characterId>.jpg（来自 docs 的角色头像素材）；
 * - 用户自定义：filesDir/avatars/<characterId>.<ext>，可在角色设定页单独设置/恢复默认。
 */
object AvatarManager {
    private const val AVATAR_ASSET_DIR = "avatars"

    private fun dir(context: Context): File =
        File(context.filesDir, "avatars").apply { mkdirs() }

    private fun safeId(characterId: String): String =
        characterId.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun assetExists(context: Context, path: String): Boolean =
        runCatching { context.assets.open(path).use { true } }.getOrDefault(false)

    /** 用户自定义头像文件（若已设置）。 */
    fun customAvatarFile(context: Context, characterId: String): File? {
        val id = safeId(characterId)
        return dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }
    }

    /** 默认头像 assets 路径（若存在）。 */
    fun defaultAvatarAssetPath(context: Context, characterId: String): String? {
        val path = "$AVATAR_ASSET_DIR/${safeId(characterId)}.jpg"
        return if (assetExists(context, path)) path else null
    }

    /** 该角色是否有可用头像（自定义或默认）。 */
    fun hasAvatar(context: Context, characterId: String): Boolean =
        customAvatarFile(context, characterId) != null ||
            defaultAvatarAssetPath(context, characterId) != null

    /** 导入自定义头像（覆盖旧的自定义头像）。 */
    fun importAvatar(context: Context, characterId: String, uri: Uri): Boolean = runCatching {
        val id = safeId(characterId)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }?.delete()
        val target = File(dir(context), "$id.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)

    /** 清空自定义头像（回到默认/无）。 */
    fun clearAvatar(context: Context, characterId: String) {
        val id = safeId(characterId)
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }?.delete()
    }

    // ==================== 用户自己的头像（全局，与角色头像独立） ====================

    /** 用户自定义头像文件（未设置时返回 null，默认为空）。 */
    fun userAvatarFile(context: Context): File? {
        val id = "user"
        return dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }
    }

    /** 导入用户头像（覆盖旧的）。 */
    fun importUserAvatar(context: Context, uri: Uri): Boolean = runCatching {
        val id = "user"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }?.delete()
        val target = File(dir(context), "$id.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)

    /** 清空用户头像（回到空）。 */
    fun clearUserAvatar(context: Context) {
        val id = "user"
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == id }?.delete()
    }
}
