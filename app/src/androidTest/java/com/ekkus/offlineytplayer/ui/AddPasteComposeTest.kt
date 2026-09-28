package com.ekkus.offlineytplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AddPasteComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pasteButtonPlacesBoundedClipboardTextIntoUrlField() {
        val clipboard = clipboard()
        val sharedText = "fixture://" + "x".repeat(AddWorkflowPolicy.MaxClipboardChars + 20)
        clipboard.setPrimaryClip(ClipData.newPlainText("video", sharedText))

        compose.setContent {
            OfflineYTPlayerApp(initialSharedUrl = "")
        }

        compose.onNodeWithText("Paste").performClick()
        compose.onNodeWithText(sharedText.take(AddWorkflowPolicy.MaxClipboardChars)).assertIsDisplayed()
        compose.onNodeWithText(AddWorkflowPolicy.PastedClipboardMessage).assertIsDisplayed()
    }

    @Test
    fun pasteButtonHandlesMissingClipboardTextGracefully() {
        clipboard().setPrimaryClip(ClipData.newPlainText("blank", "   "))

        compose.setContent {
            OfflineYTPlayerApp(initialSharedUrl = "")
        }

        compose.onNodeWithText("Paste").performClick()
        compose.onNodeWithText(AddWorkflowPolicy.MissingClipboardMessage).assertIsDisplayed()
    }

    private fun clipboard(): ClipboardManager =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getSystemService(ClipboardManager::class.java)
}
