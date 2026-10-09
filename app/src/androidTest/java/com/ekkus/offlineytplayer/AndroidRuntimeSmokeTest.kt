package com.ekkus.offlineytplayer

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
