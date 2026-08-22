# PakkaBaat — Android MVP

This is a working Android Studio project implementing the MVP scope from the product
spec (§15): in-person recording via Nearby Connections + QR pairing, mutual consent to
start/stop with the 60-second timeout, local-first storage (Room), SHA-256 tamper
evidence, Bhashini ASR + Gemini structuring in the cloud, and PDF export.

Both cloud APIs used here are **free, no credit card required**: Bhashini (government,
free by design) and Google's Gemini API free tier (via Google AI Studio).

## What's real vs. stubbed

| Piece | Status |
|---|---|
| Onboarding, home screen, session list | Fully working |
| QR pairing + Nearby Connections (Bluetooth/Wi-Fi Direct) | Fully working, no server involved |
| Mutual consent to start/stop, 60s timeout | Fully working |
| Audio recording (16kHz mono WAV) + SHA-256 hash | Fully working |
| On-device draft transcript | **Stub.** Returns a placeholder string. Wiring in real offline speech-to-text (whisper.cpp or Vosk) needs native model binaries this environment couldn't fetch or compile — see the comments in `recording/OnDeviceTranscriber.kt` for exact integration steps. Everything downstream of it works either way. |
| Bhashini ASR (cloud) | Fully implemented, needs your free API credentials |
| Structuring (the §10.1 prompt, via Gemini) | Fully implemented, needs your free Gemini API key |
| PDF export, proof details | Fully working |
| Remote (non-in-person) pairing, cloud bridge calling | Not built — these are Phase 2/3/4 per the spec's own roadmap |
| Phone OTP | **Dev-mode mock.** Shows the code on-screen instead of texting it (see `data/repository/OtpService.kt` for how to swap in Firebase Phone Auth or MSG91) |

## 1. Get your API keys (both genuinely free, no card)

