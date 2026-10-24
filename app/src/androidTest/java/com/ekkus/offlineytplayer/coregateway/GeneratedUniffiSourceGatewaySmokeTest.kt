package com.ekkus.offlineytplayer.coregateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.core.FfiYouTubeSourceService
import com.ekkus.offlineytplayer.core.FfiCancellationToken
import org.junit.Assert.assertNotNull
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
        // Kotlin's generated constructor is invoked directly. The old reflection
        // erroneously searched for .new(), causing a permanent disabled Analyze.
        FfiYouTubeSourceService().use { service ->
            FfiCancellationToken().use { token ->
                val result = service.resolve(
                    "https://unsupported.invalid/watch?v=AACwyjlk8ko", token,
                )
                assertNotNull(result.error)
            }
        }
        GeneratedUniffiSourceAnalysisGateway.open().use { gateway ->
            // Production bootstrap must pass the same native/UniFFI preflight.
            assertNotNull(gateway)
        }
    }
}
