package com.example.smsdiscountorganizer.feature.offers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsdiscountorganizer.core.PlaceholderContent

@Composable
fun OffersScreen(onOpenDetails: () -> Unit) {
    Column {
        PlaceholderContent(
            title = "Offers",
            description = "Offers will be organized here.",
        )
        Button(onClick = onOpenDetails, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Open sample offer")
        }
    }
}
