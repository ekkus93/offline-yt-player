package com.ekkus.offlineytplayer

object BuildMetadata {
    val version: String
        get() = BuildConfig.VERSION_NAME

    val revision: String
        get() = BuildConfig.SOURCE_REVISION

    val display: String
        get() = "$version (${revision.take(12)})"

    fun artifactName(extension: String = "apk"): String {
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val safeRevision = revision.take(12).replace(Regex("[^A-Za-z0-9._-]"), "-")
        return "offline-yt-player-$safeVersion-$safeRevision.$extension"
    }
}
