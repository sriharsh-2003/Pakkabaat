package com.pakkabaat.app.consent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/**
 * Spec section 8.5: "if the other party doesn't respond within 60 seconds, the requester
 * can stop unilaterally." This emits the remaining whole seconds once per second, then a
 * final value of 0 to signal the timeout has elapsed. The caller (SessionViewModel) is
 * responsible for cancelling this flow's collection if a StopConfirm arrives first.
 */
const val STOP_CONFIRM_TIMEOUT_SECONDS = 60

fun stopTimeoutCountdown() = flow {
    for (remaining in STOP_CONFIRM_TIMEOUT_SECONDS downTo 0) {
        emit(remaining)
        if (remaining > 0) delay(1000)
    }
}
