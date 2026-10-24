package com.ekkus.offlineytplayer.coregateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the real APK-packaged Rust/UniFFI source boundary on the CI emulator.
 * Gateway.open() performs a deterministic native call using an unsupported host;
 * it must never depend on YouTube network availability or test-only injection.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedUniffiSourceGatewaySmokeTest {
    @Test
    fun packagedYouTubeResolverInitializesWithoutNetwork() {
        GeneratedUniffiSourceAnalysisGateway.open().use { gateway ->
            // open() fails on class loading, JNI ABI mismatch, constructor failure,
            // generated-method mismatch, or failure of the native preflight.
            check(gateway is AppSourceAnalysisGateway)
        }
    }
}
