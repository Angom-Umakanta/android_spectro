# Spectro – real-time spectrogram + sound labeling (Android, Kotlin, Compose)

## Setup (2 minutes)
1. Android Studio → New Project → **Empty Activity** (Jetpack Compose). Name it "Spectro",
   package `com.example.spectro`, min SDK 24+.
2. Copy the 5 `.kt` files from `app/src/main/java/com/example/spectro/` into your project's
   same folder (replace the generated MainActivity.kt).
3. Replace your `AndroidManifest.xml` with the included one (or just add the `RECORD_AUDIO` permission).
4. Run on a real phone (emulator mics are unreliable).

No extra dependencies are needed beyond the default Compose template.

## How it works
- `AudioEngine` – AudioRecord @ 44.1 kHz → Hann window → 1024-pt FFT every 512 samples → dBFS.
- `SpectrogramModel` – scrolling bitmap (~4.6 s), inferno colormap, low frequencies at the bottom, 0–11 kHz.
- `SoundLibrary` – labeled sounds stored as volume-independent spectral fingerprints.
- Live matching runs ~10x/second on the last 0.35 s of audio (cosine similarity, threshold 0.80).

## Using labels
Make the sound → tap **Label sound** → name it. The display freezes so you can see what you captured.
Whenever it's heard again, a chip appears top-right with the name and match %.
Tips: label while the sound is playing; record in a fairly quiet room; tune `MATCH_THRESHOLD`
and `GATE_DB` if it's too eager or too shy.
