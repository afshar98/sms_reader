package com.example.smsdiscountorganizer.domain.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface ThemeRepository {
    fun observeThemeMode(): kotlinx.coroutines.flow.Flow<ThemeMode>
    suspend fun saveThemeMode(mode: ThemeMode)
}
