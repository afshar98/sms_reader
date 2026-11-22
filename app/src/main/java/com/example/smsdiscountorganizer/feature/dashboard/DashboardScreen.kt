package com.example.smsdiscountorganizer.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsdiscountorganizer.core.PlaceholderContent

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenOffers: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column {
        PlaceholderContent(title = state.title, description = state.message)
        Button(onClick = onOpenOffers, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Browse offers")
        }
    }
}
