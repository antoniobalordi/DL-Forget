# Forget-DL: Possible Improvements

*Consolidated 6 October 2026. Replaces `Forget_Mutations_and_Translation_Validation.md` and the open items of the earlier roadmap. Each item includes the corrections raised in the CAV review.*

**Companion documents:**

- [1_CAV2027_Assessment.md](1_CAV2027_Assessment.md): why these items matter for the paper.
- [3_Forget-DL_Formal_Description.md](3_Forget-DL_Formal_Description.md): definitions and results referenced below (Thm 1, Props 1–7).

**Status markers:**

- **[verified]**: run through Tamarin, with logs in `experiments/`.
- **[partial]**: some scenarios verified.
- **[to model]**: design only.
- **[proved]**: proof in file 3.
- **[open]**: not started.

---

## 0. Priority overview

| Prio | ID | Item | Part | Effort | Depends on |
|---|---|---|---|---|---|
| **P0** | M3 | SMS one-time code phishing: rule- vs procedure-level lapse | A | Large | E1 |
| **P0** | M2 | Full Invoice / Confirmation of Payee ceremony | A | Medium | — |
| **P0** | E1 | Procedural Neglect (corrected design) | B | Medium | — |
| **P0** | V1–V4, V10–V11 | Declarative certificate with independent extraction and source/output checks | C | Medium | — |
| **P0** | D1–D4 | Fair baselines, configurations, automated runs | D | Medium–large | M2, M3 |
| **P0** | T-A | Translation theorem **or** validator soundness theorem | E | Large | V-series |
| P1 | V5 | Executability certificates | C | Small | — |
| P1 | V6 | Procedural certificate (rule level; procedure identity) | C | Large | V2 |
| P1 | E2 | Runtime / attacker-controlled cues | B | Large | design decision |
| P1 | M4 | OAuth device-code phishing | A | Medium | — |
| P1 | T-B | Relative completeness with exact-procedure lapses (P7) | E | Medium | — |
| P1 | T-C | Honest complexity statement and measurements | E | Small | D3 |
| P1 | F1 | Headless entry point and clean-machine artifact | F | Medium | — |
| P1 | V8, F5 | Multi-session models; fix the Bank lemmas | C, F | Medium | — |
| P2 | M5, E4 | Address poisoning with partial cues (representation repaired) | A, B | Medium | — |
| P2 | E3 | Forget-triggered retry | B | Medium | validity model |
| P2 | F2–F4 | Legacy fixes, SPINdle adapter, upstream ASP report | F | Small–medium | — |
| P3 | M6 | PIN mix-up | A | Small | — |

The minimal package that supports a regular paper is:

1. a corrected E1;
2. declarative assurance, plus procedural assurance or an explicit procedural limitation;
3. the file 3 results;
4. complete M2 and M3 experiments with baselines and controls.

E2 strengthens the paper only if its runtime semantics is established. E3 is not on the critical path.

---

## Part A: Scenarios where forgetting matters

### A.0 Vocabulary

| Term | Notation | What the human loses |
|---|---|---|
| Declarative lapse | `Forget(x)` | A value |
| Rule-level procedural lapse | `ForgetRule('r')` | The ability to apply inference rule `r` anywhere |
| Procedure-level procedural lapse | `ForgetProcedure('pi_…')` | One specific derivation; other uses of the same rule still work |

The possible decisions are:

- **Preserve:** the step is unchanged.
- **Replace:** a same-label, same-format substitute is sent.
- **Skip:** the send is dropped.
- **Neglect:** an internal check is dropped.

Cues are declared with `Cue(c)`, `AssociateCue(c, x)` and `PresentCue(c)`. A present, associated cue reinstates the blocked value at that transition only.

### A.1 Selection heuristics F1–F4

These are **heuristics for choosing examples**, not necessary conditions for a security-relevant effect.

| # | Heuristic | Typical consequence if missing |
|---|---|---|
| F1 | Memory across steps: the value was stored at an earlier step | Usually at most a Skip |
| F2 | Same-type alternatives (same `!Type` label and format) | No Replace. Neglect and Skip remain possible |
| F3 | Crossing contexts: the substitute reaches an exploitable party, or a protective check is dropped | Often no verdict change |
| F4 | A cue, or a human-side derivation step (projection, decryption, comparison) | DL decides exactly as the message-level semantics (Prop 4). No DL-specific result |

