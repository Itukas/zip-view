package com.zipview.app.archive

import java.io.File

class UnsupportedArchiveException(message: String) : Exception(message)

/** 按文件后缀选择对应的 [ArchiveProvider]，把"格式差异"关在这一层后面。 */
object ArchiveProviderFactory {

    fun create(source: ArchiveSource, cacheRoot: File): ArchiveProvider =
        when (FileType.extensionOf(source.displayName)) {
            "zip", "cbz" -> ZipProvider(source)
            "tar" -> TarProvider(source, cacheRoot)
            "rar", "cbr" -> throw UnsupportedArchiveException("RAR 暂未支持，将在后续版本加入")
            "7z" -> throw UnsupportedArchiveException("7z 暂未支持，将在后续版本加入")
            else -> ZipProvider(source) // 兜底按 zip 尝试（不少无扩展名文件实为 zip）
        }
}
