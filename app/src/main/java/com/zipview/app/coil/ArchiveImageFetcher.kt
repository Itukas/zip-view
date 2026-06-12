package com.zipview.app.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.zipview.app.archive.ArchiveManager
import okio.buffer
import okio.source

/** 指向某个已打开压缩包内一张图片的 Coil 模型。 */
data class ArchiveImage(val archiveKey: String, val entryPath: String)

/**
 * Coil 自定义数据源：把 [ArchiveProvider.openStream] 的流直接喂给 Coil 解码，
 * 由 Coil 负责降采样、内存/磁盘缓存与 bitmap 复用，避免自造 OOM 防护。
 */
class ArchiveImageFetcher(
    private val data: ArchiveImage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val provider = ArchiveManager.get(data.archiveKey)
            ?: error("压缩包未打开: ${data.archiveKey}")
        val entry = ArchiveManager.entriesOf(options.context, data.archiveKey)
            .firstOrNull { it.path == data.entryPath }
            ?: error("条目不存在: ${data.entryPath}")
        val input = provider.openStream(entry)
        return SourceResult(
            source = ImageSource(input.source().buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<ArchiveImage> {
        override fun create(data: ArchiveImage, options: Options, imageLoader: ImageLoader): Fetcher =
            ArchiveImageFetcher(data, options)
    }
}
