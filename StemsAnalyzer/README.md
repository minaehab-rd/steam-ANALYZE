# Stems Analyzer (Android)

A native Android app (Kotlin + Jetpack Compose) that takes a photo of raw
material containing stems/straw and tells you the **stems %** and **product
%** in it, using Google's Gemini vision model.

This is a from-scratch native Android port of the *idea* behind the uploaded
React/Vite "Mixed Analysis" web app (which used the Gemini API in the
browser). The core prompt-engineering approach (grid-scan the image, judge
visual *mass* not just area, straw = light/low-density vs. product =
dense) is carried over from `services/geminiService.ts` in that project, but
simplified from an open-ended category list down to exactly the two buckets
you asked for: **Stems/Straw** vs. **Product**.

## What it does

1. Take a photo with the camera (or pick one from your gallery).
2. Tap **Analyze** — the photo is resized, compressed, and sent straight
   to Google's Gemini API (`generateContent`) with a structured-output
   request.
3. You get back two percentages — Stems/Straw and Product — shown as
   progress bars, plus an optional short note from the model (e.g. "photo
   is a bit blurry").

Everything runs directly against Google's API from the phone — there's no
backend server. Your Gemini API key is stored only on-device
(Jetpack DataStore) and is only ever sent to `generativelanguage.googleapis.com`.

## Before you build: get a Gemini API key

1. Go to https://aistudio.google.com/apikey and create a free API key.
2. Build and run the app, tap the ⚙️ Settings icon, and paste the key in.
   (You can also change the model name there if `gemini-2.5-flash`
   ever gets renamed/retired — check https://ai.google.dev/gemini-api/docs/models
   for current model names.)

## How to build

You'll need [Android Studio](https://developer.android.com/studio)
(Koala/2024.1 or newer).

1. Open this folder (`StemsAnalyzer/`) in Android Studio as an existing
   project — it will download the Gradle wrapper and dependencies
   automatically on first sync.
2. Let Gradle sync finish.
3. Plug in an Android phone (with USB debugging on) or start an emulator.
4. Click **Run ▶** to install and launch the app.

To build a signed release APK/AAB, use Android Studio's
**Build > Generate Signed Bundle / APK** wizard, or run:

```
./gradlew assembleRelease
```

(the output APK will be in `app/build/outputs/apk/release/`).

## Project layout

```
app/src/main/java/com/strawlens/analyzer/
  MainActivity.kt        - all UI (Compose): capture/pick photo, analyze, results, settings
  network/GeminiClient.kt - talks to the Gemini REST API directly (no SDK), builds the
                            stems-vs-product prompt + structured JSON schema, parses results
  data/MixtureResult.kt   - simple result data class (stemsPercentage, productPercentage, notes)
  data/SettingsStore.kt   - persists API key / model name / language (DataStore)
  util/ImageUtils.kt      - loads a photo, fixes camera rotation (EXIF), downsizes it,
                            and encodes it as base64 JPEG for upload
```

## Notes & things you may want to tweak

- **Accuracy**: Gemini is estimating percentages from a single 2D photo —
  treat results as a strong visual estimate, not a lab-grade measurement.
  For tricky mixtures, a well-lit, close-up, evenly-spread photo (not a
  deep pile) gives the model the best shot.
- **Language**: the app ships with English and Arabic prompts/UI (toggle
  in Settings) since the source project was Arabic-first.
- **Costs/quota**: this calls the Gemini API with your own key, so usage
  is billed/limited under your own Google AI Studio account, same as the
  original web app.
- The original web app had a lot more going on (dynamic category
  management, 2x2 tiled analysis for extra precision, image
  isolation/thresholding filters, a reference-image library to "train"
  the model on your specific goods, PDF/data export). None of that is
  in this app — it's intentionally focused on your exact ask (stems %
  vs. product % from one photo). Happy to add tiled analysis (splits the
  photo into a grid and analyzes each piece separately, then combines the
  result — meaningfully more accurate for very uneven piles) or reference
  images if you want closer parity with the original app.
