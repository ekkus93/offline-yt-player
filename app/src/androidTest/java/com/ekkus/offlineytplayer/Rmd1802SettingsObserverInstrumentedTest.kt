package com.ekkus.offlineytplayer

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.settings.AppearanceSetting
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1802SettingsObserverInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun backgroundStoreUpdatePublishesAppearanceToComposeOnMainThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SharedPreferencesAppSettingsStore.open(context)
        val originalAppearance = store.snapshot().appearance
        val updatedAppearance = if (originalAppearance == AppearanceSetting.Dark) {
            AppearanceSetting.Light
        } else {
            AppearanceSetting.Dark
        }
        try {
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ).use {
                compose.waitUntil(20_000) {
                    runCatching { compose.onNodeWithText("Settings").assertIsDisplayed() }.isSuccess
                }
                compose.onNodeWithText("Settings").performClick()
                compose.onNodeWithText("Appearance").performClick()
                compose.onNodeWithText("Theme: ${originalAppearance.name}").assertIsDisplayed()

                val failure = AtomicReference<Throwable?>()
                val updater = thread(name = "rmd-1802-off-main-settings-update") {
                    try {
                        store.update { appearance = updatedAppearance }
                    } catch (error: Throwable) {
                        failure.set(error)
                    }
                }
                updater.join(10_000)
                assertFalse("background settings update did not complete", updater.isAlive)
                failure.get()?.let { throw AssertionError("background settings update failed", it) }

                compose.waitUntil(20_000) {
                    runCatching {
                        compose.onNodeWithText("Theme: ${updatedAppearance.name}").assertIsDisplayed()
                    }.isSuccess
                }
            }
        } finally {
            store.update { appearance = originalAppearance }
            store.close()
        }
    }
}
