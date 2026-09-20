package com.ekkus.offlineytplayer

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun packagedAppCanReadDeterministicLoopbackHttpFixture() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val served = CountDownLatch(1)
        val fixtureBody = "offline-yt-player-network-fixture"
        val serverThread = thread(name = "network-fixture", isDaemon = true) {
            server.use { socket ->
                socket.accept().use { client ->
                    client.getInputStream().bufferedReader().readLine()
                    while (!client.getInputStream().bufferedReader().readLine().isNullOrEmpty()) {
                        // Consume the bounded HTTP request headers before responding.
                    }
                    val bodyBytes = fixtureBody.toByteArray(Charsets.UTF_8)
                    client.getOutputStream().bufferedWriter().use { writer ->
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: text/plain; charset=utf-8\r\n")
                        writer.write("Content-Length: ${bodyBytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(fixtureBody)
                    }
                    served.countDown()
                }
            }
        }

        val connection = URL("http://127.0.0.1:${server.localPort}/fixture").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.useCaches = false
        try {
            assertEquals(200, connection.responseCode)
            assertEquals(fixtureBody, connection.inputStream.bufferedReader().use { it.readText() })
            assertTrue("Fixture server did not complete", served.await(2, TimeUnit.SECONDS))
        } finally {
            connection.disconnect()
            server.close()
            serverThread.join(2_000)
        }
    }
}