Caveats:

- Skip can have relevant *availability* consequences.
- Failing to retain a newly received value can affect a check.
- A cue's presence alone does not guarantee a difference from classical Forget (Prop 2: extra cues never disable a surviving derivation).

**Two tests for inclusion in the paper:**

1. A lemma verdict differs between the baseline and the mutant.
2. For a *DL-specific* result, the verdict also differs between cue and no cue, or between a rule-level and a procedure-level lapse.

### A.2 Scenarios

#### M1: Password mix-up across two banks [verified]

The human holds `pw1` (bank B1) and `pw2` (bank B2). `Forget(pw1)` leads to Replace `pw1 → pw2`, which sends B2's password to B1.

- **Result.** In the single-setup models, `Forget_Attack_Only_B1_Compromised` is **verified** in the mutant (12 steps) and **falsified** in the baseline (6 steps).
- **Role.** Introductory compatibility example only. It is a *classical* Forget result and shows nothing DL-specific.
- **Required fixes:**
  - Preserve the single-setup qualification (`OnlyOnceSetup` restricts the trace set).
  - State the intended *authentication* property, using the prior intent event, ordering and session data, not just the cross-bank login witness.
  - Replace the mis-specified "should HOLD" lemmas.
  - Eventually use fresh per-session state and report a multi-session result (V8).

#### M2: Invoice redirection with Confirmation of Payee [partial]

The payer has forgotten the supplier account `accS`, and the attacker has altered the invoice to show `accAtt`.

| Variant | Decision | Outcome |
|---|---|---|
| No cue | Replace `accS → accAtt` | Attack |
| CoP cue associated with `accS` | Preserve | Safe |
| QR code from the altered document (attacker controls what is shown) | Lapse stands | Attack (faithful model needs E2 or explicit associations) |
| `ForgetRule('sign')` | Skip | No send; safety **vacuous**, so pair with executability (V5) |

