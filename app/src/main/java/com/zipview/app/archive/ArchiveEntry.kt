package com.zipview.app.archive

/** 压缩包内一个条目的类型。 */
enum class EntryType { DIRECTORY, IMAGE, TEXT, ARCHIVE, OTHER }

/**
 * 压缩包内的一个条目（文件或目录）。
 *
 * @param path 包内完整路径，使用 '/' 分隔，且末尾不带 '/'
 * @param name 显示名（路径最后一段）
 * @param size 解压后大小（字节），未知时为 0
 */
data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val type: EntryType,
) {
    /** 父目录路径（顶层条目为空串）。 */
    val parent: String get() = path.substringBeforeLast('/', "")
}
