package com.zipview.app.archive

object FileType {

    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val text = setOf("txt", "md", "log", "json", "xml", "csv", "ini", "yml", "yaml", "html", "htm")
    private val archive = setOf("zip", "cbz", "rar", "cbr", "7z", "tar", "gz", "tgz", "bz2", "xz")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun typeOf(name: String, isDirectory: Boolean): EntryType {
        if (isDirectory) return EntryType.DIRECTORY
        return when (extensionOf(name)) {
            in image -> EntryType.IMAGE
            in text -> EntryType.TEXT
            in archive -> EntryType.ARCHIVE
            else -> EntryType.OTHER
        }
    }

    fun isImage(name: String): Boolean = extensionOf(name) in image
    fun isArchive(name: String): Boolean = extensionOf(name) in archive
}
