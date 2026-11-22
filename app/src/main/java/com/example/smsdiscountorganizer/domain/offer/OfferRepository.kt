package com.example.smsdiscountorganizer.domain.offer

import kotlinx.coroutines.flow.Flow

interface OfferRepository {
    fun observeOffers(): Flow<List<Offer>>

    suspend fun upsertOffers(offers: List<Offer>)

    suspend fun clearAllLocalData()
}
