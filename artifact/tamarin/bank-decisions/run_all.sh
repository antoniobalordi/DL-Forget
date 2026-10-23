#!/usr/bin/env bash
# Re-run the Bank judgments without GNU timeout/date dependencies.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-$HERE/../../generated/tamarin/bank-decisions}
export LANG=C.UTF-8 LC_ALL=C.UTF-8
python3 - "$HERE" "$OUT" <<'PY'
from pathlib import Path
import subprocess, sys, time

models, out = map(Path, sys.argv[1:])
(out / "logs").mkdir(parents=True, exist_ok=True)
failures = 0
for model in ("preserve", "replace", "skip"):
    for lemma in ("Forget_Attack_Only_B1_Compromised", "Credential_Sent", "Functional"):
        path = out / "logs" / f"{model}__{lemma}.log"
        start = time.monotonic()
        with path.open("w") as log:
            try:
                rc = subprocess.run(
                    ["tamarin-prover", f"--prove={lemma}", "--bound=25", "--quit-on-warning",
                     "--derivcheck-timeout=20", str(models / f"{model}.spthy")],
                    stdout=log, stderr=subprocess.STDOUT, timeout=300).returncode
            except subprocess.TimeoutExpired:
                rc = 124
        verdict = next((line.strip() for line in path.read_text().splitlines()
                        if line.startswith(f"  {lemma} ")), None)
        if rc or verdict is None:
            failures += 1
        print(f"{model}.spthy | {verdict or f'{lemma}: no verdict (exit {rc}, wall limit 300s)'}"
              f" | {time.monotonic() - start:.1f} s", flush=True)
sys.exit(bool(failures))
PY
