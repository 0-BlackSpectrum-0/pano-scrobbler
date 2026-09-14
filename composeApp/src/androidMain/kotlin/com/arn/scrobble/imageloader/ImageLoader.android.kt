package com.arn.scrobble.imageloader

import android.os.Build
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.allowHardware


actual fun ImageLoader.Builder.additionalOptions(): ImageLoader.Builder {
    allowHardware(false)
    return this
}

actual fun ComponentRegistry.Builder.additionalComponents(): ComponentRegistry.Builder {
    if (Build.VERSION.SDK_INT >= 28) {
        add(AnimatedImageDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
    add(AppIconKeyer())
    add(AppIconFetcher.Factory())
    return this
}