- **Result.** Nine judgments on minimal fixtures. The full payer / bank / payee-bank ceremony is **[to model]**.
- **Role.** Recommended running example.
- **Required corrections:**
  - CoP checks the *account name* against the payment details. It does **not** by itself retrieve a remembered account number.
  - Model the bank's CoP response explicitly. Separate bank validation from the human's assumed recovery: a name mismatch may cause cancellation without restoring `accS`.
  - If a trusted stored-payee record supplies `accS`, distinguish new acquisition from cue-triggered accessibility.
  - Compare these mechanisms so that an ordinary protective check is not credited to DL recall ([Pay.UK CoP](https://www.wearepay.uk/what-we-do/overlay-services/confirmation-of-payee/); EU Verification of Payee since 9 Oct 2025).

#### M3: SMS one-time code under real-time phishing [to model], the DL showcase

The SMS is `<'login', site, otp>`. A careful human projects out `site` (procedure `π_site`) and `otp` (procedure `π_otp`), compares `site` with the address bar, and types `otp` only if they match.

| Lapse | Check | Send `otp` | Predicted outcome |
|---|---|---|---|
| `Forget(site)` | Neglected | Sent | Attack |
| `ForgetRule('proj')` | Not performable | Skipped (if every `otp` proof needs `proj`) | Safe, but login fails |
| `ForgetProcedure(π_site)` | Neglected (needs E1) | Sent | Attack |

The intended point is that rule- and procedure-level forgetting give **opposite** outcomes on one ceremony. **This prediction is conditional.** Before claiming it:

- **All site proofs must be blocked.** `ForgetProcedure(π_site)` removes one exact tree. The site may stay usable through another projection tree, a directly stored site value, or another source. Show that every relevant site proof is blocked while an `otp` proof survives.
- **All `otp` proofs must be blocked.** The global `proj` lapse must block every available `otp` proof before Skip follows.
- **Fix the knowledge state.** The engine accepts a message lapse only on a direct member of prior knowledge or of the received message. If only the composite SMS is stored, `Forget(site)` is not a valid declaration. If `site` is stored explicitly, it has a direct proof that is unaffected by `π_site`. The declarative and procedural configurations need one common source model, or explained differences.
- **Show causation.** Demonstrate that the omitted check *causes* the attack, not merely that it correlates with a lapse.
- **Use an existing baseline.** Compare with or reuse existing human-error models in Tamarin ([ETH HISP resources](https://infsec.ethz.ch/research/completed/hisp.html); Basin et al., CSF 2016).

#### M4: OAuth device-code sign-in phishing [to model]

The attacker emails its own `codeAtt` together with the genuine verification link. `Forget(codeTV)` leads to Replace with `codeAtt`, and the human authorises the attacker's device.

Corrections:

- Device-code phishing and its mitigations are already described in [RFC 8628 §§3.3.1, 5.4](https://www.rfc-editor.org/rfc/rfc8628.html#section-5.4). **Do not present it as a new attack class.** The contribution is a systematic forgetting analysis, or a new result about a modelled mitigation.
- Explain why the human holds `codeTV`, how `codeAtt` becomes a candidate, and which *authenticated* display can legitimately cue the intended code. A familiar device name does not identify the device.

#### M5: Cryptocurrency address poisoning [to model]

The address is modelled as `<prefix, body>`. The intended lesson is that a cue on `prefix` does not reinstate a forgotten `body` (Cor 1). **Repair the representation first:**

- If the whole address is a stored member of $K$, its direct `Initial` proof survives forgetting `body`, especially under Case 1.
- If `body` is not stored at all, message-lapse validation rejects it.
- Choose component storage, labels and closure policy consistently. Labelling two whole addresses `'address'` does not make their bodies interchangeable.
- For a stronger test, use several blocked components and show that restoring one leaves another blocked.

#### M6: PIN mix-up on a skimmed terminal [optional]

This is structurally identical to M1. Spend the effort on M2 and M3, and on one more independent application, first.

**Rejected candidates:**

- Bluetooth Numeric Comparison: no memory across steps, and the cue is always present.
- Passkey Entry: no alternative to substitute; failure is only denial of service.
- Purely server-side protocols.

### A.3 Summary

| ID | Ceremony | Replace | Skip | Neglect | Cue | Procedural | Status | Role |
|---|---|---|---|---|---|---|---|---|
| M1 | Bank password mix-up | ✓ | | | | | verified (restricted) | Introductory / compatibility |
| M2 | Invoice + CoP | ✓ | ✓ | | ✓ | rule | partial | Running example |
| M3 | SMS-OTP phishing | | ✓ | ✓ | ✓ | rule vs procedure | to model | DL showcase |
| M4 | Device-code phishing | ✓ | | | ✓ | | to model | Benchmark |
| M5 | Address poisoning | ✓ | | | partial | | to model | Illustrates Cor 1 |
| M6 | PIN mix-up | ✓ | | | | | to model | Optional |

---

## Part B: Extensions to the Forget mutation

Each of these changes the formal behaviour, so each carries proof obligations. They are not merely small code changes.

### E1: Procedural Neglect (P0)

**Today.**

- A *declarative* lapse on an internal-check operand triggers Neglect.
- A *procedural* lapse that makes the operand underivable makes the host fail with "cannot certify internal action" (HTTP 400). This blocks the third row of M3.

**Corrected design:**

1. **Neglect needs a proved negative.** Neglect fires only on a *justified negative* result: $-\partial\,\mathit{Usable}(x)$ with complete coverage (Props 6–7), plus an identified lapse relevant to the operand. The absence of $+\partial$ alone is not enough, because it can reflect unknown coverage, unsupported terms or an inconclusive computation.
2. **Inaccessibility is not rejection.** An inaccessible operand differs from a false comparison. Ordinary protocol rejection keeps its ordinary semantics. Specify the treatment of critical checks as part of the modelled human policy.
3. **Fix the timing.** Place E1 relative to lapse application, relearning, re-acquisition and local cues. The current core returns early on Neglect and skips the send decision. That is why the host certification guard exists.
4. **Re-evaluate the rest of the rule.** After removing a check, re-evaluate the surviving sends and checks. Never leave a send that still needs the unavailable operand.
5. Change host certification to accept the dropped check only under conditions 1–4.

### E2: Runtime and attacker-controlled cues (P1)

**Today.** Cues are static annotations, so a modelled cue is always genuine.

**Proposed.** `PresentCue(c)` takes `c` from an `In(...)` premise. The cue counts only if it *matches* the context of the blocked message.

**Required design work:**

- **Runtime guards.** The compiler builds a propositional theory per scenario. If presence depends on an attacker value, the emitted model must split enabled and disabled recovery through execution guards (for example, two rule variants, match and mismatch), or through another proved symbolic encoding. Treating a variable name as a cue identifier is **not** enough.
- **Two separate questions.** Who can *present* a cue is a different question from what it *retrieves*. The association dictionary and the provenance of a received cue are separate.
- **Wrong recall needs its own mechanism.** Reinstaters restore their associated message; they do not model wrong recall. By Prop 2, extra cues never disable a surviving derivation, so an attacker cue alone cannot cause redirection. Demonstrate the actual attack mechanism under explicit associations and guards.
- **Certificate.** The certificate (Part C) must then hold for every cue value the attacker can send.

### E3: Forget-triggered retry (P2)

When the decision is Skip, optionally Add a ceremony-defined re-request step (for example, "resend code").

- This is a *modelled policy choice*, not a consequence of Skip.
- Extra valid codes require an explicit server validity and expiry model.
- It enlarges the transition and validation obligations.

### E4: Partial cues over composite values (P2)

No engine change is needed: associate the cue with the component it really covers. Choose a representation in which forgetting a component actually blocks the intended derivation (see M5). Otherwise, opaque whole-value knowledge bypasses it.

---

## Part C: Translation validation

### C.1 Idea and honest scope

X-Men's decision is about symbolic terms. Tamarin analyses the emitted rules under matching, unbounded interleaving and attacker instantiation. Per-model validation asks Tamarin itself to prove a safety invariant over all traces of each emitted model:

```
lemma Certificate_No_Forgotten_Reuse:
  "All h x #i #j. Forgot(h,x) @ #i & Uses(h,x) @ #j & (#i < #j | #i = #j)
     ==> (Ex #k. Reacquired(h,x) @ #k & #i < #k & (#k < #j | #k = #j))
       | Reinstated(h,x) @ #j"
```

**What it proves.** An invariant of the *emitted instrumentation* in one model. It is not a source-to-target refinement in the sense of Pnueli et al. (TACAS 1998). Three logical counterexamples show the gap:

1. **Omitted marker.** A translator omits a `Forgot` marker. The premise never fires, so the certificate passes despite forbidden use.
2. **Fabricated recovery.** A translator emits a fabricated `Reacquired` or `Reinstated`. The exception excuses forbidden use.
3. **Wrong but unforgotten output.** A translator emits a wrong value that is not forgotten, or drops a permitted branch. No forgotten reuse occurs, yet the replacement policy is violated or behaviour is removed.

**So far.** In the Bank demonstration, `experiments/bank/cert_ok.spthy` **verifies** (149 steps) and `cert_bug.spthy` (whose `Out` still sends `pw1`) is **falsified** (7 steps). The events were **hand-written**. X-Men strips annotations and generates none of this yet.

### C.2 Work items

| # | Task | Effort | Notes |
|---|---|---|---|
| V1 | Emit `Forgot($H,x)` on the lapse rule only | Small | Time ordering covers later rules |
| V2 | **Independent `Uses` extractor** over the *final* rule text | Medium | See C.3 |
| V3 | `Reacquired` exception | Small | Must be validated independently (V11) |
| V4 | `Reinstated` exception, at the same step only | Small | Must be validated independently (V11); with E2, quantify over attacker cues |
| V5 | **Executability** certificates | Small | Not "liveness". An `exists-trace` per mutated rule, plus full-session witnesses where completion is expected. Intended failure is documented for Skip configurations |
| V6 | Procedural certificates | Large | See C.4. Otherwise state "declarative lapses only" |
| V7 | Run across the benchmark; report certificate time next to security lemmas | Follows | A timed-out certificate is inconclusive, not passed |
| V8 | Multi-session models | Medium | Scope state by agent and session; is memory shared across sessions? |
| V9 | Paper text: exact guarantee and trusted base | Small | — |
| **V10** | **Source/output obligation comparison** | Medium | Check, against the *source* model, that every source lapse has its marker and every permitted branch is present. Closes counterexamples 1 and 3 |
| **V11** | **Independent recovery validation** | Medium | Justify `Reacquired` and `Reinstated` from the source model and the actual operational premises, not from the engine's decision. Closes counterexample 2 |
| **V12** | **Fault injection** | Small | Inject representative translator faults and show they are caught: wrong output, omitted lapse marker, false recovery, receive-pattern bypass, forgotten procedural step |
| **V13** | Instantiation and alpha-renaming tests | Small | Structural hashes separate printed terms. They do not prove invariance under renaming, substitution or sessions |

### C.3 Design rules for the extractor (V2)

- **Define "use"** before writing code. A use is one of: a derivation hypothesis, a constructed output, an implicit destructor in a receive pattern, or an internal-check operand. A forgotten term inside an opaque known ciphertext is **not** automatically a use. Align the definition with the three closure policies.
- **Extract independently of the decision.** Obligations come from the emitted rules, never from the engine's decision. Otherwise the certificate merely restates the decision.
- **Cover everything the rule emits:** receive-pattern operations, non-send operational facts and co-sends.
- **Check repeated episodes:** repeated lapses, relearning and local cue expiry.
- **Keep it auditable.** Keep the extractor small and separate from the engine, because it is in the trusted base.

### C.4 Procedural certificates (V6)

- **Rule level.** Detect implicit destructors in premises (`In(senc(x,k))` with `k` known is a decryption) and constructors in conclusions. Emit `UsesRule($H,'sdec')`. The lemma is `ForgotRule & UsesRule ⇒ F`.
- **Procedure level.** Requires the identity and provenance of the derivation, to tell `π_site` from `π_otp`. A surface-syntax `UsesRule` does not establish which proof tree an execution used when alternatives exist. A justified proof-identity interpretation is needed.
- **If procedural forgetting is the paper's main verified capability, V6 is P0.** It can be met by a sound procedural validator, or by a separate scoped translation proof. A declarative invariant cannot substitute for it.

### C.5 What the paper can then claim

| Guarantee | Source |
|---|---|
| The decision is correct for the cognitive state | **[proved]** file 3, Thm 1, Props 1–7; reasoner checked against ASP |
| No forgotten value is reused in the emitted model, on any trace, under any instantiation | Certificate (V1–V4), **plus** V10 and V11 for markers and exceptions |
| Mutated rules are executable | V5 |
| Procedural lapses are respected | V6, or a stated limitation |
| Nothing that should remain possible was removed | **Not** covered by the certificate. Needs V10 (branch presence) and a coverage argument |

**Trusted base:** the verifier, the event extractor, the source-state interpretation and the recovery validation. **Not** "Tamarin plus the extractor only".

---

## Part D: Evaluation

### D.1 Configurations (D1)

Use the same protocol, attacker, setup and session restrictions, formulas, labels and budgets for every configuration:

1. unchanged ceremony;
2. classical Forget, as a *faithful* reference implementation or a defect-free restricted comparison, so that semantics is not confounded with legacy bugs D1–D10;
3. procedural forgetting without cues;
4. DL with cues;
5. a direct Boolean evaluator of file 3, Definition 6, run on the same derivations. This isolates reasoning overhead, but does not validate enumeration.

### D.2 Measurements (D2)

- **Analysis outcomes:** mutant counts per configuration; completed verdicts; execution witnesses; explanation examples; uncertain, partial and unsupported outcomes.
- **Cost:** generation, reasoning, validation and Tamarin time, measured separately; peak memory; sensitivity to the caps (512 / 250,000 / 256).

### D.3 Research questions (D3)

| RQ | Question | Measure |
|---|---|---|
| RQ1 | Where does the extension change analysis outcomes relative to classical Forget? | Verdict differences per configuration, with causes |
| RQ2 | Effect of cues: genuine, absent, attacker-controlled (if E2) | Verdicts per cue configuration |
| RQ3 | Cost | Phase times, $\lvert\Pi\rvert$, $\lvert T\rvert$, $\lvert D\rvert$, memory |
| RQ4 | Coverage | Frequency of `UNKNOWN_COVERAGE` and `partial`, and sensitivity to caps |

### D.4 Benchmark rules (D4)

- **Declare inclusion criteria.** Report screened-out and unsupported cases. **Do not** select only favourable verdict flips.
- **Include no-change controls.**
- **Prefer depth over count.** A few realistic, diverse, fully analysed ceremonies beat ten near-identical fixtures.
- **Pair every safety claim** with an executability or availability property.
- **Report restrictions explicitly.** Report single-setup restrictions as restrictions. No attack in a restricted baseline does not establish security of the unrestricted one.

### D.5 Forgetting-sensitivity scan (D5)

For each human transition, generate the following mutants:

- `Forget(m)` for every known `m`;
- `ForgetRule(r)` for every rule used;
- **`ForgetProcedure(π)` for the relevant procedures**, which the current scan omits and which M3 needs;
- cue and no-cue variants.

Run every lemma, and the certificate, on the baseline and on all mutants. Record verdict changes and their causes. Keep the full table, including no-change rows. It is the RQ1 data.

X-Men generates the mutants already. The missing piece is a driver script that loops over annotations and calls Tamarin.

---

## Part E: Theory

| ID | Item | Status |
|---|---|---|
| — | Boolean characterisation, per-hypothesis recovery, joint = separate, monotonicity, inertness of $\omega > d$, agreement with Algorithm 1, procedural separation, positive-decision soundness, completeness without procedure lapses | **[proved]** in file 3; needs co-author review, then move into the paper |
| T-A | **Translation guarantee.** Either Conjecture T1 (file 3 §7.3) for the supported fragment, or a validator soundness theorem stating what passing V1–V11 implies | **[open]**, P0 |
| T-B | **P7 with exact-procedure lapses.** Prove that under (N1)–(N2) a repeated message on a branch implies a cyclic decryption dependency, so the implementation's acyclicity test makes the finite basis sufficient. Same for the re-acquisition search | **[open]**, P1 |
| T-C | **Complexity.** State the implementation's bound (constant rounds, at most quadratic in $\lvert D\rvert$) and the exponential enumeration and candidate costs. Measure them (RQ3). Optimise only if measurements warrant it | **[open]**, P1 |
| T-D | **Correct the manuscript.** Remove "finite normal derivations"; fix the $\omega > d$ reading; state E8; separate failure to prove $+\partial p$, a proved $-\partial p$, and $+\partial\neg p$; move the normative assumptions A1–A13 from developer docs into the paper | **[open]**, P1 |
| T-E | **Human abstraction.** Explain which human or device competences each rule abstracts, with the actual UI or task for each procedural example. Treat lapses and associations as analyst hypotheses. Specify cue control and per-transition scope | **[open]**, P1 |

---

## Part F: Engineering and artifact

| ID | Item | Priority |
|---|---|---|
| F1 | **Headless entry point** (no GUI flag) and a Docker/VM artifact containing X-Men 2.0, JDK 21, Tamarin 1.12.0, Maude 3.5.1, clingo, all inputs and scripts that rebuild every table. Pin versions and the oracle revision; include traces, proof logs, checksums and licences; Zenodo DOI. The local review cache is not an artifact | P1 |
| F2 | Fix legacy defects D1–D7 and D10 behind before/after regression fixtures (needed for a fair classical baseline, D1) | P2, but P0 if the legacy tool is the baseline |
| F3 | Optional SPINdle backend behind `DefeasibleReasoner`, qualified with the same conformance tests and oracle | P3 |
| F4 | Report the ASP team-defeat binding defect upstream (`gvdgdo/Defeasible-Deontic-Logic@679c638`) before citing it | P2 |
| F5 | Bank: replace the "should HOLD" lemmas with properly restricted ones; find witnesses or discharge proofs (helper lemmas or oracles); use fresh per-session state | P1 |
| F6 | Get the combined full host suite green (two GUI classes fail only in the combined run) | P2 |
| F7 | Broaden host scope only where an experiment needs it: non-linear State graphs, compound receive rebinding | P3 |
