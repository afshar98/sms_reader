package com.example.smsdiscountorganizer.di

import com.example.smsdiscountorganizer.data.sms.AndroidSmsDataSource
import com.example.smsdiscountorganizer.domain.sms.SmsDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsDataSourceModule {
    @Binds
    @Singleton
    abstract fun bindSmsDataSource(dataSource: AndroidSmsDataSource): SmsDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object SmsClockModule {
    @Provides
    @Singleton
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)
}
