package com.example.smsdiscountorganizer.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import com.example.smsdiscountorganizer.domain.sms.GetOfferSourceTextUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    offerRepository: OfferRepository,
    private val getOfferSourceText: GetOfferSourceTextUseCase,
) : ViewModel() {
    private val sourceSmsId: Long = checkNotNull(savedStateHandle["sourceSmsId"])

    val offer = offerRepository.observeOffers()
        .map { offers -> offers.firstOrNull { it.sourceSmsId == sourceSmsId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sourceText = MutableStateFlow<OfferSourceTextState>(OfferSourceTextState.Loading)
    val sourceText = _sourceText.asStateFlow()

    init {
        viewModelScope.launch {
            offer
                .distinctUntilChangedBy { it?.sourceSmsId }
                .collectLatest { currentOffer ->
                    if (currentOffer == null) {
                        _sourceText.value = OfferSourceTextState.Unavailable
                    } else {
                        _sourceText.value = OfferSourceTextState.Loading
                        val text = try {
                            getOfferSourceText(currentOffer.sourceSmsId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        _sourceText.value = text?.let(OfferSourceTextState::Available)
                            ?: OfferSourceTextState.Unavailable
                    }
                }
        }
    }

    override fun onCleared() {
        _sourceText.value = OfferSourceTextState.Unavailable
        super.onCleared()
    }
}

sealed interface OfferSourceTextState {
    data object Loading : OfferSourceTextState
    data object Unavailable : OfferSourceTextState
    data class Available(val text: String) : OfferSourceTextState
}
