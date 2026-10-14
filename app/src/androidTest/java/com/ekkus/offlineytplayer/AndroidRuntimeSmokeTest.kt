package com.ekkus.offlineytplayer

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.ekkus.offlineytplayer.ui.decodeBoundedSetupThumbnail
import java.io.ByteArrayOutputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Small deterministic device-side gate proving the app/test APKs install and instrumentation runs. */
@RunWith(AndroidJUnit4::class)
class AndroidRuntimeSmokeTest {
    @Test
    fun packagedAppDoesNotPermitImplicitCloudBackupOfPrivateMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun targetPackageIsInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ekkus.offlineytplayer", context.packageName)
    }
    @Test
    fun thumbnailDecoderRejectsLargeDecodedImageDespiteSmallCompressedBody() {
        val source = Bitmap.createBitmap(4_097, 1, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            source.recycle()
        }
        val encoded = output.toByteArray()
        assertTrue(encoded.size < 1_000_000)
        assertNull(decodeBoundedSetupThumbnail(encoded))
    }

    @Test
    fun thumbnailDecoderAcceptsOrdinaryImage() {
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            source.recycle()
        }
        val decoded = decodeBoundedSetupThumbnail(output.toByteArray())
        assertNotNull(decoded)
        try {
            assertEquals(2, decoded!!.width)
            assertEquals(2, decoded.height)
        } finally {
            decoded?.recycle()
        }
    }
}
