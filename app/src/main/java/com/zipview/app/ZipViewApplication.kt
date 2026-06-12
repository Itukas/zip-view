package com.zipview.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.zipview.app.coil.ArchiveImageFetcher

class ZipViewApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ArchiveImageFetcher.Factory())
            }
            .crossfade(true)
            .build()
}
