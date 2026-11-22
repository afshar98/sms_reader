package com.example.smsdiscountorganizer.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.domain.scan.ScanResult
import com.example.smsdiscountorganizer.domain.scan.ScanSmsUseCase
import com.example.smsdiscountorganizer.feature.offers.OfferStatus
import com.example.smsdiscountorganizer.feature.offers.statusAt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val offers: List<Offer> = emptyList(),
    val todayOffers: List<Offer> = emptyList(),
    val activeCount: Int = 0,
    val expiringTodayCount: Int = 0,
    val expiredCount: Int = 0,
    val lastScanAtMillis: Long? = null,
    val isScanning: Boolean = false,
    val message: String? = null,
    val readError: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val offerRepository: OfferRepository,
    private val scanRepository: ScanRepository,
    private val scanSms: ScanSmsUseCase,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(offerRepository.observeOffers(), scanRepository.observeScanState()) { offers, scan ->
                val now = clock.millis()
                val statuses = offers.map { it.statusAt(now, zoneId) }
                DashboardUiState(
                    isLoading = false,
                    offers = offers,
                    todayOffers = offers.filter { it.statusAt(now, zoneId) == OfferStatus.EXPIRING_TODAY },
                    activeCount = statuses.count { it == OfferStatus.ACTIVE },
                    expiringTodayCount = statuses.count { it == OfferStatus.EXPIRING_TODAY },
                    expiredCount = statuses.count { it == OfferStatus.EXPIRED },
                    lastScanAtMillis = scan?.lastSuccessfulScanAtMillis,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false, message = "بارگذاری پیشنهادها ممکن نشد.") }
            }.collect { _uiState.value = it }
        }
    }

    fun scanNow() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null, readError = null) }
            try {
                when (val result = scanSms.scan()) {
                    is ScanResult.Success -> _uiState.update {
                        it.copy(
                            isScanning = false,
                            message = "بررسی تمام شد؛ ${result.offersUpserted} پیشنهاد ذخیره شد.",
                            readError = null,
                        )
                    }
                    is ScanResult.Failure -> _uiState.update {
                        it.copy(isScanning = false, readError = result.reason.toUserMessage())
                    }
                }
            } catch (cancellation: CancellationException) {
                _uiState.update { it.copy(isScanning = false) }
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(isScanning = false, readError = "خواندن پیام‌ها ممکن نشد.") }
            }
        }
    }
}

private fun ScanResult.Reason.toUserMessage(): String = when (this) {
    ScanResult.Reason.InboxReadFailed -> "دسترسی به صندوق پیام‌ها ممکن نیست. مجوز پیامک را بررسی کنید."
    ScanResult.Reason.OfferExtractionFailed -> "پردازش پیشنهادها کامل نشد. دوباره تلاش کنید."
    ScanResult.Reason.PersistenceFailed -> "ذخیره‌سازی محلی کامل نشد."
}
