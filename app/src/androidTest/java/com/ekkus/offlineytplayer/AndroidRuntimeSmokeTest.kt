package com.ekkus.offlineytplayer

import android.content.pm.ApplicationInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.app.job.JobInfo
import android.os.Build
import com.ekkus.offlineytplayer.downloads.AndroidDownloadExecutionScheduler
import com.ekkus.offlineytplayer.downloads.DownloadBootRecovery
import com.ekkus.offlineytplayer.downloads.DownloadBootRecoveryDisposition
import com.ekkus.offlineytplayer.downloads.DownloadExecutionSchedulerPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNetworkPreference
import com.ekkus.offlineytplayer.downloads.DownloadRebootReceiver
import com.ekkus.offlineytplayer.downloads.DownloadScheduleRequest
import com.ekkus.offlineytplayer.downloads.DownloadSchedulerKind
import com.ekkus.offlineytplayer.downloads.DownloadUserInitiatedJobService
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
    fun runtimeSchedulingInputsAndRestrictedUidtFailureMatchActualApiTier() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = Build.VERSION.SDK_INT
        val expected = if (api >= 34) {
            DownloadSchedulerKind.UserInitiatedDataTransferJob
        } else {
            DownloadSchedulerKind.ForegroundServiceFallback
        }
        assertEquals(expected, DownloadExecutionSchedulerPolicy.schedulerKindForSdk(api))
        assertEquals(
            api < 34,
            DownloadExecutionSchedulerPolicy.usesForegroundFallback(api),
        )
        val extras = DownloadExecutionSchedulerPolicy.durableQueueItemExtras("api-fixture-queue")
        assertEquals(
            "api-fixture-queue",
            extras.getString(DownloadUserInitiatedJobService.ExtraQueueItemId),
        )
        val component = ComponentName(context, DownloadUserInitiatedJobService::class.java)
        val anyJob = DownloadExecutionSchedulerPolicy.applyNetworkConstraint(
            JobInfo.Builder(48_201, component).setExtras(extras),
            DownloadNetworkPreference.AnyNetwork,
        ).build()
        assertEquals(JobInfo.NETWORK_TYPE_ANY, anyJob.networkType)
        if (api >= 34) {
            val wifiJob = DownloadExecutionSchedulerPolicy.applyNetworkConstraint(
                JobInfo.Builder(48_202, component).setExtras(extras),
                DownloadNetworkPreference.WifiOnly,
            ).setUserInitiated(true).build()
            assertNotNull(
                "UIDT Wi-Fi-only scheduling must install a real NetworkRequest",
                wifiJob.requiredNetwork,
            )
            assertTrue(wifiJob.isUserInitiated)
            val refused = AndroidDownloadExecutionScheduler(
                context = context,
                sdkInt = api,
                jobScheduler = null,
            ).schedule(
                DownloadScheduleRequest(
                    queueItemId = "api-fixture-queue",
                    networkPreference = DownloadNetworkPreference.WifiOnly,
                ),
            )
            assertEquals(DownloadSchedulerKind.UserInitiatedDataTransferJob, refused.kind)
            assertFalse(
                "unavailable platform JobScheduler may not create a fictitious queued UIDT",
                refused.accepted,
            )
        } else {
            assertFalse(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(api))
        }
    }

    @Test
    fun bootBeforeUnlockCannotStartDataSyncOrOpenCredentialProtectedFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var forbiddenCalls = 0
        val locked = object : ContextWrapper(context) {
            override fun startService(service: Intent?): ComponentName? {
                forbiddenCalls++
                throw AssertionError("boot handler tried to start a background service")
            }

            override fun startForegroundService(service: Intent?): ComponentName? {
                forbiddenCalls++
                throw AssertionError("boot handler tried to start a dataSync foreground service")
            }

            override fun getFilesDir(): java.io.File {
                forbiddenCalls++
                throw AssertionError("boot handler opened credential-protected media storage")
            }
        }
        DownloadRebootReceiver().onReceive(locked, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0, forbiddenCalls)
        val decision = DownloadBootRecovery.onReceive(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(DownloadBootRecoveryDisposition.DeferUntilAppStartup, decision.disposition)
        assertTrue(decision.workRemainsRecoverable)
        assertTrue(decision.scheduledOnlyWhenLegal)
        assertFalse(decision.startsForegroundService)
        assertFalse(decision.opensCredentialProtectedStorage)
        val ignored = DownloadBootRecovery.onReceive(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertEquals(DownloadBootRecoveryDisposition.Ignore, ignored.disposition)
        DownloadRebootReceiver().onReceive(locked, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(0, forbiddenCalls)
    }

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
