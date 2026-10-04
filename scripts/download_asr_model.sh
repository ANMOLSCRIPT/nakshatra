#!/usr/bin/env bash
# Downloads a Vosk speech-recognition model into backend/models/vosk/.
#
#   scripts/download_asr_model.sh          # large Indian-English model (1 GB download, recommended)
#   scripts/download_asr_model.sh small    # small model (36 MB) -- starts fast, but does not know
#                                          # most heritage proper nouns (see README, "ASR pipeline")
#   scripts/download_asr_model.sh both
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/backend/models/vosk"
mkdir -p "$DEST"

fetch() {
  local name="$1"
  if [ -d "$DEST/$name" ]; then
    echo "already present: $DEST/$name"
    return
  fi
  echo "downloading $name ..."
  curl -fL --progress-bar -o "$DEST/$name.zip" "https://alphacephei.com/vosk/models/$name.zip"
  unzip -q "$DEST/$name.zip" -d "$DEST"
  rm "$DEST/$name.zip"
  echo "installed: $DEST/$name"
}

case "${1:-large}" in
  large) fetch vosk-model-en-in-0.5 ;;
  small) fetch vosk-model-small-en-in-0.4 ;;
  both)  fetch vosk-model-en-in-0.5; fetch vosk-model-small-en-in-0.4 ;;
  *) echo "usage: $0 [large|small|both]" >&2; exit 2 ;;
esac
