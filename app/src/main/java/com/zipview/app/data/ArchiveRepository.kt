package com.zipview.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.zipview.app.archive.FileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * 压缩包列表的存储与查询。通过 SAF 获取并持久化可访问的 Uri 权限，
 * 不申请全盘存储权限。对于分享/外部打开这类不可持久化的临时授权，
 * 复制到应用私有目录后再保存，避免稍后打开时 Permission Denial。
 */
class ArchiveRepository private constructor(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences("archives", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow<List<ArchiveItem>>(emptyList())
    val items: StateFlow<List<ArchiveItem>> = _items.asStateFlow()

    init {
        reload()
    }

    private fun uriSet(): MutableSet<String> =
        prefs.getStringSet(KEY_URIS, emptySet())!!.toMutableSet()

    private fun nameOverride(uriStr: String): String? =
        prefs.getString(NAME_PREFIX + uriStr.hashCode(), null)

    fun reload() {
        _items.value = uriSet()
            .mapNotNull { toItem(Uri.parse(it)) }
            .sortedBy { it.name.lowercase() }
    }

    fun add(uri: Uri, flags: Int = 0): Uri {
        val storedUri = persistOrImport(uri, flags)
        prefs.edit().putStringSet(KEY_URIS, uriSet().apply { add(storedUri.toString()) }).apply()
        reload()
        return storedUri
    }

    fun remove(uri: Uri) {
        prefs.edit().putStringSet(KEY_URIS, uriSet().apply { remove(uri.toString()) }).apply()
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                runCatching { File(path).takeIf { it.isFile && it.parentFile == importDir() }?.delete() }
            }
        } else {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        reload()
    }

    fun rename(uri: Uri, newName: String) {
        prefs.edit().putString(NAME_PREFIX + uri.toString().hashCode(), newName).apply()
        reload()
    }

    /** 检测某压缩包的访问授权是否仍然有效。 */
    fun isAccessible(uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            val path = uri.path ?: return@runCatching false
            File(path).isFile
        } else {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }
    }.getOrDefault(false)

    private fun toItem(uri: Uri): ArchiveItem? {
        var name = uri.lastPathSegment ?: return null
        var size = 0L
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                name = file.name
                size = file.length()
            }
        } else {
            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
            }
        }
        return ArchiveItem(
            uri = uri,
            name = nameOverride(uri.toString()) ?: name,
            sizeBytes = size,
            format = FileType.extensionOf(name).uppercase().ifEmpty { "?" },
        )
    }

    private fun persistOrImport(uri: Uri, flags: Int): Uri {
        if (uri.scheme == "file") return uri

        val readGrant = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        val persistableGrant = flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (readGrant != 0 && persistableGrant != 0) {
            val persisted = runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (persisted) return uri
        }

        return importToPrivateStorage(uri)
    }

    private fun importToPrivateStorage(uri: Uri): Uri {
        val name = sanitizeFileName(queryDisplayName(uri) ?: uri.lastPathSegment ?: "archive.zip")
        val target = uniqueFile(importDir(), name)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取导入的压缩包: $uri")
        return Uri.fromFile(target)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun importDir(): File =
        File(appContext.filesDir, "imports").apply { mkdirs() }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index$ext")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "archive.zip" }

    companion object {
        private const val KEY_URIS = "uris"
        private const val NAME_PREFIX = "name_"

        @Volatile
        private var instance: ArchiveRepository? = null

        fun get(context: Context): ArchiveRepository =
            instance ?: synchronized(this) {
                instance ?: ArchiveRepository(context.applicationContext).also { instance = it }
            }
    }
}
