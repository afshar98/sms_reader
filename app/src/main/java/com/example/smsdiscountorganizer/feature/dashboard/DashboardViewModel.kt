package com.example.smsdiscountorganizer.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanRepository
import com.example.smsdiscountorganizer.domain.scan.ScanProgress
import com.example.smsdiscountorganizer.domain.scan.ScanResult
import com.example.smsdiscountorganizer.domain.scan.ScanSmsUseCase
import com.example.smsdiscountorganizer.domain.scan.SmsDateRangePreset
import com.example.smsdiscountorganizer.domain.scan.SmsDateRangeSelection
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
import kotlinx.coroutines.Job

data class DashboardUiState(
    val isLoading: Boolean = true,
    val offers: List<Offer> = emptyList(),
    val todayOffers: List<Offer> = emptyList(),
    val activeCount: Int = 0,
    val expiringTodayCount: Int = 0,
    val expiredCount: Int = 0,
    val lastScanAtMillis: Long? = null,
    val isScanning: Boolean = false,
    val progress: ScanProgress = ScanProgress(),
    val message: String? = null,
    val readError: String? = null,
    val dateRangeSelection: SmsDateRangeSelection = SmsDateRangeSelection(),
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
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            combine(offerRepository.observeOffers(), scanRepository.observeScanState(), scanSms.progress) { offers, scan, progress ->
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
                    isScanning = progress.isScanning,
                    progress = progress,
                    dateRangeSelection = _uiState.value.dateRangeSelection,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false, message = "بارگذاری پیشنهادها ممکن نشد.") }
            }.collect { _uiState.value = it }
        }
    }

    fun cancelScan() {
        scanJob?.cancel(CancellationException("User cancelled Inbox scan"))
    }

    fun scanNow(fullRescan: Boolean = false) {
        if (_uiState.value.isScanning) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null, readError = null) }
            try {
                when (val result = scanSms.scan(fullRescan, _uiState.value.dateRangeSelection)) {
                    is ScanResult.Success -> _uiState.update {
                        it.copy(
                            isScanning = false,
                            message = "بررسی تمام شد؛ ${result.messagesScanned} پیام دیده شد، ${result.messagesUnchanged} پیام تغییری نداشت و ${result.offersUpserted} پیشنهاد درج یا به‌روزرسانی شد.",
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

    fun selectDateRange(preset: SmsDateRangePreset) {
        _uiState.update { it.copy(dateRangeSelection = SmsDateRangeSelection(preset = preset)) }
    }

    fun selectCustomDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate) {
        _uiState.update {
            it.copy(
                dateRangeSelection = SmsDateRangeSelection(
                    preset = SmsDateRangePreset.CUSTOM,
                    customStartDate = startDate,
                    customEndDate = endDate,
                ),
            )
        }
    }
}

private fun ScanResult.Reason.toUserMessage(): String = when (this) {
    ScanResult.Reason.InboxReadFailed -> "دسترسی به صندوق پیام‌ها ممکن نیست. مجوز پیامک را بررسی کنید."
    ScanResult.Reason.PermissionDenied -> "مجوز خواندن پیامک فعال نیست."
    ScanResult.Reason.OfferExtractionFailed -> "پردازش پیشنهادها کامل نشد. دوباره تلاش کنید."
    ScanResult.Reason.PersistenceFailed -> "ذخیره‌سازی محلی کامل نشد."
    ScanResult.Reason.InvalidDateRange -> "بازه انتخابی معتبر نیست. تاریخ شروع و پایان را بررسی کنید."
}
