package com.zipview.app.archive

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 已打开压缩包的进程内注册表：维护 key -> Provider 与条目缓存，供浏览/看图/Coil 取流共享。
 * key 为压缩包的 Uri 字符串；嵌套包 key 形如 "<parent>!<entryPath>"。
 */
object ArchiveManager {

    private val providers = HashMap<String, ArchiveProvider>()
    private val entries = HashMap<String, List<ArchiveEntry>>()

    private fun cacheRoot(context: Context): File =
        File(context.applicationContext.cacheDir, "archives").apply { mkdirs() }

    fun openUri(context: Context, key: String): ArchiveProvider =
        providers.getOrPut(key) {
            val uri = Uri.parse(key)
            val source = if (uri.scheme == "file") {
                val path = uri.path ?: error("无效的文件 Uri: $key")
                FileArchiveSource(File(path))
            } else {
                UriArchiveSource(context.applicationContext, uri)
            }
            ArchiveProviderFactory.create(source, cacheRoot(context))
        }

    /** 打开包内的嵌套压缩包：先把内层条目解到缓存文件，再以文件来源打开。返回嵌套 key。 */
    fun openNested(context: Context, parentKey: String, entryPath: String): String {
        val nestedKey = "$parentKey!$entryPath"
        if (!providers.containsKey(nestedKey)) {
            val parent = get(parentKey) ?: openUri(context, parentKey)
            val target = entriesOf(context, parentKey).first { it.path == entryPath }
            val tmp = File(
                cacheRoot(context),
                "nested/${Integer.toHexString(nestedKey.hashCode())}_${target.name}",
            )
            tmp.parentFile?.mkdirs()
            parent.openStream(target).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            providers[nestedKey] = ArchiveProviderFactory.create(FileArchiveSource(tmp), cacheRoot(context))
        }
        return nestedKey
    }

    fun get(key: String): ArchiveProvider? = providers[key]

    fun entriesOf(context: Context, key: String): List<ArchiveEntry> =
        entries.getOrPut(key) { (get(key) ?: openUri(context, key)).listEntries() }

    fun close(key: String) {
        providers.remove(key)?.let { runCatching { it.close() } }
        entries.remove(key)
    }
}
