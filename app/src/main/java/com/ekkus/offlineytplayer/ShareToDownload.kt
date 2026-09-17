package com.ekkus.offlineytplayer

internal enum class ShareBackStackDestination {
    Library,
}

internal sealed class ShareToDownloadRoute {
    data object Library : ShareToDownloadRoute()

    data class DownloadSetup(
        val url: String,
        val backStackDestination: ShareBackStackDestination = ShareBackStackDestination.Library,
        val preservesFixedControlLayout: Boolean = true,
    ) : ShareToDownloadRoute()
}

internal object ShareToDownloadPolicy {
    const val OpensDownloadSetupDirectly = true
    const val PreservesFixedControlLayout = true
    const val ClearsAmbiguousBackStack = true

    fun initialRoute(sharedUrl: String?): ShareToDownloadRoute {
        val url = sharedUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return ShareToDownloadRoute.Library
        return ShareToDownloadRoute.DownloadSetup(url = url)
    }
}
