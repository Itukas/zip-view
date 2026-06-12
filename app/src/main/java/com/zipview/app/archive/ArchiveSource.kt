package com.zipview.app.archive

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel

/** 压缩包字节来源的抽象：既能给随机访问通道，也能给顺序流。 */
interface ArchiveSource : Closeable {
    val displayName: String
    val cacheId: String

    /** 随机访问通道（zip 等支持随机抽取的格式使用）。 */
    fun openChannel(): SeekableByteChannel

    /** 顺序输入流（tar/7z 等降级格式使用）。 */
    fun openInput(): InputStream
}

/** 来自 SAF / 分享 Intent 的内容 Uri 来源。借助文件描述符实现随机访问，无需先整包复制。 */
class UriArchiveSource(
    private val context: Context,
    val uri: Uri,
) : ArchiveSource {

    private val openFds = mutableListOf<ParcelFileDescriptor>()

    override val cacheId: String = Integer.toHexString(uri.toString().hashCode())

    override val displayName: String by lazy { queryName() }

    override fun openChannel(): SeekableByteChannel {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("无法打开压缩包: $uri")
        openFds += pfd
        return FileInputStream(pfd.fileDescriptor).channel
    }

    override fun openInput(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取压缩包: $uri")

    private fun queryName(): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !c.isNull(idx)) return c.getString(idx)
                }
            }
        return uri.lastPathSegment ?: "archive"
    }

    override fun close() {
        openFds.forEach { runCatching { it.close() } }
        openFds.clear()
    }
}

/** 来自本地文件的来源（用于嵌套压缩包：先把内层条目解到缓存文件再打开）。 */
class FileArchiveSource(private val file: File) : ArchiveSource {
    override val displayName: String = file.name
    override val cacheId: String = Integer.toHexString(file.absolutePath.hashCode())
    override fun openChannel(): SeekableByteChannel = RandomAccessFile(file, "r").channel
    override fun openInput(): InputStream = FileInputStream(file)
    override fun close() {}
}
