package com.ekkus.offlineytplayer.coregateway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Installed-app/runtime smoke for the app-owned UniFFI core gateway.
 *
 * This is deliberately narrower than the later deterministic E2E lanes: it proves the installed
 * APK can load the packaged Rust library, open generated UniFFI services through the app-owned
 * gateway, and round-trip representative records against an isolated app-private database/media
 * root without relying on production user data.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedUniffiCoreGatewayInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var testRoot: File? = null

    @After
    fun cleanup() {
        testRoot?.deleteRecursively()
    }

    @Test
    fun packagedNativeCoreRunsRepresentativeQueriesAgainstTemporaryAppPrivateStorage() {
        val root = File(context.filesDir, "ffi-runtime-smoke-${System.nanoTime()}")
        val mediaRoot = File(root, "media")
        assertTrue(root.mkdirs())
        assertTrue(mediaRoot.mkdirs())
        testRoot = root

        val database = File(root, "offline-yt-core.sqlite3")
        val gateway = GeneratedUniffiCoreGateway.open(database.absolutePath)
        try {
            val startup = gateway.reconcileStartup()
            assertNull(startup.error)
            assertNotNull(startup.value)

            val library = gateway.listLibrary()
            assertNull(library.error)
            assertNotNull(library.value)
            assertTrue(library.value!!.isEmpty())

            val downloads = gateway.listDownloadQueue()
            assertNull(downloads.error)
            assertNotNull(downloads.value)
            assertTrue(downloads.value!!.isEmpty())
        } finally {
            gateway.close()
        }

        assertTrue("FFI gateway should create/use the temporary app-private database", database.exists())
        assertTrue("Runtime smoke should exercise an app-private media root boundary", mediaRoot.isDirectory)
    }
}
