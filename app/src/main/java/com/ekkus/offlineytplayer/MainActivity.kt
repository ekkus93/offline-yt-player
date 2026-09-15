package com.ekkus.offlineytplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUrl = ShareInput.parse(
            intent?.action,
            intent?.type,
            intent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        setContent { OfflineYTPlayerApp(initialSharedUrl = sharedUrl) }
    }
}
