package com.example.smsdiscountorganizer.di

import com.example.smsdiscountorganizer.engine.OfferExtractor
import com.example.smsdiscountorganizer.engine.expiration.PersianDateResolver
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfferExtractorModule {
    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun providePersianDateResolver(clock: Clock, zoneId: ZoneId): PersianDateResolver =
        PersianDateResolver(clock, zoneId)

    @Provides
    @Singleton
    fun provideOfferExtractor(resolver: PersianDateResolver): OfferExtractor =
        PersianOfferExtractor(expirationResolver = resolver)
}
