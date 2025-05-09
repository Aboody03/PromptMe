#!/usr/bin/env bash
set -euo pipefail

MODEL_DIR="model/vosk-model-small-en-us-0.15"
ZIP_NAME="vosk-model-small-en-us-0.15.zip"
DOWNLOAD_URL="https://alphacephei.com/vosk/models/${ZIP_NAME}"

if [[ ! -d model ]]; then
  mkdir model
fi

if [[ -d "${MODEL_DIR}" ]]; then
  echo "Model already exists at ${MODEL_DIR}"
  exit 0
fi

echo "Downloading Vosk model from ${DOWNLOAD_URL}..."
curl -L -o "model/${ZIP_NAME}" "${DOWNLOAD_URL}"

echo "Extracting to model/..."
unzip -q "model/${ZIP_NAME}" -d model

echo "Removing ZIP file"
rm "model/${ZIP_NAME}"

echo "✔Model is ready at ${MODEL_DIR}"
