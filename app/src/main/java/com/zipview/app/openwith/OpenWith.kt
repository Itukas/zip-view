package com.zipview.app.openwith

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.zipview.app.archive.ArchiveManager
import com.zipview.app.archive.FileType
import java.io.File

object OpenWith {

    /**
     * 把条目内容落到缓存文件，并通过系统 Intent 转交可处理的外部应用。
     * @return 成功转交返回 true；没有可用应用或失败返回 false。
     */
    fun openExternally(context: Context, archiveKey: String, entryPath: String): Boolean {
        val provider = ArchiveManager.get(archiveKey) ?: return false
        val entry = ArchiveManager.entriesOf(context, archiveKey)
            .firstOrNull { it.path == entryPath } ?: return false

        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(sharedDir, entry.name)
        provider.openStream(entry).use { input -> outFile.outputStream().use { input.copyTo(it) } }

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(entry.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            val chooser = Intent.createChooser(view, "用其他应用打开")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun mimeOf(name: String): String = when (FileType.extensionOf(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "txt", "log", "ini" -> "text/plain"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "xml", "html", "htm" -> "text/html"
        "pdf" -> "application/pdf"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "*/*"
    }
}
