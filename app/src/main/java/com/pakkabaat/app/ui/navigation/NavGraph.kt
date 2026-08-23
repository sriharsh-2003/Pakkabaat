package com.pakkabaat.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pakkabaat.app.ui.screens.*
import com.pakkabaat.app.ui.viewmodel.SessionViewModel

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

    // SessionViewModel's init block loads any previously-saved identity into uiState
    // synchronously (SharedPreferences reads are synchronous), before this composable
    // ever runs, so reading state.value here (not the collected `state`) is safe and
    // lets returning users skip straight past splash + onboarding.
    var chosenLanguage by remember { mutableStateOf(state.myLanguage) }
    val startDestination = if (state.myName.isNotBlank()) Routes.HOME else Routes.SPLASH

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SPLASH) {
            SplashLanguageScreen(onLanguageChosen = { lang ->
                chosenLanguage = lang
                navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onContinue = { name ->
                    viewModel.setMyIdentity(name = name, language = chosenLanguage)
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
                onDeleteSession = { sessionId -> viewModel.deleteSession(sessionId) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                apiKeyStore = viewModel.apiKeyStore,
                currentName = state.myName,
                currentLanguage = state.myLanguage,
                onSaveProfile = { name, language -> viewModel.updateMyProfile(name, language) }
            )
        }

        composable(Routes.START_SESSION) {
            // Backing out here (system back, or a future explicit Cancel button) must never
            // leave advertising/discovery running or a phantom session behind.
            BackHandler {
                viewModel.cancelSessionSetup()
                navController.popBackStack()
            }
            StartSessionScreen(
                qrToken = state.qrToken,
                pairingConnected = state.pairingConnected,
                partnerName = state.partnerName,
                pairingError = state.error,
                onBecomeInitiator = { viewModel.startAsInitiator() },
                onScannedToken = { token -> viewModel.joinAsScanner(token) },
                onStartSinglePhone = { otherName -> viewModel.startSinglePhoneMode(otherName) },
                onContinueToConsent = { navController.navigate(Routes.CONSENT) },
                onDismissError = { viewModel.clearPairingError() }
            )
        }

        composable(Routes.CONSENT) {
            BackHandler {
                viewModel.cancelSessionSetup()
                navController.popBackStack(Routes.HOME, inclusive = false)
            }
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
            // Recording previously had no BackHandler at all, so a system back press
            // silently dropped out of the screen mid-recording with no confirmation and
            // no way to just keep recording while stepping away. This intercepts back,
            // asks once, and only stops if the person says so.
            var showExitConfirm by remember { mutableStateOf(false) }
            BackHandler(enabled = state.isRecording) { showExitConfirm = true }
            if (showExitConfirm) {
                AlertDialog(
                    onDismissRequest = { showExitConfirm = false },
                    title = { Text("End recording?") },
                    text = { Text("Going back won't stop the recording. Do you want to end it now, or keep recording?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitConfirm = false
                            viewModel.requestStop()
                        }) { Text("End recording") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirm = false }) { Text("Keep recording") }
                    }
                )
            }
            RecordingScreen(
                elapsedSeconds = state.elapsedSeconds,
                stopRequestedByMe = state.stopRequestedByMe,
                stopRequestedByOtherName = state.stopRequestedByOtherName,
                stopTimeoutRemaining = state.stopTimeoutRemaining,
                onStop = { viewModel.requestStop() },
                onConfirmStop = { viewModel.confirmStopRequestedByOther() }
            )
            LaunchedEffect(state.isRecording, state.processing) {
                if (!state.isRecording && state.processing) {
                    navController.navigate(Routes.PROCESSING) { popUpTo(Routes.RECORDING) { inclusive = true } }
                }
            }
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
                stopViaTimeout = state.stopViaTimeout,
                stage = state.processingStage,
                processingDeviceLabel = state.processingDeviceLabel,
                isHost = state.singlePhoneMode || state.role == com.pakkabaat.app.data.model.PartyRole.PARTY_A,
                errorMessage = state.error
            )
        }

        composable(Routes.DOCUMENT) {
            DocumentScreen(document = state.document, certificate = state.certificate, audioRecording = state.audioRecording)
        }
    }
}
