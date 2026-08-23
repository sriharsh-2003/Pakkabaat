# PakkaBaat — `local-asr` branch

This branch removes Bhashini entirely. Speech-to-text now runs **fully on-device** via
`whisper.cpp` — no network, no signal, no API needed for that step. Gemini stays for
the final document-structuring step (§10.1 prompt), since that's the one piece that
genuinely benefits from a bigger model, and its free tier costs nothing.

**Only network dependency left in the entire app: the structuring call, and the OTP
step during onboarding.** Recording, hashing, and transcription are 100% offline.

## What changed vs. the cloud-only `main` branch

| Piece | main branch | this branch |
|---|---|---|
| Speech-to-text | Bhashini (cloud) | **whisper.cpp, on-device** (~57MB model, bundled) |
| Structuring | Gemini (cloud) | Gemini (cloud) — unchanged |
| Gemini API key | `gradle.properties` only | **Settings screen (BYOK)** — each tester enters their own free key, stored encrypted on their own device. `gradle.properties` still works as a fallback for solo dev testing. |
| App size | small (~15-20MB) | ~70-90MB (base app + the bundled Whisper model) |

## Why this needs real `git`, not GitHub's drag-and-drop upload

This branch depends on the actual `whisper.cpp` source as a **git submodule**
(`.gitmodules` is already in this project). GitHub's web "Upload files" page can't
materialize a submodule — it just uploads files, it doesn't understand `.gitmodules`.
So for this branch specifically, push it with real `git`:

```bash
git checkout -b local-asr
git add .
git commit -m "local-asr: on-device whisper.cpp, Gemini for structuring only"
git push -u origin local-asr
git submodule add https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper.cpp
git submodule update --init --recursive
git add .gitmodules app/src/main/cpp/whisper.cpp
git commit -m "add whisper.cpp submodule"
git push
```

If you don't have `git` on your main machine, a Chromebook's Linux mode, Termux on
Android, or literally any machine with `git` installed for five minutes will do —
this one step needs it, nothing else in the project does.

## The GitHub Actions workflow handles the rest automatically

`.github/workflows/build.yml` on this branch:
1. Checks out the repo **with the submodule** (`submodules: 'recursive'`)
2. Installs the Android NDK
3. **Downloads the Whisper model itself** (`ggml-base-q5_1.bin`, ~57MB, multilingual)
   straight into `app/src/main/assets/models/` before building
4. Builds and uploads the APK, exactly like the `main` branch's workflow

This means you can get a fully working local-ASR APK **without ever touching Android
Studio, NDK, or downloading the model yourself** — just push this branch and let the
cloud build do it. (It only works this smoothly *because* GitHub's runners have real
internet access — this project was built in a sandboxed environment that didn't, which
is why the model file and submodule aren't already sitting in this zip.)

## If you build locally instead

1. Run the two `git submodule` commands from above.
2. Download the model manually:
   ```bash
   mkdir -p app/src/main/assets/models
   curl -L -o app/src/main/assets/models/ggml-base-q5_1.bin \
     https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
   ```
3. Delete `app/src/main/assets/models/PUT_MODEL_HERE.txt` (just a placeholder).
4. Open in Android Studio, make sure the NDK is installed (Tools → SDK Manager → SDK
   Tools tab → NDK), sync, run.

## Testing this branch

Same two-phone flow as the `main` branch's README, with two differences:
1. **First launch will pause for a moment** the first time you record — that's the
   ~57MB model being copied out of the APK's assets into usable storage, one-time only.
2. **Before your first recording, open Settings (gear icon on the home screen)** and
   paste in a free Gemini key from aistudio.google.com/apikey. Each tester needs their
   own — that's the whole point of BYOK for a test group.

Everything else — QR pairing, mutual consent, the 60s stop timeout, PDF export — works
identically to the main branch.

## Two features requested but not built yet

- **Real-time/streaming captions during recording** — whisper.cpp processes a
  finished audio file, not a live stream. True real-time needs a rolling-buffer +
  voice-activity-detection layer on top (see `whisper.cpp`'s own `stream` example in
  its repo for the reference approach). Worth doing as a fast-follow once this basic
  version is confirmed working, not before.
- **Full speaker identification** — what's wired in is `tinydiarize` (set
  `ENABLE_DIARIZATION = true` in `WhisperCppTranscriber.kt` and swap in a `-tdrz`
  model file), which marks *when* the speaker changed, not *who* is speaking. True
  voice-print speaker ID would need a separate, heavier model — likely not worth it
  under your size budget.

## Known rough edges (in addition to the ones in `main`'s README)

- Whisper's accuracy on heavily code-mixed Hindi/English/regional speech is noticeably
  weaker than Bhashini's was — this is the direct trade-off for going fully local
  within your size budget (see the earlier discussion on IndicConformer vs. Whisper).
- The 57MB model adds real install size and a one-time copy delay on first use.
- BYOK means every tester needs to know what an API key is — fine for a small pilot
  group, not fine for the actual target users in spec §3. Revisit the backend-proxy
  approach before any wider release.
