# Vosk ASR models

Model weights are not kept in git. Download with:

    scripts/download_asr_model.sh          # vosk-model-en-in-0.5 (recommended, 1 GB download)
    scripts/download_asr_model.sh small    # vosk-model-small-en-in-0.4 (36 MB fallback)

`cloud_server.py` uses the large model when it is present and falls back to
the small one; `--vosk-model <dir-name>` picks one explicitly. Both are
Apache-2.0 licensed models from https://alphacephei.com/vosk/models.
