package com.yomu.app.ui.home

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomu.app.db.HistoryDao
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.LanguageSwitch
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.OverlayService
import com.yomu.app.service.PickerGroup
import com.yomu.app.service.TranslatorChoice
import com.yomu.app.translation.AppDestination
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.core.TargetLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val modelManager: ModelManager,
    private val historyDao: HistoryDao,
    private val slotSelection: ModelSlotSelection,
    private val translatorChoice: TranslatorChoice
) : ViewModel() {
    private companion object {
        const val SETUP_COMPLETE = "reading_setup_complete"
        const val HAS_READ = "has_read"
    }

    private val _uiState = MutableStateFlow(HomeUiState(
        setupComplete = sharedPreferences.getBoolean(SETUP_COMPLETE, false),
        hasRead = sharedPreferences.getBoolean(HAS_READ, false),
        deliverables = ModelType.entries.associateWith(slotSelection::deliverables),
        fitBudget = slotSelection.fitBudget(modelManager.deviceTotalMemBytes())
    ).withSlots())
    val uiState = _uiState.asStateFlow()
    private var historyJob: Job? = null
    private var countJob: Job? = null
    private var setupJob: Job? = null
    private val setupDownload = SetupDownload(modelManager, slotSelection, _uiState)

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OverlayService.ACTION_SERVICE_STARTED -> _uiState.update {
                    it.copy(isServiceRunning = true, serviceError = false, confirmation = "Reading is on")
                }
                OverlayService.ACTION_SERVICE_STOPPED -> _uiState.update { it.copy(isServiceRunning = false) }
                OverlayService.ACTION_SERVICE_START_FAILED -> _uiState.update {
                    it.copy(isServiceRunning = false, serviceError = true)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(context, serviceStateReceiver, IntentFilter().apply {
            addAction(OverlayService.ACTION_SERVICE_STARTED)
            addAction(OverlayService.ACTION_SERVICE_STOPPED)
            addAction(OverlayService.ACTION_SERVICE_START_FAILED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            try {
                modelManager.refreshModelList()
                modelManager.getAllModels().collect { models ->
                    slotSelection.commitPending(models.associate { it.id to it.status })
                    _uiState.update { it.copy(models = models).withSlots() }
                    refreshReadiness()
                    _uiState.update { state ->
                        state.copy(
                            setupVisible = state.setupVisible ||
                                (state.modelsLoading && (!state.setupComplete || state.readiness != Readiness.Ready)),
                            modelsLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(modelsLoading = false, setupVisible = true, setupError = true) }
            }
        }
        loadRecentHistory()
        refreshEnvironment()
    }

    private fun refreshReadiness() = _uiState.update { it.withSlots() }

    @Suppress("DEPRECATION")
    fun refreshEnvironment() {
        refreshReadiness()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val running = manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == OverlayService::class.java.name && it.foreground
        }
        _uiState.update {
            it.withSlots().copy(
                isServiceRunning = running,
                onMeteredNetwork = modelManager.isOnMeteredNetwork(),
                fitBudget = slotSelection.fitBudget(it.fitBudget.totalMemBytes)
            )
        }
        countJob?.cancel()
        countJob = viewModelScope.launch {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            historyDao.getTranslationCountSince(start)
                .catch { _uiState.update { it.copy(historyError = true) } }
                .collect { count -> _uiState.update { it.copy(pagesTranslatedToday = count) } }
        }
    }

    /** Re-reads the picks, and readiness with them, which Settings and Providers change while Home is off screen. */
    fun refreshSlots() = _uiState.update { it.withSlots() }

    fun openSetup() = _uiState.update { it.copy(setupVisible = true) }

    fun dismissSetup() = _uiState.update { it.copy(setupVisible = false) }

    /** Setup hands the pages to [translatorId] in place of the phone model, which it then skips; null takes the phone model back. */
    fun setupWith(translatorId: String?) = _uiState.update { it.copy(setupTranslatorId = translatorId) }

    /** Setup only records the choice; its Download button fetches every slot's choice at once. */
    fun pickSetupModel(type: ModelType, id: String) {
        viewModelScope.launch {
            slotSelection.pick(type, id, modelManager.getModel(id)?.status, _uiState.value.fitBudget.totalMemBytes)
            _uiState.update { it.withSlots() }
        }
    }

    /**
     * Home's quick pick of a phone model, which the phone then translates with. Unlike setup, a model that isn't
     * downloaded starts downloading and takes over when READY.
     */
    fun pickTranslationModel(id: String) {
        viewModelScope.launch {
            val status = modelManager.getModel(id)?.status
            if (!translatorChoice.pickPhoneModel(id, status, _uiState.value.fitBudget.totalMemBytes)) return@launch
            downloadIfPending(id)
        }
    }

    /**
     * A translator's entry from the picker. Returns the screen to open when the translator names one, else null;
     * a pick that asks first waits on the reader in [HomeUiState.translatorConfirm].
     */
    fun pickTranslatorEntry(group: PickerGroup, entry: TranslatorPickerEntry): AppDestination? =
        settle(group.translatorId, translatorChoice.pick(group.translatorId, entry.pick))

    /** The reader agreed to the waiting pick; returns the screen to open when the translator names one, else null. */
    fun acceptTranslatorConfirm(): AppDestination? {
        val waiting = _uiState.value.translatorConfirm ?: return null
        return settle(waiting.translatorId, translatorChoice.pick(waiting.translatorId, waiting.confirm.accept))
    }

    fun declineTranslatorConfirm() = _uiState.update { it.copy(translatorConfirm = null) }

    private fun settle(translatorId: String, pick: TranslatorPick): AppDestination? {
        val confirm = (pick as? TranslatorPick.Confirm)?.let { TranslatorConfirm(translatorId, it) }
        _uiState.update { it.copy(translatorConfirm = confirm).withSlots() }
        return (pick as? TranslatorPick.Open)?.destination
    }

    /** From the quick-settings chip here, or from the overlay's when its switch needs the model offer (#338). */
    fun chooseLanguage(language: TargetLanguage) {
        viewModelScope.launch {
            val switch = slotSelection.switchLanguage(language)
            _uiState.update { it.copy(languageOffer = switch as? LanguageSwitch.Offer).withSlots() }
        }
    }

    fun acceptLanguageOffer() {
        val offer = _uiState.value.languageOffer ?: return
        _uiState.update { it.copy(languageOffer = null) }
        viewModelScope.launch {
            if (slotSelection.accept(offer, _uiState.value.fitBudget.totalMemBytes)) downloadIfPending(offer.model.id)
        }
    }

    fun declineLanguageOffer() = _uiState.update { it.copy(languageOffer = null) }

    private suspend fun downloadIfPending(id: String) {
        _uiState.update { it.withSlots() }
        if (slotSelection.pendingId(ModelType.LLM) != id) return
        slotSelection.download(id)
        _uiState.update { it.withSlots() }
    }

    fun deleteModel(id: String) {
        viewModelScope.launch {
            slotSelection.delete(id)
            _uiState.update { it.withSlots() }
        }
    }

    /** The picks, and readiness with them: whether the phone model is needed follows the translator that takes the pages (#536). */
    private fun HomeUiState.withSlots(): HomeUiState {
        val phoneTranslates = translatorChoice.phoneTranslates()
        val ready = resolveReadiness(
            models.associate { it.id to it.status },
            slotSelection.selectedId(ModelType.DETECTION), slotSelection.selectedId(ModelType.OCR), slotSelection.selectedId(ModelType.LLM),
            Settings.canDrawOverlays(context), phoneTranslates
        )
        return copy(
            readiness = ready,
            phoneTranslates = phoneTranslates,
            // Losing readiness brings setup back, such as the server's address changing with no phone model.
            setupVisible = setupVisible || (readiness == Readiness.Ready && ready != Readiness.Ready),
            chosenIds = ModelType.entries.associateWith(slotSelection::chosenId),
            translationSelectedId = slotSelection.selectedId(ModelType.LLM),
            translationPendingId = slotSelection.pendingId(ModelType.LLM),
            // With the slots, since a download that never completes can return the language to English.
            targetLanguage = slotSelection.targetLanguage(),
            // Read from the stored settings, so opening the picker never contacts the server.
            translatorGroups = translatorChoice.pickerGroups()
        )
    }

    fun prepareSetup() {
        if (setupJob?.isActive == true) return
        setupJob = viewModelScope.launch {
            if (!setupDownload.run()) return@launch
            refreshReadiness()
            _uiState.update { it.copy(setupDownloadsReady = true) }
        }
    }

    fun completeSetup() {
        refreshReadiness()
        if (_uiState.value.readiness != Readiness.Ready || !_uiState.value.setupDownloadsReady) return
        sharedPreferences.edit().putBoolean(SETUP_COMPLETE, true).apply()
        _uiState.update { it.copy(setupComplete = true, setupVisible = false, setupStarted = false) }
    }

    fun loadRecentHistory() {
        historyJob?.cancel()
        _uiState.update { it.copy(historyLoading = true, historyError = false) }
        historyJob = viewModelScope.launch {
            historyDao.getAllTranslations()
                .catch { _uiState.update { it.copy(historyLoading = false, historyError = true) } }
                .collect { translations ->
                    if (translations.isNotEmpty() && !_uiState.value.hasRead) {
                        sharedPreferences.edit().putBoolean(HAS_READ, true).apply()
                    }
                    _uiState.update {
                        it.copy(translations = translations, hasRead = it.hasRead || translations.isNotEmpty(), historyLoading = false)
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyDao.clearHistory()
                _uiState.update { it.copy(confirmation = "Recent translations cleared", clearError = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(clearError = true) }
            }
        }
    }

    fun dismissConfirmation() = _uiState.update { it.copy(confirmation = null) }

    fun dismissServiceError() = _uiState.update { it.copy(serviceError = false) }

    fun stopService() {
        OverlayService.stop(context)
    }

    override fun onCleared() {
        context.unregisterReceiver(serviceStateReceiver)
        super.onCleared()
    }
}
