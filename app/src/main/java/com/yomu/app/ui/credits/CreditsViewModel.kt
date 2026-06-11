package com.yomu.app.ui.credits

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class CreditsUiState(
    val balance: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class CreditsViewModel @Inject constructor() : ViewModel() {

    var uiState: CreditsUiState = CreditsUiState()
        private set

    fun purchaseCredits(packageId: String) {
    }
}
