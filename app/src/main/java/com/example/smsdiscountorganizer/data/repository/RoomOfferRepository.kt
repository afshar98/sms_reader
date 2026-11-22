package com.example.smsdiscountorganizer.data.repository

import androidx.room.withTransaction
import com.example.smsdiscountorganizer.data.local.AppDatabase
import com.example.smsdiscountorganizer.data.local.OfferEntity
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomOfferRepository @Inject constructor(
    private val database: AppDatabase,
) : OfferRepository {
    override fun observeOffers(): Flow<List<Offer>> =
        database.offerDao().observeAll().map { offers -> offers.map(OfferEntity::toDomain) }

    override suspend fun upsertOffers(offers: List<Offer>) {
        database.offerDao().upsertAll(offers.map(Offer::toEntity))
    }

    override suspend fun clearAllLocalData() {
        database.withTransaction {
            database.offerDao().deleteAll()
            database.scanStateDao().clear()
            database.appPreferenceDao().clearAll()
        }
    }
}

internal fun OfferEntity.toDomain(): Offer = Offer(
    sourceSmsId = sourceSmsId,
    title = offerTitle,
    merchantName = merchantName,
    discountCode = discountCode,
    discountValue = discountValue,
    validUntilMillis = validUntilMillis,
    sourceDateMillis = sourceDateMillis,
    extractedAtMillis = extractedAtMillis,
)

internal fun Offer.toEntity(): OfferEntity = OfferEntity(
    sourceSmsId = sourceSmsId,
    offerTitle = title,
    merchantName = merchantName,
    discountCode = discountCode,
    discountValue = discountValue,
    validUntilMillis = validUntilMillis,
    sourceDateMillis = sourceDateMillis,
    extractedAtMillis = extractedAtMillis,
)
