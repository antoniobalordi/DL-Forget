# Forget-DL plugin for X-Men 2.0 — roadmap and review evidence

Reviewed on 2026-10-02 against the supplied `7.DefeasibleLogic.tex` and upstream host `d8c6404`.
Semantics, assumptions (A1–A13), manuscript issues (E1–E8) and host discrepancies (D1–D10):
[`SEMANTIC_CONTRACT.md`](SEMANTIC_CONTRACT.md). Status values: `completed` means code exists **and**
its tests were executed; `ready` means no blocker; `blocked` names the missing dependency.

## 1. Changes with respect to the supplied plan

* **Reasoner.** SPINdle is not on the critical path. Compiled theories are finite, acyclic and
  template-shaped; a reference DL(∂) engine (~200 lines) is differentially tested against an
  independent ASP encoding instead. `DefeasibleReasoner` remains the seam for SPINdle (optional O2).
* **Derivations.** The legacy engine is not repaired in place (that would change Algorithm 1 output);
  DL mode uses a new enumerator. Legacy fixes become optional, regression-tracked work (O1).
* **Scope.** Guarded cue branches, a translation-correctness argument, bank validity state and the
  old-X-Men adapter remain research items. The host certifies surviving operands/outputs after
  the core's early Neglect return; fixture verification does not establish a universal theorem.
* **Added.** Re-evaluation of later sends under persisting lapses (D9), scenario syntax for
  procedural lapses and cues, and the host defects D5/D7 that also affect the Algorithm 1 baseline.

## 2. Roadmap

Critical path: **T1.1 → T1.2 → T1.4 → T1.5 → T2.2 → T3.1 → T3.2 → T4.1** (T1.3, T2.1, T2.3 feed T1.5).

| ID | Task | Components | Depends | Validation | Status |
|---|---|---|---|---|---|
| T0.1 | Semantic contract | `SEMANTIC_CONTRACT.md` | — | review | completed |
| T0.2 | Host inspection, defect inventory | D1–D10 | — | source inspection at d8c6404 | completed |
| T1.1 | Term algebra, normal-derivation enumerator, coverage | `Terms`, `NormalDerivations` | T0.1 | `NormalDerivationsTest` | completed |
| T1.2 | `Blocked`, Cases 1–3 | `ForgetBlocking` | T1.1 | `ForgetBlockingTest` | completed |
| T1.3 | DL(∂) engine | `DefeasibleTheory`, `AmbiguityBlockingReasoner` | — | 14 real JUnit conformance tests; fresh ASP differential run | completed |
| T1.4 | Compiler (§§7.3, 7.5, 7.6) | `ForgetDlCompiler` | T1.1–T1.3 | examples, properties | completed |
| T1.5 | Algorithm 2 engine | `ForgetDlEngine`, `ForgetDlModel` | T1.4 | `ForgetDlEngineTest` | completed |
| T1.6 | Cognitive trace | `ForgetDlResult` | T1.5 | trace assertions in example tests | completed |
| T2.1 | DL conformance + differential oracle | tests, `scripts/dl-oracle` | T1.3 | fresh seed-1 run: 600 theories, 14,034 literals, zero corrected-oracle mismatches | completed for current run |
| T2.2 | Paper examples 1–10, Lemma 1, §7.8, Tables 3–4 | `ForgetDlPaperExamplesTest` | T1.5 | 15 tests | completed |
| T2.3 | Property tests | `ForgetDlPropertiesTest` | T1.5 | 4 × 400 seeded scenarios | completed |
| T3.1a | Host bridge (representation, labels, state write-back) | `ForgetDlBridge` | T1.5 | `ForgetDlBridgeTest` | completed |
| T3.1b | Opt-in DL branch + `Forget-Semantics` header | `ForgetMutationStrategy`, `ForgetMutationController` | T3.1a | 17 actual HTTP ZIP/trace regressions; request-local model and ZIP rendering | completed |
| T3.2 | Maven build, existing suite, Surefire run of the DL tests | `./mvnw test` | T3.1b | final remaining-suite 210 tests, zero failures/errors, two disabled; isolated 13 UI tests pass | completed in split runs; combined-run UI failures retained |
| T3.3 | Re-evaluate later sends under persisting lapses (D9) | strategy | T3.2 | current cue changes later send; State/channel ordering, branch and unsafe-use regressions | implemented for documented acyclic ordering; tested |
| T3.4 | `.spthy` syntax for rule/procedure lapses and cues | parser, bridge | T3.2 | `ForgetRule`, `ForgetProcedure`, relearning and cue annotations in actual HTTP uploads | implemented; tested |
| T4.1 | DL-directed Bank/Invoice models through Tamarin | outputs + Tamarin | T3.2 | Invoice nine lemma judgments close; Bank parses/closes but six bounded searches unfinished | completed checks with qualified Bank result |
| T4.2 | User documentation of DL mode | READMEs | T3.2 | API, semantic contract, review and persistent evidence | completed |
| O1 | Fix legacy D1–D7, D10 behind regression fixtures | legacy services | T3.2 | before/after fixture diff | optional |
| O2 | Qualify SPINdle behind `DefeasibleReasoner` | adapter | access to SourceForge | same conformance + oracle | optional |
| O3 | Report the ASP binding defect upstream | — | — | — | optional |
| O4 | Guarded cue branches; translation argument; original-X-Men adapter | — | T4.1 | — | optional |

