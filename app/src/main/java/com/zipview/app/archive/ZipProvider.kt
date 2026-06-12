package com.zipview.app.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * zip/cbz 的满级"免解压"实现：通过文件描述符的随机访问通道读取中央目录列条目，
 * 看某张图时只对那一个 entry 开流，全程不在磁盘落地整包解压。
 */
class ZipProvider(private val source: ArchiveSource) : ArchiveProvider {

    override val supportsRandomAccess: Boolean = true

    private val zip: ZipFile by lazy {
        ZipFile.builder().setSeekableByteChannel(source.openChannel()).get()
    }

    override fun listEntries(): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        val e = zip.entries
        while (e.hasMoreElements()) {
            val ze: ZipArchiveEntry = e.nextElement()
            val normalized = ze.name.trimEnd('/')
            if (normalized.isEmpty()) continue
            out += ArchiveEntry(
                path = normalized,
                name = normalized.substringAfterLast('/'),
                isDirectory = ze.isDirectory,
                size = if (ze.size >= 0) ze.size else 0L,
                type = FileType.typeOf(normalized, ze.isDirectory),
            )
        }
        return out
    }

    override fun openStream(entry: ArchiveEntry): InputStream {
        val ze = zip.getEntry(entry.path)
            ?: zip.getEntry(entry.path + "/")
            ?: throw FileNotFoundException("条目不存在: ${entry.path}")
        return zip.getInputStream(ze)
    }

    override fun close() {
        runCatching { zip.close() }
        runCatching { source.close() }
    }
}
