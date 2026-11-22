package com.example.smsdiscountorganizer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.scan.ScanResult
import com.example.smsdiscountorganizer.domain.scan.ScanSmsUseCase
import com.example.smsdiscountorganizer.domain.settings.ThemeMode
import com.example.smsdiscountorganizer.domain.settings.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val clearMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val offerRepository: OfferRepository,
    private val scanSms: ScanSmsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.observeThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themeRepository.saveThemeMode(mode) }
    }

    fun scanNow() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanMessage = null) }
            try {
                val message = when (val result = scanSms.scan()) {
                    is ScanResult.Success -> "بررسی تمام شد؛ ${result.offersUpserted} پیشنهاد ذخیره شد."
                    is ScanResult.Failure -> when (result.reason) {
                        ScanResult.Reason.InboxReadFailed -> "دسترسی به صندوق پیام‌ها ممکن نیست. مجوز را بررسی کنید."
                        ScanResult.Reason.OfferExtractionFailed -> "پردازش پیشنهادها کامل نشد. دوباره تلاش کنید."
                        ScanResult.Reason.PersistenceFailed -> "ذخیره‌سازی محلی کامل نشد."
                    }
                }
                _uiState.update { it.copy(isScanning = false, scanMessage = message) }
            } catch (cancellation: CancellationException) {
                _uiState.update { it.copy(isScanning = false) }
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(isScanning = false, scanMessage = "بررسی پیام‌ها ممکن نشد.") }
            }
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            try {
                offerRepository.clearAllLocalData()
                _uiState.update { it.copy(clearMessage = "همه داده‌های محلی پاک شد.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(clearMessage = "پاک‌کردن داده‌ها ممکن نشد.") }
            }
        }
    }

    fun clearTransientMessages() = _uiState.update { it.copy(scanMessage = null, clearMessage = null) }
}
