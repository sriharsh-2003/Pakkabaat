package com.pakkabaat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pakkabaat.app.ui.screens.*
import com.pakkabaat.app.ui.viewmodel.SessionViewModel
import java.util.UUID

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val START_SESSION = "start_session"
    const val CONSENT = "consent"
    const val RECORDING = "recording"
    const val PROCESSING = "processing"
    const val DOCUMENT = "document"
    const val SETTINGS = "settings"
}

@Composable
fun PakkaBaatNavGraph() {
    val navController = rememberNavController()
    val viewModel: SessionViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val pastSessions by viewModel.pastSessions.collectAsState(initial = emptyList())

    var chosenLanguage by remember { mutableStateOf("hi") }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashLanguageScreen(onLanguageChosen = { lang ->
                chosenLanguage = lang
                navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                otpService = viewModel.otpService,
                onVerified = { _, name ->
                    viewModel.setMyIdentity(userId = UUID.randomUUID().toString(), name = name, language = chosenLanguage)
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                sessions = pastSessions,
                onRecordNew = { navController.navigate(Routes.START_SESSION) },
                onOpenSession = { sessionId ->
                    viewModel.loadExistingSession(sessionId)
                    navController.navigate(Routes.DOCUMENT)
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(apiKeyStore = viewModel.apiKeyStore)
        }

        composable(Routes.START_SESSION) {
            // Auto-advance once both this device's consent state machine is ready.
            LaunchedEffect(state.pairingConnected) {
                // no-op; navigation to consent is via the explicit "Continue" tap below
            }
            StartSessionScreen(
                qrToken = state.qrToken,
                pairingConnected = state.pairingConnected,
                partnerName = state.partnerName,
                onBecomeInitiator = { viewModel.startAsInitiator() },
                onScannedToken = { token -> viewModel.joinAsScanner(token) },
                onContinueToConsent = { navController.navigate(Routes.CONSENT) }
            )
        }

        composable(Routes.CONSENT) {
            LaunchedEffect(state.isRecording) {
                if (state.isRecording) navController.navigate(Routes.RECORDING) { popUpTo(Routes.CONSENT) { inclusive = true } }
            }
            ConsentScreen(
                partnerName = state.partnerName,
                myConsent = state.myConsent,
                otherConsent = state.otherConsent,
                onAgree = { viewModel.giveConsentToStart() }
            )
        }

        composable(Routes.RECORDING) {
            LaunchedEffect(state.isRecording, state.processing) {
                if (!state.isRecording && state.processing) {
                    navController.navigate(Routes.PROCESSING) { popUpTo(Routes.RECORDING) { inclusive = true } }
                }
            }
            RecordingScreen(
                elapsedSeconds = state.elapsedSeconds,
                stopRequestedByMe = state.stopRequestedByMe,
                stopRequestedByOtherName = state.stopRequestedByOtherName,
                stopTimeoutRemaining = state.stopTimeoutRemaining,
                onStop = { viewModel.requestStop() },
                onConfirmStop = { viewModel.confirmStopRequestedByOther() }
            )
        }

        composable(Routes.PROCESSING) {
            LaunchedEffect(state.document) {
                if (state.document != null) {
                    navController.navigate(Routes.DOCUMENT) { popUpTo(Routes.PROCESSING) { inclusive = true } }
                }
            }
            ProcessingScreen(
                draftTranscript = state.draftTranscript,
                isOnline = state.isOnline,
                stopViaTimeout = state.stopViaTimeout
            )
        }

        composable(Routes.DOCUMENT) {
            DocumentScreen(document = state.document, certificate = state.certificate)
        }
    }
}
