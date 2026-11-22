package com.example.smsdiscountorganizer.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.domain.offer.Offer
import com.example.smsdiscountorganizer.domain.offer.OfferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    offerRepository: OfferRepository,
) : ViewModel() {
    private val sourceSmsId: Long = checkNotNull(savedStateHandle["sourceSmsId"])

    val offer = offerRepository.observeOffers()
        .map { offers -> offers.firstOrNull { it.sourceSmsId == sourceSmsId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
