package com.example.smsdiscountorganizer.data.repository

import androidx.room.withTransaction
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.data.local.AppPreferenceEntity
import com.example.smsdiscountorganizer.domain.settings.ThemeMode
import com.example.smsdiscountorganizer.domain.settings.ThemeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class RoomThemeRepository @Inject constructor(
    private val database: AppDatabase,
) : ThemeRepository {
    override fun observeThemeMode(): Flow<ThemeMode> = combine(
        database.appPreferenceDao().observe(LIGHT_THEME_KEY),
        database.appPreferenceDao().observe(DARK_THEME_KEY),
    ) { light, dark ->
        when {
            dark?.enabled == true -> ThemeMode.DARK
            light?.enabled == true -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        database.withTransaction {
            database.appPreferenceDao().delete(LIGHT_THEME_KEY)
            database.appPreferenceDao().delete(DARK_THEME_KEY)
            when (mode) {
                ThemeMode.SYSTEM -> Unit
                ThemeMode.LIGHT -> database.appPreferenceDao().save(
                    AppPreferenceEntity(LIGHT_THEME_KEY, enabled = true),
                )
                ThemeMode.DARK -> database.appPreferenceDao().save(
                    AppPreferenceEntity(DARK_THEME_KEY, enabled = true),
                )
            }
        }
    }

    private companion object {
        const val LIGHT_THEME_KEY = "theme_light"
        const val DARK_THEME_KEY = "theme_dark"
    }
}
