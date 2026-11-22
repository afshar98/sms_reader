package com.example.smsdiscountorganizer.di

import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import com.example.smsdiscountorganizer.data.repository.RoomScanRepository
import com.example.smsdiscountorganizer.data.repository.RoomThemeRepository
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.domain.settings.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    @Singleton
    abstract fun bindOfferRepository(repository: RoomOfferRepository): OfferRepository

    @Binds
    @Singleton
    abstract fun bindScanRepository(repository: RoomScanRepository): ScanRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(repository: RoomThemeRepository): ThemeRepository
}
