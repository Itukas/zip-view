package com.zipview.app.archive

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream

/** tar 的降级实现：作为 [CachingArchiveProvider] 的落地范例（tar 无中央目录，需顺序解压到缓存）。 */
class TarProvider(
    source: ArchiveSource,
    cacheRoot: File,
) : CachingArchiveProvider(source, cacheRoot) {

    override fun extractAll(dest: File, onProgress: ((Int, Int) -> Unit)?) {
        val destCanonical = dest.canonicalPath
        TarArchiveInputStream(source.openInput()).use { tar ->
            var entry = tar.nextTarEntry
            var count = 0
            while (entry != null) {
                val target = File(dest, entry.name)
                // 防止 zip-slip：确保解压目标落在 dest 之内
                if (!target.canonicalPath.startsWith(destCanonical)) {
                    entry = tar.nextTarEntry
                    continue
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { tar.copyTo(it) }
                }
                count++
                onProgress?.invoke(count, -1)
                entry = tar.nextTarEntry
            }
        }
    }
}
