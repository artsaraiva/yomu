package com.yomu.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.yomu.app.ui.theme.rememberReduceMotion
import androidx.navigation.NavGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yomu.app.ui.theme.ChromeContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yomu.app.translation.AppDestination
import com.yomu.app.translation.NetworkTranslatorEntries
import com.yomu.app.ui.home.HomeViewModel
import com.yomu.app.ui.home.SetupScreen
import com.yomu.app.ui.theme.PaperLoading
import com.yomu.app.ui.home.HomeScreen
import com.yomu.app.ui.home.RecentScreen
import androidx.navigation.compose.navigation
import com.yomu.app.ui.settings.AdvancedSettings
import com.yomu.app.ui.settings.AppearanceSettings
import com.yomu.app.ui.settings.SettingsScreen
import com.yomu.app.ui.settings.LanguageOfferDialog
import com.yomu.app.ui.settings.NameGlossarySettings
import com.yomu.app.ui.settings.SettingsSection
import com.yomu.app.ui.settings.SettingsViewModel
import com.yomu.app.ui.settings.TranslationSettings
import com.yomu.app.ui.settings.TranslatorSettings
import com.yomu.app.ui.settings.TranslatorSide
import com.yomu.app.ui.settings.TypesettingSettings
import com.yomu.core.TargetLanguage

private const val SETTINGS_ROOT = "settings/root"

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val RECENT = "recent"
private const val PHONE_MODELS = "settings/translator/phone"

