package com.zipview.app.archive

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * 不支持随机访问格式（如 7z solid、tar.gz）的降级基类：
 * 首次访问时在后台把所有条目解压到 app 私有缓存目录，之后从缓存读取。
 * 子类只需实现 [extractAll]。
 */
abstract class CachingArchiveProvider(
    protected val source: ArchiveSource,
    private val cacheRoot: File,
) : ArchiveProvider {

    override val supportsRandomAccess: Boolean = false

    protected val extractDir: File by lazy {
        File(cacheRoot, source.cacheId).apply { mkdirs() }
    }

    @Volatile
    private var prepared = false

    /** 首次访问时把所有条目解压到缓存目录；可重复调用，仅第一次真正执行。 */
    @Synchronized
    fun prepare(onProgress: ((done: Int, total: Int) -> Unit)? = null) {
        if (prepared) return
        extractAll(extractDir, onProgress)
        prepared = true
    }

    protected abstract fun extractAll(dest: File, onProgress: ((Int, Int) -> Unit)?)

    override fun listEntries(): List<ArchiveEntry> {
        prepare()
        return extractDir.walkTopDown()
            .filter { it != extractDir }
            .map { f ->
                val rel = f.relativeTo(extractDir).path.replace(File.separatorChar, '/')
                ArchiveEntry(
                    path = rel,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    type = FileType.typeOf(f.name, f.isDirectory),
                )
            }
            .toList()
    }

    override fun openStream(entry: ArchiveEntry): InputStream {
        prepare()
        return FileInputStream(File(extractDir, entry.path))
    }

    override fun close() {
        runCatching { source.close() }
    }
}