Mandatory: T0–T3.2, T4.1. Desirable: T3.3, T3.4, T4.2. Optional: O1–O4.

## 3. Formal concept → code → test

| Formal concept | Representation | Component | Test |
|---|---|---|---|
| Messages, Def. 1–3 | X-Men `Message`; format A2 | `Terms` | `formatRequired`, `legacyEncrypt` |
| Π_m, normality (§3.2) | `Derivation` trees, A3 | `NormalDerivations` | `example4`, `example6`, `finitenessCounterexample`, `normality` |
| Hyp_π (Def. 5) | `Initial` leaves; decryption keeps ciphertext + key | `NormalDerivations.hypotheses` | `example1`, `example4` |
| Rules(π), procedure id (Def. 7) | label set; SHA-256 of canonical tree | `NormalDerivations.rules/procedureId` | `example10`, `determinismAndStableIds` |
| Blocked, Cases 1–3 (Def. 5) | analysis/synthesis saturation | `ForgetBlocking` | `ForgetBlockingTest` |
| DL(∂) (§7.1) | least fixpoint of the proof conditions | `AmbiguityBlockingReasoner` | conformance suite, ASP oracle |
| Known, Lapsed, d_π, use_π, ω (§7.3) | atoms `Known(x)`, `Lapsed(h)`, `Via(π,t)`, `Usable(t)` | `ForgetDlCompiler` | `example7`–`example9` |
| Cue model, reinstaters (Def. 8, §7.5) | `CueModel`, `ContextCue(c,h)`, `rho:π:h:c` | model, compiler | `lemma1`, `cueSeparation`, `irrelevantCueIsConservative` |
| PForget, ω^rule, ω^proc (§7.6) | `ProceduralState`, `LapsedRule`, `LapsedProc` | model, compiler, engine | `example10*`, `ruleVersusProcedureScope`, `proceduralStateUpdate` |
| Alg. 2 l.1–13 | state update, Neglect, re-acquisition | `ForgetDlEngine` | `erratum…`, `forgetJustReceived`, `neglect`, `reacquisition*` |
| Alg. 2 l.14–19 | T, R_m̄, m2[C] | `ForgetDlEngine` | `candidateCap`, `labelRequired`, `closureBlockedVariant`, `noSelfReplacement`, `multipleLapses` |
| Alg. 2 l.20–31 | joint compile, decision | `ForgetDlEngine` | `table4`, `table3*`, `jointEqualsSeparate`, `coverage` |
| Cognitive trace (§7.4) | `TargetTrace`, `DerivationTrace`, `Blocker` | `ForgetDlResult` | standing/overruled assertions in example tests |
| Host integration | A9/A10, normalized cues/ciphers; request-local strategy/ZIP | `ForgetDlBridge`, strategy/controller | 6 bridge tests plus actual HTTP ZIP, direct API and ZIP concurrency regressions |

