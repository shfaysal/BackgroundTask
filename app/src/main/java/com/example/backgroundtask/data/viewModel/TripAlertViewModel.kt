package com.example.backgroundtask.data.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backgroundtask.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripUiState(
    val tripId: Int = 0,
    val pickupLocation: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface TripAlertEffect {
    data class NavigateToAcceptedRide(val tripId: Int) : TripAlertEffect
    object DismissAlert : TripAlertEffect
}

@HiltViewModel
class TripAlertViewModel @Inject constructor(
    private val repository: TripRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val tripId: Int = savedStateHandle["trip_id"] ?: 0
    val pickupLocation: String = savedStateHandle["pickup_location"] ?: "Unknown Location"

    private val _uiState = MutableStateFlow(TripUiState(tripId = tripId, pickupLocation = pickupLocation))
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TripAlertEffect>()
    val effects: SharedFlow<TripAlertEffect> = _effects.asSharedFlow()

    fun acceptTrip() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.acceptTrip(tripId)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.emit(TripAlertEffect.NavigateToAcceptedRide(tripId))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.localizedMessage ?: "Failed to accept trip")
                    }
                }
        }
    }

    fun declineTrip() {
        viewModelScope.launch {
            repository.declineTrip(tripId)
            _effects.emit(TripAlertEffect.DismissAlert)
        }
    }
}