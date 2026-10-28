#!/usr/bin/env bash
# Run from anywhere: ./artifact/reproduce.sh
# Optional legacy arguments: <repository-root> <ASP-reference-checkout>.
#
# Requirements: JDK 21 (javac/java), python3 with artifact/requirements.txt for the
# differential oracle, tamarin-prover 1.12.0 and maude 3.5.1 on PATH for the Tamarin part.
# The X-Men 2.0 DL core and its tests are compiled with plain javac; the tests use only
# org.junit.jupiter.api.{Test,DisplayName,Assertions}, provided here by a minimal shim with
# standard semantics (shim/), so no Maven repository access is needed.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=${1:-$(cd "$HERE/.." && pwd)}
DDL=${2:-$HERE/vendor/Defeasible-Deontic-Logic}
OUT=$HERE/generated
X=$REPO/X-Men_2.0/src
if [ ! -f "$X/main/java/com/xmen/service/forget/dl/ForgetDlEngine.java" ]; then
  printf 'Error: no Java Defeasible Logic implementation under %s/X-Men_2.0/src.\n' "$REPO" >&2
  printf 'Use the repository root containing X-Men_2.0/.\n' >&2
  exit 2
fi
B=$(mktemp -d)
trap 'rm -rf "$B"' EXIT
mkdir -p "$B/main" "$B/shim" "$B/test" "$B/harness" "$OUT"

echo "== compile X-Men 2.0 DL core"
( cd "$X/main/java" && javac -encoding UTF-8 -d "$B/main" \
    com/xmen/model/{Message,Pair,Derivation,Atom,Encrypt,PredictiveFunction}.java \
    com/xmen/service/forget/ForgetContext.java com/xmen/service/forget/dl/*.java )
javac -d "$B/shim" "$HERE"/shim/org/junit/jupiter/api/*.java "$HERE"/shim/RunTests.java
( cd "$X/test/java" && javac -encoding UTF-8 -cp "$B/main:$B/shim" -d "$B/test" com/xmen/service/forget/dl/*.java )
CP=$B/main:$B/shim:$B/test

echo "== core tests (110)"
java -cp "$CP" RunTests $(for c in AmbiguityBlockingReasonerTest ForgetBlockingTest NormalDerivationsTest \
  ForgetDlEngineTest ForgetDlPaperExamplesTest ForgetDlPropertiesTest ForgetDlBridgeTest \
  ForgetDlReviewFixesTest UsabilityDeciderTest ForgetDlUsabilityFallbackTest; do
  echo com.xmen.service.forget.dl.$c; done) | tee "$OUT/core_tests.txt"

if [ -n "$DDL" ]; then
  echo "== differential oracle against the ASP encoding (3 seeds x 600 theories)"
  for s in 1 2 3; do echo "== seed $s"
    python3 -I "$REPO/X-Men_2.0/scripts/dl-oracle/asp_crosscheck.py" "$DDL" "$CP" 400 200 $s; done | tee "$OUT/asp_oracle.txt"
fi

echo "== research harness"
javac -encoding UTF-8 -cp "$CP" -d "$B/harness" "$HERE"/harness/src/com/xmen/service/forget/dl/*.java
H=$CP:$B/harness
J=(java -Xmx5g -Dstdout.encoding=UTF-8 -cp "$H")
"${J[@]}" com.xmen.service.forget.dl.ResearchRunningExample      > "$OUT/running_example.md"
"${J[@]}" com.xmen.service.forget.dl.ResearchVariantStudy 5 2000 > "$OUT/variant_study.txt"
"${J[@]}" com.xmen.service.forget.dl.ResearchDecisionStudy 5000  > "$OUT/decision_study.md"
"${J[@]}" com.xmen.service.forget.dl.ResearchClosureCheck        > "$OUT/closure_check.md"
"${J[@]}" com.xmen.service.forget.dl.ResearchAdversarialCues     > "$OUT/adversarial_cues.md" 2> "$OUT/table6.dl"
"${J[@]}" com.xmen.service.forget.dl.ResearchDeltaTags "$OUT/table6.dl" > "$OUT/table6_tags.txt"
python3 -I "$REPO/X-Men_2.0/scripts/dl-oracle/ap_crosscheck.py" check "$DDL" "$OUT/table6.dl" "$OUT/table6_tags.txt" --support | tee "$OUT/table6_oracle.txt"
"${J[@]}" com.xmen.service.forget.dl.ResearchScalability         > "$OUT/scalability.md"

if command -v tamarin-prover >/dev/null && command -v maude >/dev/null; then
  echo "== Tamarin"
  export LANG=C.UTF-8 LC_ALL=C.UTF-8
  F="--bound=25 --quit-on-warning --derivcheck-timeout=20"
  mkdir -p "$OUT/tamarin"
  cp -R "$HERE/tamarin/." "$OUT/tamarin/"
  ( cd "$OUT/tamarin/bank-decisions" && ./run_all.sh . > results.txt && cat results.txt )
  ( cd "$OUT/tamarin/invoice-host-outputs" && for s in NoCue Cue SignLapse; do
      tamarin-prover --prove $F Invoice_DL_${s}_actual.spthy > invoice_$s.log 2>&1; done )
  ( cd "$OUT/tamarin/certificate" && for f in cert_ok cert_bug; do
      tamarin-prover --prove=Certificate_No_Forgotten_Reuse $F $f.spthy > $f.log 2>&1; done )
  ( cd "$OUT/tamarin/bank-reproduction" && for f in base mut; do
      tamarin-prover --prove=Forget_Attack_Only_B1_Compromised $F $f.spthy > $f.log 2>&1; done )
else
  echo "== Tamarin skipped: install tamarin-prover and maude to reproduce the proof runs"
fi
echo "done; outputs in $OUT"
