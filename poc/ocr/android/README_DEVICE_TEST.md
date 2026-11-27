# CalcSuits Phase 4.1 — Isolated Android ARM64 OCR POC

Standalone, offline-only Android harness inside `poc/ocr/android/` (completely decoupled from CalcSuits production `app/` and root `settings.gradle.kts`).

## Security & Scope Guarantees
- **Zero `INTERNET` permission** in `AndroidManifest.xml` (offline enforcement at Android OS level).
- **Zero broad storage permissions** (uses Android Photo Picker + app-private `getExternalFilesDir`).
- **Pre-load SHA-256 verification** of all 4 pinned `onnx-community/TexTeller-ONNX` (`9727784d91d7f8437dc7140941c4335284ce075e`) files before opening ONNX Runtime or parsing JSON.
- **Native ARM64 runtime:** `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` (MIT License), `abiFilters += "arm64-v8a"`.

## Build & Run on Physical ARM64 Device

1. Build the isolated APK and run JVM unit tests:
   ```bash
   gradle -p poc/ocr/android assembleDebug testDebugUnitTest
   ```
2. Install on a connected `arm64-v8a` Android device:
   ```bash
   adb install -r poc/ocr/android/app/build/outputs/apk/debug/app-debug.apk
   ```
3. Push the 4 verified model files (and optional real camera handwritten/printed photos):
   ```bash
   bash poc/ocr/android/push_models_to_device.sh \
     /path/to/texteller_onnx \
     /path/to/tokenizer_dir \
     /path/to/real_photos_dir
   ```
4. Turn on **Airplane Mode** on the phone, launch **CalcSuits OCR POC**:
   - Tap **Verify SHA-256 & Load Model** (records `verify_ms`, `load_ms`, provider `XNNPACK+CPU` or `CPU`).
   - Tap **Pick Formula Image (Offline)** for single-photo tests, or **Run Local Benchmark Dir** for batch benchmarking.
5. Pull the structured device benchmark JSON back to your workstation:
   ```bash
   adb pull /sdcard/Android/data/com.calcsuits.poc.ocr.arm64/files/results/arm64_benchmark.json \
     poc/ocr/results/arm64_device_benchmark.json
   ```