- **Bhashini** (ASR): register at the [Bhashini API docs](https://bhashini.gitbook.io/bhashini-apis)
  → "Pre-requisites and Onboarding" page → follow its registration link → verify your
  email → log in → **My Profile** → note your **User ID** and hit **Generate** for an
  **API Key**.
- **Gemini** (structuring): go to [aistudio.google.com](https://aistudio.google.com/apikey),
  sign in with any Google account, click **Create API key**. No card, no expiry, just a
  rate limit (plenty for personal use/testing).

Open `gradle.properties` in the project root and fill in:

```properties
PAKKABAAT_BHASHINI_USER_ID=your_bhashini_user_id
PAKKABAAT_BHASHINI_API_KEY=your_bhashini_api_key
PAKKABAAT_GEMINI_API_KEY=your_gemini_api_key
```

You can skip this at first — the app runs fine without keys, it'll just fail with a
clear error at the very last "generate the official document" step.

## 2. Open and build the project

**You need a PC or Mac (not the phone) to build this** — Android apps are compiled on
a desktop with Android Studio, then installed onto a phone. The phone is where you
*run and test* it, the computer is where you *build* it. (If you'd rather skip this
entirely, see "Getting an APK without installing Android Studio" below.)

1. Install [Android Studio](https://developer.android.com/studio) (free) if you don't have it.
2. `File → Open`, select the `PakkaBaat` folder (the one with `settings.gradle.kts`).
3. Let it sync — Android Studio will notice there's no Gradle wrapper jar and offer to
   generate one automatically; accept that. First sync will download dependencies, so
   make sure the PC has internet access.
4. If you added API keys above, they'll be picked up automatically on the next build.

## 3. Install it on a phone

**Should you test on your phone or on the PC? On your phone (or two phones).** The app
only runs on Android — the PC is just the workbench. The Android Studio emulator can
run it too, but this app's core feature (pairing two phones over Bluetooth/Wi-Fi
Direct) needs real radios, so a real phone is the better choice, and you'll want
**two** phones to test the actual back-and-forth.

**To install on a real phone:**
1. On the phone: Settings → About phone → tap "Build number" 7 times to unlock Developer
   options → go back → Developer options → enable **USB debugging**.
2. Connect the phone to the PC with a USB cable (accept the "Allow USB debugging?"
   prompt on the phone).
3. In Android Studio, pick your phone from the device dropdown (top toolbar) and click
   **Run ▶**. It installs and launches automatically.
4. To test with a second phone, repeat steps 1–3 with that phone plugged in instead
   (or use **Build → Build App Bundle(s)/APK(s) → Build APK(s)**, then copy the
   resulting `.apk` file to the second phone via USB/Drive/WhatsApp and open it there
   to sideload — you may need to allow "install from unknown sources" for that one time).

## 4. Testing the actual flow

**You genuinely need two Android phones sitting next to each other** to test the
core feature, because it's a two-person pairing app:

1. Install the app on both phones and complete onboarding (name + phone number; the
   OTP will show on-screen since it's dev-mode).
2. On **Phone A**: Home → "Record a new agreement" → grant the permissions it asks for
   (mic, camera, nearby devices) → "Show my code". A QR code appears.
3. On **Phone B**: same screen → grant permissions → "Scan the other person's code" →
   point B's camera at A's screen.
4. Both phones should show "Connected" within a few seconds (this is Bluetooth/Wi-Fi
   Direct pairing, no internet needed) → tap Continue on both.
5. Both phones show the consent screen → both tap "I agree" → recording starts on both
   simultaneously.
6. Talk out a sample agreement out loud (e.g. "Ramesh is lending Suresh ₹5000, to be
   repaid in a month").
7. Tap Stop on either phone → confirm on the other (or wait 60 seconds to see the
   auto-stop timeout fire).
8. Each phone shows its own on-device draft immediately (currently the placeholder
   text — see the table above), then — if you added API keys and have internet —
   produces the real structured document within a few seconds. Without keys, you'll
   see a clear error instead of a silent hang.
9. Tap "Download PDF" to see the exported record with the proof-details hash section.

**If you only have one phone:** you can still test onboarding, permissions, QR
display, and (with API keys set) manually verify the Bhashini/Gemini network calls
work by checking Logcat — but you won't be able to complete a full session, since it
always needs a second, physically separate device to pair with.

## Known rough edges in this build

- The on-device draft transcript is a placeholder (see table above).
- Remote (non-in-person) pairing and cloud bridge calling aren't built — the spec
  itself scopes those to later phases.
- The certificate wording is explicitly marked "⚠️ needs lawyer review" everywhere it
  appears, per the spec — don't treat it as legally final.
- Gemini's free tier is rate-limited (currently in the range of ~15 requests/minute,
  ~1,500/day for the Flash model this app uses) — more than enough for personal
  testing, but if you ever see a 429 error, you've just hit that limit; wait a minute
  and retry (WorkManager will do this automatically on its own retry schedule).
- This hasn't been run through an actual Gradle build in this environment (no network
  access here to fetch Android/Gradle dependencies), so treat the first Android Studio
  sync as the real first compile — if it flags a typo, it'll be something small and
  easy to spot in Android Studio's error panel.

## Getting an APK without installing Android Studio (cloud build)

This project includes `.github/workflows/build.yml`, a GitHub Actions workflow that
builds a debug APK entirely in the cloud — no PC or Android Studio required. It has
no wrapper jar to worry about; the workflow installs Gradle directly.

1. **Create a free GitHub account** at github.com if you don't have one.
2. **Create a new repository** (Settings can be anything — public repos get unlimited
   free Actions minutes, private repos get a generous free monthly quota).
3. **Upload this project's files into it.** Easiest paths, pick one:
   - Drag the extracted `PakkaBaat` folder onto GitHub's "Add file → Upload files" page
     (most desktop browsers support dragging a whole folder and preserve its structure).
   - Or, if you have `git` available anywhere (a Chromebook's Linux mode, Termux on
     Android, another machine): `git init`, `git remote add origin <your repo URL>`,
     `git add .`, `git commit -m "initial"`, `git push -u origin main`.
4. **(Optional) Add your API keys as secrets** so the final "generate document" step
   works: repo → Settings → Secrets and variables → Actions → New repository secret →
   add `BHASHINI_USER_ID`, `BHASHINI_API_KEY`, `GEMINI_API_KEY`. You can skip this
   and add it later — everything except the very last cloud-processing step still works.
5. **The build runs automatically** on push (or click the "Actions" tab → "Build
   PakkaBaat APK" → "Run workflow" to trigger it manually). It takes a few minutes.
6. **Download the APK**: once the run finishes (green checkmark), open that run →
   scroll to "Artifacts" → download `PakkaBaat-debug-apk` (a small zip containing the
   `.apk` file). This works fine from a phone browser too.
7. **Install on your phone(s)**: unzip to get `app-debug.apk`, open it from your
   phone's Files app or Downloads. You'll be prompted to allow "install unknown apps"
   for that app the first time — accept it, then tap through the install.

This is a **debug-signed APK**, which is exactly right for installing on your own test
phones (it will not install as an update over a Play Store release, and it's not meant
for the Play Store itself — that needs a release signing key, a separate step not
covered here since it's not needed for testing).

If you'd rather avoid touching a YAML file or GitHub Secrets at all, point-and-click
alternatives that also build from a git repo are **Codemagic** or **Bitrise** — both
have a free tier, auto-detect an Android/Gradle project, and give you a "Start build"
button with no config file required.
