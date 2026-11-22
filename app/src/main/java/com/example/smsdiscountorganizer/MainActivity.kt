package com.example.smsdiscountorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.navigation.AppNavigation
import com.example.smsdiscountorganizer.core.theme.OrganizerTheme
import com.example.smsdiscountorganizer.domain.settings.ThemeMode
import com.example.smsdiscountorganizer.feature.settings.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode = themeViewModel.themeMode.collectAsStateWithLifecycle().value
            OrganizerTheme(darkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppNavigation()
                }
            }
        }
    }
}
