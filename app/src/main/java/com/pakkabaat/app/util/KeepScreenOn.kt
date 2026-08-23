package com.pakkabaat.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** LocalContext.current in Compose is often a ContextWrapper (e.g. the Activity's
 *  Compose-specific wrapper), not the Activity itself — a direct `as? Activity` cast
 *  on it silently returns null and the FLAG_KEEP_SCREEN_ON call never happens, which
 *  is exactly the "I asked for this and the screen still sleeps" bug. Unwrap until we
 *  hit the real Activity. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

/**
 * Keeps the device screen on for as long as the composable calling this stays in
 * composition (recording, processing, and QR pairing all involve the user staring at
 * the screen for a while with no touch input — without this, Android's normal
 * screen-timeout kicks in mid-recording or mid-processing). Removes the flag again on
 * dispose so we don't leave the whole app burning battery once the user has moved on
 * (e.g. to the finished document, which they may just leave open).
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
