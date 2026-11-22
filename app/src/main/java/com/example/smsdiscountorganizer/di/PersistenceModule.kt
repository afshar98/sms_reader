package com.example.smsdiscountorganizer.di

import com.example.smsdiscountorganizer.data.repository.RoomOfferRepository
import com.example.smsdiscountorganizer.data.repository.RoomScanRepository
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.engine.FakeOfferExtractor
import com.example.smsdiscountorganizer.engine.OfferExtractor
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
    abstract fun bindOfferExtractor(extractor: FakeOfferExtractor): OfferExtractor
}
