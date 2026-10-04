# hardware/ — the edge device

Everything that runs on, or stands in for, the ESP32. **This is the voice
activation engine carried over unchanged from the original Nakshatra build**:
same wake word, same dataset, same trained model, same MFCC front end, same
detector, same wire protocol. Only its application (what the answers are
about) changed, and that lives entirely on the server.

```
hardware/
├── edge_agent.py            reference edge device: mic -> KWS -> WebSocket stream
├── audio_devices.py         microphone listing / selection for the scripts here
├── live_demo.py             on-screen wake-word demo (no server needed)
├── PROTOCOL.md              the edge <-> server wire contract (authoritative)
├── firmware_handoff/        what the ESP32 firmware is built from (checksummed)
│   ├── model/               model_int8.tflite, model_data.cc/.h, quant_params.json
│   ├── features/            kws_mel_filterbank.h (Hann window, mel + DCT matrices)
│   ├── parity/              C reference MFCC + parity and go/no-go tests
│   ├── test_vectors/        11 input/expected-output vectors
│   ├── MANIFEST.md          porting notes, detection rule, results
│   └── PROTOCOL.md          copy of ../PROTOCOL.md
├── kws/                     the keyword-spotting ML pipeline (see kws/README.md)
│   ├── config.py            SINGLE source of truth for every constant
│   ├── features.py          MFCC front end
│   ├── model.py, train.py, quantize.py, eval_*.py, export_c.py
│   ├── artifacts/checkpoints/nakshatra_v5/   the shipped model
│   └── data/                the wake-word dataset (recordings, hard negatives, ambient)
├── record_session.py, record_probe.py, split_*.py, sanity_check.py,
│   generate_synthetic_background.py          dataset recording / preparation tools
└── requirements.txt
```

## What is byte-identical to the original

Verified with `diff`/`cmp` against the original project when this one was built:

| Item | Status |
|---|---|
| `edge_agent.py`, `audio_devices.py`, `live_demo.py`, recording/splitting tools | identical |
| `kws/config.py`, `features.py`, `model.py`, `dataset.py`, `train.py`, `quantize.py`, `export_c.py`, checks | identical |
| `kws/data/**` (all recordings) and `kws/artifacts/**` (both checkpoints) | identical, except `data/gsc/README.md` (paths updated) |
| `firmware_handoff/model/**`, `features/**`, `parity/**`, `test_vectors/**` | identical |
| `kws/make_firmware_bundle.py` | output path only (`hardware/firmware_handoff`) |
| `kws/eval_streaming.py`, `kws/RECORDING_CHECKLIST.md` | one comment / two sentences reworded (no code change) |
| `PROTOCOL.md` | edge ↔ server contract untouched; only the server → browser section describes the new answer payload |
| `firmware_handoff/PROTOCOL.md`, `MANIFEST.md`, `SHA256SUMS.txt`, `manifest.json` | updated to carry that `PROTOCOL.md` and its new checksum |

## The model

| | |
|---|---|
| Wake word | **"Nakshatra"** (`N AH K SH AA T R AH`) |
| Checkpoint | `nakshatra_v5` |
| Architecture | DS-CNN-S, int8 TFLite, **44,832 bytes** |
| Input | 1 s of 16 kHz mono audio → MFCC `[49 frames × 10 coefficients]`, int8 |
| Output | `[silence, unknown, keyword]` |
| Front end | 40 ms frames, 20 ms hop, 1024-point FFT, 40 mel bands (20–4000 Hz), 10 MFCCs |
| Detector | every 100 ms; fire when ≥ 3 of the last 5 keyword probabilities exceed 0.75; then 1 s refractory |
| Held-out result | TPR 89.8 %, hard-negative TNR 96.7 %, 0 false triggers in a 300 s talking recording |
| Feature contract hash | `eaec172cd4689c14` |

## Run the reference edge device (laptop microphone)

`edge_agent.py` is the executable specification of the firmware: the same
IDLE → STREAMING → IDLE state machine and the same messages, with a laptop
microphone in place of the INMP441.

```bash
python hardware/edge_agent.py --list-devices
python hardware/edge_agent.py --device <idx> --server ws://localhost:8000/ws/edge
python hardware/edge_agent.py --debug-probs        # print [silence, unknown, keyword] every 0.5 s
```

## Connect the ESP32

**Firmware source is not part of this repository** (it was not part of the
original either). The firmware is built from `firmware_handoff/` and must
produce exactly the messages in `PROTOCOL.md`; because that contract did not
change, firmware that worked with the original server works with this one
without modification.

1. **Wire the INMP441** (I²S MEMS microphone). Any free GPIOs work; this is a
   common assignment — set the same numbers in your firmware's I²S config:

   | INMP441 | ESP32-WROOM-32 |
   |---|---|
   | VDD | 3V3 |
   | GND | GND |
   | L/R | GND (selects the left channel) |
   | SCK (BCLK) | GPIO 32 |
   | WS (LRCLK) | GPIO 25 |
   | SD (DOUT) | GPIO 33 |

   Configure I²S as master, RX, 16 kHz, mono (left). The INMP441 delivers
   24-bit samples in 32-bit slots: shift down to int16 before the front end.

2. **Build the firmware from `firmware_handoff/`** — `model/model_data.cc`
   (the TFLite Micro model), `features/kws_mel_filterbank.h` (window, mel and
   DCT tables) and `parity/kws_frontend_constants.h`. Follow "Front-end porting
   notes" in `firmware_handoff/MANIFEST.md`.

3. **Prove the port before using a microphone**:

   ```bash
   cd hardware/firmware_handoff
   gcc -O2 -std=c99 -I features -I test_vectors -I parity \
       parity/parity_test.c parity/mfcc_reference.c -lm -o parity_test && ./parity_test
   ```

   Then link *your* `kws_extract_mfcc` instead of `mfcc_reference.c`, run the
   same test on the device, and run `parity/go_nogo_test.c` with your full
   MFCC → model → softmax path. Ship only when it prints `GO`.

4. **Point it at the server.** Put the laptop and the ESP32 on the same Wi-Fi,
   start `python backend/cloud_server.py` (it binds `0.0.0.0:8000`), find the
   laptop's address (`ipconfig getifaddr en0` on macOS) and have the firmware
   open `ws://<laptop-ip>:8000/ws/edge`. Allow incoming connections on port
   8000 in the laptop's firewall.

5. **Watch it work.** The website's status pill turns to "Edge device
   connected" the moment the socket opens, and the input-level meter moves
   with the device's `level` events.

Only one edge device should be connected at a time (see `PROTOCOL.md`), so stop
`edge_agent.py` before powering the ESP32.
