package com.zipview.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.zipview.app.archive.FileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 压缩包列表的存储与查询。通过 SAF 获取并持久化可访问的 Uri 权限，
 * 不申请全盘存储权限。重命名以"显示名覆盖"方式记录，不改动源文件。
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

    fun add(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.edit().putStringSet(KEY_URIS, uriSet().apply { add(uri.toString()) }).apply()
        reload()
    }

    fun remove(uri: Uri) {
        prefs.edit().putStringSet(KEY_URIS, uriSet().apply { remove(uri.toString()) }).apply()
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        reload()
    }

    fun rename(uri: Uri, newName: String) {
        prefs.edit().putString(NAME_PREFIX + uri.toString().hashCode(), newName).apply()
        reload()
    }

    /** 检测某压缩包的访问授权是否仍然有效。 */
    fun isAccessible(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun toItem(uri: Uri): ArchiveItem? {
        var name = uri.lastPathSegment ?: return null
        var size = 0L
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
        return ArchiveItem(
            uri = uri,
            name = nameOverride(uri.toString()) ?: name,
            sizeBytes = size,
            format = FileType.extensionOf(name).uppercase().ifEmpty { "?" },
        )
    }

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
