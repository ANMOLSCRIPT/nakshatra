#!/usr/bin/env bash
# Runs every automated check. Each step prints its own PASS/FAIL lines.
#   scripts/run_tests.sh          # everything
#   scripts/run_tests.sh --quick  # skip the slow ASR accuracy sweep
set -uo pipefail
cd "$(dirname "$0")/.."
PY="${PYTHON:-.venv/bin/python}"
[ -x "$PY" ] || PY=python3
fail=0
step() { echo; echo "=== $1"; shift; "$@" || { echo "*** FAILED: $1"; fail=1; }; }

step "knowledge base structure"            "$PY" scripts/validate_kb.py
step "README question list is up to date"  "$PY" scripts/build_readme_qa.py --check
step "answer engine regression"            bash -c "cd backend && ../$PY test_matcher.py"
step "KWS frozen config"                   bash -c "cd hardware/kws && ../../$PY verify_step1.py"
step "MFCC front end self-test"            bash -c "cd hardware/kws && ../../$PY features.py"
step "firmware bundle checksums"           bash -c "cd hardware/firmware_handoff && shasum -a 256 -c SHA256SUMS.txt >/dev/null && echo 'all checksums OK'"
if command -v gcc >/dev/null; then
  T="$(mktemp -d)"
  step "firmware MFCC parity (C vs Python)" bash -c "cd hardware/firmware_handoff && gcc -O2 -std=c99 -I features -I test_vectors -I parity parity/parity_test.c parity/mfcc_reference.c -lm -o $T/parity && $T/parity | tail -2"
  step "firmware go/no-go harness"          bash -c "cd hardware/firmware_handoff && gcc -O2 -std=c99 -I features -I test_vectors -I parity parity/go_nogo_test.c parity/go_nogo_stub.c -lm -o $T/gonogo && $T/gonogo | tail -1"
fi
step "end-to-end pipeline (KWS -> edge -> server -> ASR -> answer)" "$PY" tests/e2e_pipeline.py
step "website in headless Chrome"          "$PY" tests/ui_check.py --shots "${TMPDIR:-/tmp}/nakshatra-ui"
[ "${1:-}" = "--quick" ] || step "ASR accuracy on synthesised questions" "$PY" tests/asr_eval.py

echo; [ $fail = 0 ] && echo "ALL TEST STEPS PASSED" || echo "SOME STEPS FAILED"
exit $fail
