package com.ekkus.offlineytplayer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.downloads.DownloadConnectivityMapper
import com.ekkus.offlineytplayer.downloads.DownloadConnectivity
import com.ekkus.offlineytplayer.downloads.AndroidDownloadConnectivityObserver
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun packagedAppCanReadFromDeterministicLoopbackHttpFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertNotNull(packageInfo)

        ServerSocket().use { server ->
            server.bind(InetSocketAddress("127.0.0.1", 0))
            val served = CountDownLatch(1)
            val fixture = thread(name = "network-capability-fixture") {
                server.accept().use { socket ->
                    socket.getInputStream().bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                    }
                    val body = "offline-yt-network-ok"
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: text/plain\r\n")
                        writer.write("Content-Length: ${body.toByteArray().size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(body)
                    }
                    served.countDown()
                }
            }

            val connection = URL("http://127.0.0.1:${server.localPort}/fixture").openConnection().apply {
                connectTimeout = 2_000
                readTimeout = 2_000
            }
            assertEquals("offline-yt-network-ok", connection.getInputStream().bufferedReader().use { it.readText() })
            assertTrue(served.await(2, TimeUnit.SECONDS))
            fixture.join(2_000)
        }
    }
}