## 4. Traceability matrix

| Paper requirement | Implementation | Test | Status |
|---|---|---|---|
| Def. 4 case 1 (not retained) | engine l.4 | `forgetJustReceived` | tested |
| Def. 4 case 2 (known message) | engine l.4, E1 | `erratumReceivedMessageIsKept` | tested |
| Ex. 1 (forgotten non-hypothesis) | enumerator | `example1` | tested |
| Ex. 2, Ex. 3 (re-acquisition) | engine l.13, A6 | `forgetJustReceived`, `reacquisition`, `reacquisitionNeedsReceiptProvenance` | tested |
| Def. 5 Cases 1–3, Ex. 4–6 | `ForgetBlocking` | `ForgetBlockingTest` | tested |
| Def. 6 / Alg. 1 l.12 | compiler + reasoner | `agreesWithAlgorithm1` | tested |
| §3.2 normal derivations | enumerator, A3 | `NormalDerivationsTest` | tested |
| §7.1 DL(∂) tags | reasoner | conformance; oracle | tested |
| §7.3 templates, Ex. 7–9 | compiler | `example7`, `example8`, `example9` | tested |
| Def. 7 Rules(π), identity | enumerator | `example10`, `determinismAndStableIds` | tested |
| §7.6 rule vs procedure scope, Ex. 10 | compiler | `example10RuleLapse`, `example10ProcedureLapse`, `ruleVersusProcedureScope` | tested |
| §7.6 state update / relearning | engine | `proceduralStateUpdate` | tested |
| Def. 8, Lemma 1 | compiler | `lemma1`, `dlMatchesTemplateOracle` | tested |
| §7.5 irrelevant-cue conservativity | compiler | `irrelevantCueIsConservative` | tested |
| §7.7 joint = separate | compiler | `jointEqualsSeparate` | tested |
| §7.7 divergence from Alg. 1 (closure) | engine | `closureBlockedVariant` | tested |
| §7.7 no mixed keep/replace variants | engine, A5 | `noSelfReplacement` | tested |
| §7.4 cognitive trace | `ForgetDlResult` | `example7`, `table3Hint`, `example10*` | tested |
| §7.8 two separations | compiler | `proceduralSeparation`, `cueSeparation` | tested |
| §8.1 Table 3 (none / hint / reset) | engine | `table3NoRecovery`, `table3Hint`, `table3Reset` | tested |
| §8.1 cue absent at later transition | engine and whole-run host | `table3Hint` plus actual HTTP later-send regression | tested within supported State/channel ordering |
| §8.2 Table 4, four rows | engine | `table4`, `invoiceWithReceivedReminder` | tested |
| §2.1 attack (PW1 → PW2) | engine, bridge | `bankLoginAttack`, `bankRuleH2` | tested |
| Alg. 2 Neglect branch | engine, bridge | `neglect` (both) | tested |
| Matching + propagation (§4.3) | guarded structural substitutions | actual HTTP directed-channel, native network, State topology and operand regressions | tested within documented bounds |
| Generated `.spthy`/`.m` analysed by Tamarin | outputs + portable Tamarin/Maude | Invoice nine closed judgments; Bank parses/closes but searches unfinished | qualified evidence (T4.1) |
| §8.1 bank validity state `Valid_B` | — | — | not in scope of the human decision (ceremony model) |

## 5. Validation evidence from the current review

* Portable Temurin **21.0.12.1+1** compiled the full host with actual Maven dependencies and
  annotation processors. The host's existing `PSpecial` unchecked-operation note remains; the
  full build is not claimed warning-free.
