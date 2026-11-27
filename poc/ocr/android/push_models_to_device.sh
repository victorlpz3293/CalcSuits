#!/usr/bin/env bash
# Pushes the 4 SHA-256-verified TexTeller-ONNX (q4f16) artifacts and optional
# benchmark images to a physical ARM64 Android device via ADB (100% offline test).
set -euo pipefail

MODEL_SRC_DIR="${1:-/tmp/poc_ocr_env/texteller_onnx}"
TOKENIZER_SRC_DIR="${2:-/tmp/poc_ocr_env/hf_cache/onnx-community/TexTeller-ONNX/9727784d91d7f8437dc7140941c4335284ce075e}"
BENCHMARK_SRC_DIR="${3:-}"

APP_ID="com.calcsuits.poc.ocr.arm64"
DEVICE_BASE="/sdcard/Android/data/${APP_ID}/files"

declare -A EXPECTED_SHA256=(
  ["encoder_model_q4f16.onnx"]="c42190515ffcd4a728a1abee4dc8d4636b62f782fd823294d2861434a7f7d0eb"
  ["decoder_model_q4f16.onnx"]="edebc4c90596d13313382eecfeef89f0041efe7bfa4e89391d5b5dbabab25100"
  ["tokenizer.json"]="ec4ca954798a092faf6fefcfa47fb5f85d76cdf6ab170b624ae1a683d53dae14"
  ["tokenizer_config.json"]="74c8bdcd769acae371f98980fe929be1172fab3df844a50159c61998a3832eea"
)

verify_hash() {
  local file="$1"
  local expected="$2"
  local actual
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "ERROR: SHA-256 mismatch for $file (expected $expected, got $actual)" >&2
    exit 1
  fi
  echo "OK SHA-256: $(basename "$file")"
}

verify_hash "${MODEL_SRC_DIR}/encoder_model_q4f16.onnx" "${EXPECTED_SHA256[encoder_model_q4f16.onnx]}"
verify_hash "${MODEL_SRC_DIR}/decoder_model_q4f16.onnx" "${EXPECTED_SHA256[decoder_model_q4f16.onnx]}"
verify_hash "${TOKENIZER_SRC_DIR}/tokenizer.json" "${EXPECTED_SHA256[tokenizer.json]}"
verify_hash "${TOKENIZER_SRC_DIR}/tokenizer_config.json" "${EXPECTED_SHA256[tokenizer_config.json]}"

adb shell "mkdir -p '${DEVICE_BASE}/models' '${DEVICE_BASE}/benchmark' '${DEVICE_BASE}/results'"
adb push "${MODEL_SRC_DIR}/encoder_model_q4f16.onnx" "${DEVICE_BASE}/models/"
adb push "${MODEL_SRC_DIR}/decoder_model_q4f16.onnx" "${DEVICE_BASE}/models/"
adb push "${TOKENIZER_SRC_DIR}/tokenizer.json" "${DEVICE_BASE}/models/"
adb push "${TOKENIZER_SRC_DIR}/tokenizer_config.json" "${DEVICE_BASE}/models/"

if [[ -n "$BENCHMARK_SRC_DIR" && -d "$BENCHMARK_SRC_DIR" ]]; then
  echo "Pushing benchmark images from ${BENCHMARK_SRC_DIR}..."
  adb push "${BENCHMARK_SRC_DIR}/." "${DEVICE_BASE}/benchmark/"
fi

echo "Done. Enable Airplane Mode on the phone, launch 'CalcSuits OCR POC', tap Verify & Run."
