package com.ekkus.offlineytplayer

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side proof that the packaged app can use its declared network capability. */
@RunWith(AndroidJUnit4::class)
class NetworkCapabilitySmokeTest {
    @Test
    fun packagedAppCanReadFromDeterministicLoopbackSocketFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertNotNull(packageInfo)

        val loopbackAddress = InetAddress.getByName("127.0.0.1")
        val server = ServerSocket(0, 1, loopbackAddress)
        val served = CountDownLatch(1)
        val fixtureBody = "offline-yt-network-ok"
        val receivedProbe = AtomicReference<String>()
        val fixture = thread(name = "network-capability-fixture", isDaemon = true) {
            try {
                server.accept().use { socket ->
                    receivedProbe.set(socket.getInputStream().bufferedReader().readText())
                    socket.getOutputStream().write(fixtureBody.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
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
            assertEquals("network-capability-probe", receivedProbe.get())
            assertTrue("Fixture server did not complete", served.await(2, TimeUnit.SECONDS))
        } finally {
            server.close()
            fixture.join(2_000)
        }
    }
}
