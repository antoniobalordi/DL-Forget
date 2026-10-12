# Validation artifacts — 2026-10-02

These are actual outputs from `ForgetDlHostIntegrationTest` HTTP uploads through the
controller, DL strategy and in-memory ZIP path. Model files are copied byte for byte;
`SHA256SUMS` records their hashes and the accompanying proof/log files. Test inputs are in
`src/test/resources/Forget_Bank_Input.spthy` and `Forget_DL_Invoice_{NoCue,Cue,SignLapse}.spthy`.
The singleton input is constructed in the integration test. Its root state is not produced
by a setup rule, so it is a syntax/fresh-variable shape fixture, not a reachability example.

## Java evidence

`maven-results.txt` preserves concise class/result lines from the real Maven runs without
Surefire environment/classpath dumps. Temurin 21.0.12.1+1, the Maven wrapper and actual project
dependencies/annotation processors were used.

* Final suite excluding `XMenInterfaceTest` and `DisplayScaleSupportTest`: 210 reported,
  zero failures/errors, two intentionally disabled lexer/parser diagnostics.
* Those two existing UI classes: isolated rerun, 13 executed, zero failures/errors/skips.
* Total class coverage across these split runs: 223 reported, 221 executed successfully,
  two disabled. The final suite includes 85 core DL and 17 actual HTTP integration tests.
* The earlier combined 217-test run had one failure and two errors in those UI classes:
  monitor placement/work-area assertions and an absent `#buttonStart` query. Their isolated
  rerun passed without UI code changes; this does not make the original combined run green.
* An intermediate 209-test run found one Invoice assertion scanning into lemma text. Its
  rule boundary was corrected before the passing final run.
* After removing three review-only artifact writes from the test class, its 17 HTTP tests
  and the quoted-builtin grammar test passed again (18 total). Tests no longer require the
  review's temporary directory to exist.

Initial restricted attempts encountered Mockito self-attachment and JavaFX cache permissions;
successful retries used permission to run outside the sandbox and temporary caches. No current
coverage percentage is claimed from older JaCoCo data.

## Tamarin evidence

Tamarin 1.12.0 and Maude 3.5.1 were used. All final Invoice proof runs used `--quit-on-warning`,
`--bound=25`, `--derivcheck-timeout=20` and finished the entire proof with no unfinished goals.
`Invoice_*_proof.spthy` and the corresponding logs retain the verifier results.

| Actual scenario | Functional (send exists) | Wrong_Account_Exists | No_Wrong_Account |
|---|---|---|---|
| No cue | verified, 3 steps | verified, 3 steps | falsified, trace found, 3 steps |
| Matching cue | verified, 3 steps | falsified, no trace, 3 steps | verified, 3 steps |
| Signing-rule lapse with cue | falsified, no trace, 2 steps | falsified, no trace, 2 steps | verified, 2 steps |

The signing-lapse safety result is vacuous because the actual send is skipped. These are
minimal authored human-decision fixtures, not full merchant/bank ceremonies. The initial
Invoice premise pattern `pk(skB)` caused an official message-derivation warning; corrected
inputs bind an opaque `pkB`, while setup still produces `pk(skB)`. Final models pass strict
wellformedness/derivation checks.

The actual Bank model parses, passes strict derivation/wellformedness checks, closes and
completes source saturation. Searches for `Functional`, `Attack_Forget_Exists`,
`No_Reuse_Attack_Without_Compromise` and `Password_Confidentiality`, each at bound 40, timed
out after 120 seconds. `Functional` and `Attack_Forget_Exists` retries with bound 18 and
`--stop-on-trace=SEQDFS` timed out after 90 seconds. All six logs are retained. These results
prove or falsify none of the Bank lemmas.

Bank source comments saying safety/confidentiality “should HOLD” are authored expectations,
not established results. Static inspection suggests additional compromises/chosen challenges
may invalidate those expectations: `Compromise` can reveal the human key, and learning `nH`
can enable an attacker-chosen nonce challenge. This is an unverified inference; the protocol
and lemma formulas were not changed to force a result. No universal translation-correctness
or non-reintroduction theorem follows from these fixtures.

`Singleton_DL_parse.spthy` records parser output: singleton tuple syntax is accepted and
normalized to the scalar. Parsing gives no execution proof.

## Reproduce

With JDK 21 and the normal project dependencies available, run the existing checks from the
repository root:

```sh
./mvnw test '-Dtest=*Test,*Tests,!XMenInterfaceTest,!DisplayScaleSupportTest'
./mvnw test '-Dtest=XMenInterfaceTest,DisplayScaleSupportTest'
```

With Tamarin and Maude installed or available as portable executables, run from this directory
(adapt the Maude path to the local executable):

```sh
tamarin-prover --with-maude=/path/to/maude --prove --bound=25 --quit-on-warning \
  --derivcheck-timeout=20 --output=Invoice_NoCue_recheck.spthy Invoice_DL_NoCue_actual.spthy
tamarin-prover --with-maude=/path/to/maude --prove=Attack_Forget_Exists --bound=18 \
  --stop-on-trace=SEQDFS --quit-on-warning --derivcheck-timeout=20 Bank_DL_actual.spthy
tamarin-prover --with-maude=/path/to/maude --parse-only Singleton_DL_actual.spthy
```

Repeat the Invoice command for `Cue` and `SignLapse`. Bank commands require an external
wall-clock limit to reproduce the recorded timeout budget; `--bound` controls proof depth.
The retained logs include the exact commands used in this review. Review tools and caches
were confined to `/private/tmp/xmen-dl-tools`; no system installation was performed.
