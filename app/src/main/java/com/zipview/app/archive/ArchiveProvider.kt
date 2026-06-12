package com.zipview.app.archive

import java.io.Closeable
import java.io.InputStream

/**
 * 统一的压缩包访问抽象。上层 UI 只依赖此接口，不感知具体格式。
 * 新增一种格式 = 新增一个实现，UI 无需改动。
 */
interface ArchiveProvider : Closeable {

    /** 是否支持随机抽取单个条目（不支持者需走降级缓存）。 */
    val supportsRandomAccess: Boolean

    /** 列出包内所有条目（不在磁盘落地整包解压）。 */
    fun listEntries(): List<ArchiveEntry>

    /** 打开单个条目的输入流。看图、预览、转交外部都基于它。 */
    fun openStream(entry: ArchiveEntry): InputStream
}
