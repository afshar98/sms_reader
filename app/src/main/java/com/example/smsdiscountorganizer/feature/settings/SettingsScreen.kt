package com.example.smsdiscountorganizer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsdiscountorganizer.core.PlaceholderContent

@Composable
fun SettingsScreen(onOpenSmsDiagnostics: () -> Unit) {
    Column {
        PlaceholderContent(
            title = "Settings",
            description = "App preferences will be available here.",
        )
        Button(onClick = onOpenSmsDiagnostics, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("SMS Inbox diagnostics")
        }
    }
}
