package com.example.smsdiscountorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.smsdiscountorganizer.core.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF176B5B),
                    secondary = Color(0xFF4E635C),
                    background = Color(0xFFF8F7F2),
                    surface = Color(0xFFF8F7F2),
                ),
            ) {
                Surface {
                    AppNavigation()
                }
            }
        }
    }
}
