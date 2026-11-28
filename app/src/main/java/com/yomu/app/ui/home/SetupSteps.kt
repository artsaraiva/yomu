package com.yomu.app.ui.home

/** Setup's steps, in the order the stepper lists them (#547). */
enum class SetupStepId { Language, Kit, Permission }

/** How the stepper draws a step: collapsed to one line, open in place, or lying flat until its turn. */
enum class SetupPhase { Done, Active, Later }

/**
 * The kit is in once every chosen model is downloaded and takes the pages. A translator chosen in place of the phone
 * model keeps the step open until it is set up (#536).
 */
private val HomeUiState.setupKitDone: Boolean
    get() = setupKit().downloaded && readiness.modelsReady && (setupTranslatorGroup() == null || !phoneTranslates)

/** Each step's phase: the first one not done is the open one. */
fun HomeUiState.setupPhases(): Map<SetupStepId, SetupPhase> {
    val done = mapOf(
        SetupStepId.Language to setupLanguageChosen,
        SetupStepId.Kit to setupKitDone,
        SetupStepId.Permission to readiness.hasOverlayPermission
    )
    val active = SetupStepId.entries.firstOrNull { !done.getValue(it) }
    return SetupStepId.entries.associateWith {
        when {
            done.getValue(it) -> SetupPhase.Done
            it == active -> SetupPhase.Active
            else -> SetupPhase.Later
        }
    }
}

/** True when nothing is left but to start reading. */
val HomeUiState.setupAllDone: Boolean get() = setupPhases().values.all { it == SetupPhase.Done }
