package com.opencode.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.opencode.client.ui.OpencodeNavHost
import com.opencode.client.ui.theme.OpencodeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = LocalGraph.init(applicationContext)
        val deepLinkSession = mutableStateOf(
            intent?.getStringExtra(com.opencode.client.notify.Notifier.EXTRA_SESSION_ID)
        )

        setContent {
            val settings by container.settings.settings.collectAsState()
            OpencodeTheme(themeMode = settings.themeMode) {
                OpencodeNavHost(
                    nav = rememberNavController(),
                    deepLinkSessionId = deepLinkSession.value
                )
            }
        }
    }
}