@Composable
fun AppNavigation(
    onRequestScreenCapture: () -> Unit = {},
    languageRequest: TargetLanguage? = null,
    onLanguageRequestHandled: () -> Unit = {},
    routeRequest: String? = null,
    onRouteRequestHandled: () -> Unit = {},
    translatorScreens: Set<TranslatorScreen> = emptySet()
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    // The overlay's language switch opens the app when the choice needs the model offer (#338).
    LaunchedEffect(languageRequest) {
        languageRequest?.let(viewModel::chooseLanguage)
        onLanguageRequestHandled()
    }
    state.languageOffer?.let {
        LanguageOfferDialog(it, state.targetLanguage, viewModel::acceptLanguageOffer, viewModel::declineLanguageOffer)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshEnvironment()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var setupRoute by rememberSaveable { mutableStateOf<String?>(null) }
    if (state.modelsLoading || state.setupVisible) {
        ChromeContent(Modifier.safeDrawingPadding()) {
            // A translator's screen setup opened, such as the server's (#536); closing it returns to setup.
            val destination = setupRoute?.let(::AppDestination)
            when {
                state.modelsLoading -> PaperLoading("Getting Yomu ready…")
                destination != null -> {
                    BackHandler { setupRoute = null }
                    SetupTranslatorScreen(translatorScreens, destination) { setupRoute = null }
                }
                else -> SetupScreen(state, viewModel, onRequestScreenCapture) { setupRoute = it.route }
            }
        }
        return
    }
    val navController = rememberNavController()
    val reduceMotion = rememberReduceMotion()
    // Every destination a translator names opens here; one no screen claims is dropped, since a status line's comes from
    // outside the app (#534).
    val openDestination: (AppDestination) -> Unit = {
        if (navController.graph.hasRoute(it.route)) navController.navigate(it.route) { launchSingleTop = true }
    }
    // Never past Home: a second tap on a back arrow mid-transition would otherwise pop Home and leave nothing drawn.
    val backToHome: () -> Unit = { navController.popBackStack(HOME, inclusive = false) }
    // No navigation bar: Home is the app, Settings and the whole of Recent open from it and return with back (#546).
    ChromeContent(Modifier.safeDrawingPadding()) {
        NavHost(
            navController = navController,
            startDestination = HOME,
            enterTransition = {
                if (reduceMotion) fadeIn(tween(180))
                else fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 12 }
            },
            exitTransition = {
                if (reduceMotion) fadeOut(tween(180))
                else fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it / 12 }
            }
        ) {
            composable(HOME) {
                HomeScreen(
                    viewModel, hiltViewModel(), onRequestScreenCapture,
                    onOpenSettings = { navController.navigate(SETTINGS) { launchSingleTop = true } },
                    // Home asks for a section only from its phone models' "More models" row, so Translator opens on that side.
                    onOpenSection = {
                        navController.navigate(if (it == SettingsSection.Translator) PHONE_MODELS else it.route) { launchSingleTop = true }
                    },
                    onOpenRecent = { navController.navigate(RECENT) { launchSingleTop = true } },
                    onOpenDestination = openDestination
                )
            }
            composable(RECENT) { RecentScreen(viewModel, backToHome) }
            navigation(startDestination = SETTINGS_ROOT, route = SETTINGS) {
                composable(SETTINGS_ROOT) { entry ->
                    val parent = remember(entry) { navController.getBackStackEntry(SETTINGS) }
                    val settings: SettingsViewModel = hiltViewModel(parent)
                    SettingsScreen(
                        translatorRows = remember(settings) { settings.translatorRows() },
                        onBack = backToHome,
                        onOpen = { navController.navigate(it.route) { launchSingleTop = true } },
                        onOpenDestination = openDestination
                    )
                }
                // Inside Settings the server's screen is the Translator page turned to its server side (#548); setup,
                // which has its own step for the phone's model, shows the screen its module brings instead.
                val server = NetworkTranslatorEntries.PROVIDERS
                mapOf(server.route to TranslatorSide.Server, PHONE_MODELS to TranslatorSide.Phone).forEach { (route, side) ->
                    composable(route) { entry ->
                        val parent = remember(entry) { navController.getBackStackEntry(SETTINGS) }
                        TranslatorSettings(hiltViewModel(parent), hiltViewModel(), side) { navController.popBackStack() }
                    }
                }
                // The screens translators bring, which their picker entries, Settings rows and failures open (#534).
                translatorScreens.filterNot { it.destination == server }.forEach { screen ->
                    composable(screen.destination.route) { screen.content { navController.popBackStack() } }
                }
                SettingsSection.entries.forEach { section ->
                    composable(section.route) { entry ->
                        // One ViewModel for the whole settings graph, so a download survives leaving its sub-screen.
                        val parent = remember(entry) { navController.getBackStackEntry(SETTINGS) }
                        val settings: SettingsViewModel = hiltViewModel(parent)
                        val onBack: () -> Unit = { navController.popBackStack() }
                        when (section) {
                            SettingsSection.Translator -> TranslatorSettings(settings, hiltViewModel(), openOn = null, onBack)
                            SettingsSection.Translation -> TranslationSettings(settings, hiltViewModel(), onBack)
                            SettingsSection.NameGlossary -> NameGlossarySettings(hiltViewModel(), onBack)
                            SettingsSection.Typesetting -> TypesettingSettings(settings, onBack)
                            SettingsSection.Appearance -> AppearanceSettings(settings, onBack)
                            SettingsSection.Advanced -> AdvancedSettings(settings, onBack)
                        }
                    }
                }
            }
        }
        // A screen the overlay asked for, such as the one a status-line action opens (#534).
        LaunchedEffect(routeRequest) {
            routeRequest?.let { openDestination(AppDestination(it)) }
            onRouteRequestHandled()
        }
    }
}

/** Whether [route] names a destination anywhere in this graph, nested graphs included. */
private fun NavGraph.hasRoute(route: String): Boolean = any { it.route == route || (it as? NavGraph)?.hasRoute(route) == true }

/** The screen a translator brought for [destination], shown over setup; one no translator brought closes at once. */
@Composable
private fun SetupTranslatorScreen(screens: Set<TranslatorScreen>, destination: AppDestination, onBack: () -> Unit) {
    val screen = screens.find { it.destination == destination }
    if (screen != null) screen.content(onBack) else LaunchedEffect(destination) { onBack() }
}
