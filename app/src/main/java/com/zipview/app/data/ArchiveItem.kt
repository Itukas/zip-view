package com.zipview.app.data

import android.net.Uri

/** 列表中展示的一个压缩包条目。 */
data class ArchiveItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val format: String,
) {
    val key: String get() = uri.toString()
}
