package com.ekkus.offlineytplayer

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkCapabilityInstrumentedTest {
    @Test
    fun packagedManifestDeclaresInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertTrue(
            "Packaged app must declare android.permission.INTERNET",
            packageInfo.requestedPermissions.orEmpty().contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun packagedAppCanReadDeterministicLoopbackSocketFixture() {
        val loopbackAddress = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 1, loopbackAddress)
        val served = CountDownLatch(1)
        val fixtureBody = "offline-yt-player-network-fixture"
        val receivedProbe = AtomicReference<String>()
        val serverThread = thread(name = "network-fixture", isDaemon = true) {
            try {
                server.accept().use { client ->
                    receivedProbe.set(client.getInputStream().bufferedReader().readText())
                    client.getOutputStream().write(fixtureBody.toByteArray(Charsets.UTF_8))
                    client.getOutputStream().flush()
                    served.countDown()
                }
            } catch (closedDuringCleanup: SocketException) {
                if (!server.isClosed) throw closedDuringCleanup
            } finally {
                server.close()
            }
        }

        try {
            val response = Socket(loopbackAddress, server.localPort).use { client ->
                client.soTimeout = 2_000
                client.getOutputStream().write("network-capability-probe".toByteArray(Charsets.UTF_8))
                client.shutdownOutput()
                client.getInputStream().bufferedReader().use { it.readText() }
            }
            assertEquals(fixtureBody, response)
            assertTrue("Fixture server did not complete", served.await(2, TimeUnit.SECONDS))
            assertEquals("network-capability-probe", receivedProbe.get())
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }
}
