package dev.opencode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import dev.opencode.android.ui.AppRoot
import dev.opencode.android.ui.theme.OpenCodeTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCodeTheme {
                AppRoot(container = OpenCodeApp.get().container)
            }
        }
    }
}