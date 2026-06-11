package com.yomu.app.ui.credits

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CreditsUiState(
    val balance: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class CreditsViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(CreditsUiState())
    val uiState: StateFlow<CreditsUiState> = _uiState.asStateFlow()
}
