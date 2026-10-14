package com.ekkus.offlineytplayer.ui

/** Bound the actual decoded pixel allocation independently of compressed transfer size. */
internal object SetupThumbnailDecodePolicy {
    const val MaxWidth = 4_096
    const val MaxHeight = 4_096
    const val MaxPixels = 4_194_304L

    fun acceptsDimensions(width: Int, height: Int): Boolean =
        width in 1..MaxWidth &&
            height in 1..MaxHeight &&
            width.toLong() * height.toLong() <= MaxPixels
}
