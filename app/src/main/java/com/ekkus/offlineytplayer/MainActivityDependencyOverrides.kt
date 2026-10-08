package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway

/**
 * Debug-only, in-process dependency seam for deterministic instrumentation.
 *
 * Production startup always uses the generated UniFFI gateways unless an instrumentation process
 * explicitly installs factories. There is deliberately no Intent extra, manifest component, or
 * persisted setting that can activate these overrides.
 */
object MainActivityDependencyOverrides {
    @Volatile
    private var sourceAnalysisFactory: (() -> AppSourceAnalysisGateway)? = null

    @Volatile
    private var downloadControlFactory: ((String) -> AppDownloadControlGateway)? = null

    fun installForInstrumentation(
        sourceFactory: () -> AppSourceAnalysisGateway,
        controlsFactory: (String) -> AppDownloadControlGateway,
    ) {
        check(BuildConfig.DEBUG) { "MainActivity dependency overrides are debug-only" }
        sourceAnalysisFactory = sourceFactory
        downloadControlFactory = controlsFactory
    }

    fun clearForInstrumentation() {
        sourceAnalysisFactory = null
        downloadControlFactory = null
    }

    internal fun createSourceAnalysis(): AppSourceAnalysisGateway? = sourceAnalysisFactory?.invoke()

    internal fun createDownloadControl(databasePath: String): AppDownloadControlGateway? =
        downloadControlFactory?.invoke(databasePath)
}
