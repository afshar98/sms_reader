package com.example.smsdiscountorganizer.di

import com.example.smsdiscountorganizer.engine.OfferExtractor
import com.example.smsdiscountorganizer.engine.extraction.PersianOfferExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfferExtractorModule {
    @Provides
    @Singleton
    fun provideOfferExtractor(): OfferExtractor = PersianOfferExtractor()
}
