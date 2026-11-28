package com.yomu.app.ui.home

import android.content.SharedPreferences
import com.yomu.app.service.LanguageSwitch
import com.yomu.app.service.ModelSlotSelection
import com.yomu.core.TargetLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Setup's first step (#547): the language the reader reads in, which decides the translator in the kit. */
internal class SetupLanguage(
    private val slotSelection: ModelSlotSelection,
    private val preferences: SharedPreferences,
    private val state: MutableStateFlow<HomeUiState>
) {
    /** Whether an earlier run of setup got past this step, so a return to setup doesn't ask again. */
    fun wasChosen(): Boolean = preferences.getBoolean(CHOSEN, false)

    /**
     * Sets [language]. A translator that writes English only gives way to the one the language offer names, without
     * asking: the kit is setup's to choose, and the reader can still change it in the next step.
     */
    suspend fun choose(language: TargetLanguage) {
        val switch = slotSelection.switchLanguage(language)
        if (switch is LanguageSwitch.Offer) slotSelection.accept(switch, state.value.fitBudget.totalMemBytes)
    }

    fun confirm() {
        preferences.edit().putBoolean(CHOSEN, true).apply()
        state.update { it.copy(setupLanguageChosen = true) }
    }

    /** Opens the step again for this visit; the language stays as it is until another is chosen. */
    fun reopen() = state.update { it.copy(setupLanguageChosen = false) }

    private companion object {
        const val CHOSEN = "setup_language_chosen"
    }
}
