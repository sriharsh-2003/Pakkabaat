package com.pakkabaat.app.data.repository

import kotlin.random.Random

/**
 * Spec section 8.1 / 12: "OTP / phone auth — Firebase Phone Auth or MSG91."
 *
 * Actually sending an SMS requires your own Firebase project or MSG91 account (paid,
 * needs its own API keys and a verified sender ID) — nothing this build can stand up
 * without your credentials. So this ships a dev-mode OTP that behaves identically from
 * the UI's point of view (request code -> enter code -> verified) but shows the code
 * on-screen instead of texting it, clearly labeled as such.
 *
 * TO GO LIVE: replace `requestOtp`/`verifyOtp` with calls to
 * FirebaseAuth.getInstance().verifyPhoneNumber(...) (com.google.firebase:firebase-auth),
 * or MSG91's REST OTP API. No other file needs to change — everything else talks to
 * this class only through the two methods below.
 */
class OtpService {
    private val issuedCodes = mutableMapOf<String, String>()

    /** Returns the code that was "sent", so dev-mode UI can display it. Replace return value handling when wiring real SMS. */
    fun requestOtp(phoneNumber: String): String {
        val code = (100000 + Random.nextInt(900000)).toString()
        issuedCodes[phoneNumber] = code
        return code
    }

    fun verifyOtp(phoneNumber: String, enteredCode: String): Boolean {
        return issuedCodes[phoneNumber] == enteredCode
    }
}
