package com.example.smsdiscountorganizer.feature.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OffersUiState(
    val isLoading: Boolean = true,
    val offers: List<Offer> = emptyList(),
    val visibleOffers: List<Offer> = emptyList(),
    val query: String = "",
    val statusFilter: OfferStatusFilter = OfferStatusFilter.ALL,
    val merchants: List<String> = emptyList(),
    val selectedMerchant: String? = null,
    val expirationAscending: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class OffersViewModel @Inject constructor(
    private val offerRepository: OfferRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OffersUiState())
    val uiState: StateFlow<OffersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            offerRepository.observeOffers()
                .catch { _uiState.update { state -> state.copy(isLoading = false, error = "بارگذاری پیشنهادها ممکن نشد.") } }
                .collect(::setOffers)
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query).filtered() }
    fun setStatusFilter(filter: OfferStatusFilter) = _uiState.update { it.copy(statusFilter = filter).filtered() }
    fun setMerchant(merchant: String?) = _uiState.update { it.copy(selectedMerchant = merchant).filtered() }
    fun toggleExpirationSort() = _uiState.update { it.copy(expirationAscending = !it.expirationAscending).filtered() }

    private fun setOffers(offers: List<Offer>) = _uiState.update { state ->
        state.copy(
            isLoading = false,
            offers = offers,
            merchants = offers.mapNotNull(Offer::merchantName).distinct().sorted(),
            error = null,
        ).filtered()
    }

    private fun OffersUiState.filtered(): OffersUiState = copy(
        visibleOffers = filterOffers(
            offers = offers,
            query = query,
            status = statusFilter,
            merchant = selectedMerchant,
            nowMillis = clock.millis(),
            zoneId = zoneId,
            expirationAscending = expirationAscending,
        ),
    )
}