* The final suite excluding two separately rechecked GUI classes reports **210 tests, zero
  failures/errors, two disabled diagnostics**. It includes **85 core tests**, **17 actual HTTP
  DL integration tests**, direct API compatibility, quoted-builtin grammar, concurrent ZIP
  rendering and single-rule parsing. The additional core regression checks explicit procedural
  relearning on a Neglect transition. The isolated GUI run reports **13 tests, zero failures,
  errors or skips**. Together these groups cover 223 reported tests: 221 executed and two disabled.
* Earlier existing host baseline: **117 reported, zero failures/errors, two intentionally
  disabled lexer/parser diagnostics**. The initial restricted attempt encountered Mockito
  self-attachment and JavaFX-cache permissions; the unrestricted retry passed.
* The combined run reported **217 tests, one failure, two errors, two disabled**. All core and
  host integration tests passed; failures were in existing `XMenInterfaceTest` and
  `DisplayScaleSupportTest`. Those two classes then passed an isolated **13-test** rerun,
  with no GUI code changes. This is not an all-green combined run. The subsequent
  209-test run excluding these classes found one new Invoice test assertion scanning lemma
  text beyond the rule; its boundary was corrected and the final 210-test run passed. Review-only
  hardcoded artifact writes were removed from the integration tests for clean-machine portability.
* Tamarin **1.12.0**, Maude **3.5.1**: actual HTTP-generated Bank and singleton outputs parse.
  Bank passes strict derivation/wellformedness checks and theory closing. Four Bank lemma
  searches at bound 40 timed out after 120 seconds each; witness retries at bound 18 with
  SEQDFS timed out after 90 seconds each. No Bank lemma is reported proved or falsified.
  Singleton has no root-producing setup, so parsing gives no reachability evidence.
* Actual HTTP-generated Invoice scenarios pass strict derivation/wellformedness checks and
  finish all nine lemma judgments at bound 25. No cue: a send and wrong-account witness are
  verified, while account safety is falsified. Matching cue: a send and account safety are
  verified, with no wrong-account trace. Signing-rule lapse: both send witnesses have no trace;
  account safety holds vacuously because the send is skipped. These minimal authored fixtures
  cover the human mutation mechanism, not a complete bank/merchant ceremony.
  Initial Invoice fixtures exposed a Tamarin derivation warning by destructuring `pk(skB)`
  in a premise; they were corrected to bind the public key as an opaque `pkB` value.
* Fresh ASP differential check: clingo **5.8.0**, upstream
  `gvdgdo/Defeasible-Deontic-Logic@679c638c2fddf97e1ee624cc528e84be6fd02d95`, seed 1,
  **400 general + 200 compiled theories, 14,034 literals**. The encoding with the attacker
  binding corrected had **zero mismatches and zero inconsistent literals**. Raw upstream had
  14 mismatches/14 inconsistent literals, reproducing its documented team-defeat binding defect.
  This oracle covers its generator's stated fragment, not every arbitrary cyclic DL theory.
* The supplied `VALIDATION_LOG.md` records an earlier shim-based run and a larger ASP check
  of 3,000 theories/69,066 literals. That older total and the supplied mutation-check totals
  have not been rerun in this review and remain historical evidence.

## 6. Remaining scope and current work

| Item | Requirement | Status |
|---|---|---|
| T4.1 follow-up | Find Bank witnesses or discharge safety lemmas with a suitable proof strategy | unresolved within recorded search limits; not a blocker to the qualified implementation checks |
| O2 | Qualify an optional external SPINdle adapter | optional; no adapter needed by current implementation |
| O4 | Prove translation correctness beyond tested ceremonies | research scope; no universal claim established |

## 7. Reproduce

```bash
# in the repository with JDK 21 available
./mvnw test -Dtest='com.xmen.service.forget.dl.*Test'
# differential oracle (needs: pip install clingo; git clone gvdgdo/Defeasible-Deontic-Logic)
python3 scripts/dl-oracle/asp_crosscheck.py <ddl-dir> target/classes:target/test-classes 400 200 1
```

Actual outputs, proof artifacts, bounded-search logs and concise Maven results are preserved in
[`validation/`](validation/README.md). The supplied older `VALIDATION_LOG.md` remains historical.
