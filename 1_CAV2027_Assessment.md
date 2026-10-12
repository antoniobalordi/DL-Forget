# CAV 2027 Assessment: X-Men 2.0 / Forget-DL

*Consolidated 6 October 2026. Revised the same day against the parent paper. Supersedes the earlier proposal, assessment, review and validation notes.*

**Parent paper (published baseline):** I. Sandu, R. Kar, M. Quamara, A. Balordi and L. Viganò, *Formal Modeling and Analysis of Human Memory Lapses in Security Ceremonies*, SCP 2026 proceedings, in press. This is the peer-reviewed version on the [King's Research Portal](https://kclpure.kcl.ac.uk/ws/portalfiles/portal/384018980/SCP2026_PURE.pdf). Below it is called **[SCP26]**.

**Companion documents:**

- [2_Improvements.md](2_Improvements.md): the work list.
- [3_Forget-DL_Formal_Description.md](3_Forget-DL_Formal_Description.md): the paper-ready formal account.

These are research judgments, not acceptance predictions.

---

## 1. Bottom line

- **Classical Forget is now published prior work.** [SCP26] contains:
  - the Forget mutation;
  - the Forget set;
  - the blocking Cases 1–3;
  - derivability under forgetting (Def. 4);
  - Algorithm 1;
  - the Bank Login password-reuse attack;
  - X-Men 2.0 as a "minor contribution";
  - the primitive/derivable classification of mutations.

  **None of these can be a contribution of the CAV paper.** The CAV paper must be a strict extension of [SCP26] and must say so.
- **The delta is clear and substantial.** [SCP26] has no:
  - procedural forgetting;
  - cues or recovery;
  - Defeasible Logic;
  - explanation trace;
  - coverage handling;
  - metatheory beyond Def. 4;
  - account of whether the generated Tamarin model respects the decision;
  - evaluation beyond one example.

  Everything in file 3 except §§2.1–2.2 and Def. 4 is new relative to [SCP26].
- **Target: a regular CAV research paper** (author's decision). The work is in scope ("formal methods for security"), but **the current evidence does not support a strong regular submission**. Such a paper needs three things:
  1. original semantic and algorithmic results beyond [SCP26];
  2. a justified connection between the cognitive decision and the executions of the generated Tamarin models;
  3. an evaluation in which the *extension*, not classical Forget, changes analysis outcomes on realistic ceremonies.
- **[SCP26] raises the bar.** A reviewer who reads it will ask what CAV gets beyond the SCP paper. Answers that will not satisfy:
  - "the same attack again, re-encoded in DL";
  - "we fixed bugs in X-Men 2.0".
- **The strongest honest framing today:** a conservative extension of the [SCP26] Forget semantics to procedural lapses and cue-triggered recovery. It comes with a Defeasible Logic specification that reduces provably to a Boolean test, a coverage-aware implementation, and preliminary end-to-end evidence.
- **The framing to earn by January:** a formal account of declarative and procedural forgetting and recovery, with justified analysis and translation guarantees, and security applications that [SCP26] cannot express.
- **Fallback:** a tool paper. It must present itself as a significant update *over the X-Men 2.0 of [SCP26]*.
- **Go/no-go milestone (target: mid-November 2026):** both of the following must be done:
  - one realistic ceremony whose verdict changes *because of* procedural lapses or cues, compared against [SCP26] semantics;
  - a chosen translation guarantee (theorem or validator).

  If either is missing by early January, use the tool track or a later venue rather than submit a thin regular paper.

---

## 2. Venue facts

| Item | Value |
|---|---|
| Conference | CAV 2027, Amsterdam, 19–23 July 2027 |
| Submission deadline | **20 January 2027, 23:59 AoE**; notification 23 April 2027 |
| Regular paper | 18 pages LNCS excluding references/appendix, **double-blind**, artifact optional |
| Tool paper | 18 pages, **single-blind**, artifact evaluation **mandatory**; significant updates without new research are allowed |
| Application / case-study paper | 10 pages |
| Tool artifacts | Registration 8 March 2027, due 11 March 2027; detailed instructions pending |
| Simultaneous submission | Forbidden |
| Sources | [CFP](https://conferences.i-cav.org/2027/cfp/), [artifacts](https://conferences.i-cav.org/2027/artifacts/) |

A universal translation theorem is **not** an explicit tool-track requirement. The "6–10 ceremonies" target in earlier notes was a planning choice, **not a CAV rule**.

---

## 3. Relationship to the parent paper [SCP26]

### 3.1 What [SCP26] already contains

| [SCP26] element | Where | Consequence for CAV |
|---|---|---|
| Forget as a primitive human mutation that triggers Neglect, Replace or Skip | §§1, 5, 7 | Background only |
| Monotone knowledge plus a separate Forget set | Def. 3 | Background; our $K$ and $\mathit{Forget}$ are this |
| Blocking Cases 1–3 and derivability under forgetting | §5.2, Def. 4 | Background; file 3, Def. 4, restates it |
| Algorithm 1 (Neglect / Preserve / Replace / Skip) | §5.3 | Baseline semantics for the evaluation |
| Re-acquisition by re-receipt or derivation from a received message | Ex. 3, §5.3 | Background; we make it precise (A6) |
| Bank Login attack: forgotten $PW_1$, $PW_2$ sent, SG1 `Human_intends_Bank2_if_Bank2_OK` falsified, SG2 `Password2_confidential` verified | §§3, 6.2 | **Not a new result.** Use only as a compatibility check |
| X-Men 2.0 tool (modular architecture, chatbot), GitHub `rish-kar/X-Men_2.0` | §6.1 | A tool paper must be a *significant update* over this version |
| Primitive/derivable classification (Table 1) | §7 | Background, if mentioned at all |
| Related work: Basin et al. CSF 2016; Bella et al. JCS 2022; Fila and Radomirović CSF 2024; usable-security literature on passwords | §8 | CAV positioning must go further (§3.5) |

**[SCP26]'s own future work** is "adding to the knowledge, or replacing or adding internal actions". It does not anticipate procedural forgetting or cues, which helps the novelty argument.

### 3.2 What is new relative to [SCP26]

| New element | Evidence | Strength |
|---|---|---|
| Procedural forgetting: rule-level $\bar R$ and exact-procedure $\bar\Pi$ | File 3 §§3–4; implemented and tested | **Core novelty** |
| Cue model and per-hypothesis, per-transition recovery that does not erase the lapse | File 3 Def. 6, Cor. 1 | **Core novelty** |
| DL(∂) specification, with tagged explanations | File 3 §4, §8.3 | Novel as a specification layer. Thm 1 shows it reduces to a Boolean test, so **do not sell it as reasoning power** |
| Formal results: Boolean characterisation, joint = separate, monotonicity, conservative extension of [SCP26] Def. 4 (Prop. 4), expressiveness separation (Prop. 5), soundness and partial completeness under truncated search (Props. 6–7) | File 3 §§5–6 | **New**; needs co-author check |
| Coverage-aware decisions (`UNKNOWN_COVERAGE`) instead of [SCP26]'s implicit "construct all derivations" | File 3 §6.4 | New and important. [SCP26]'s search is not finite in general (§3.3) |
| Candidate admissibility: a Replace variant must itself be usable | File 3 §6.3 | New. [SCP26] Alg. 1 emits a variant whenever $R_{\bar m} \neq \emptyset$ |
| Persistent cognitive state across *all* later human transitions | Host integration (D9) | New; [SCP26] evaluates annotated transitions |
| Host certification of surviving operands and outputs; translation-validation certificate | File 3 §7 | New, but not yet a guarantee (G2) |
| Engineering repairs D1–D10 to the [SCP26] implementation | Semantic contract | **Supporting only.** Presenting them as contributions invites "so [SCP26]'s tool was wrong?" |

### 3.3 Published claims in [SCP26] that the CAV paper corrects

The CAV paper must correct these openly and politely ("we refine", "a more precise treatment"). Silent divergence from a published paper by the same group will be noticed.

| [SCP26] statement | Issue | Treatment in CAV |
|---|---|---|
| §4.2: "we only need to search the finite space of normal derivations" | False under immediate-inverse normality: $K = \{\mathsf{senc}(a,b), \mathsf{senc}(b,a), a\}$ has infinitely many normal derivations of $a$ (E3) | File 3, Rem. 1 and Props. 6–7: finite basis, explicit coverage. This is a genuine technical contribution, so present it as one |
| §4.1 (after Def. 2): an atom's format is the atom itself | Forbids [SCP26]'s own $PW_1 \mapsto PW_2$ replacement (E2) | Atom format `atom` (A2) |
| Alg. 1, l.2–3 and Ex. 2: forgetting the just-received $m_1$ leaves the Forget set unchanged | The DL algorithm (corrected Section 7) adds $m_1$ to $\mathit{Forget}$ | **Decide, and justify, which semantics the CAV paper uses.** If it differs from [SCP26], Prop. 4 (agreement) holds only with that difference stated |
| Alg. 1, l.8–9: Neglect when $m$ is used in $a$; the text says "the same message may still subsequently be sent … assuming the forgotten information is not required" | Algorithm 1 does not check that assumption: the Neglect branch skips the send derivability test (E7/A8) | Host certification of surviving outputs (file 3 §7.2) closes this. A real, citable refinement |
| Alg. 1, l.16–26: a variant is emitted for every combination once $R_{\bar m} \neq \emptyset$, per derivation | Emitted variants need not be derivable under forgetting (Cases 2–3), duplicates arise across derivations, and self-replacement is possible | DL decision requires $+\partial\,\mathit{Usable}(m')$; $\bar m' \neq \bar m$ (A5) |
| §6.2: Tamarin "automatically produces" the SG1 attack and verifies SG2 on the X-Men 2.0 output | Our runs on the *current* HTTP output of the Bank model left **every lemma search unfinished**. Only a manually restricted single-setup model gives a verdict, and with a different lemma | **Must reconcile before submission** (G5): re-run [SCP26]'s exact published model and lemmas, and document any model differences |

### 3.4 Overlap, anonymity and submission risks

- **Simultaneous submission.** [SCP26] is accepted and in press, so it is prior publication, not a conflict. The **open question** is the *long manuscript* ("Human Memory Lapses in Security Ceremonies", whose Section 7 is the DL material). If that version, including the DL section, is under review at a journal (for example an extended SCP version), the CAV paper overlaps it. **Resolve this first (G4).** Options:
  - remove the DL, procedural and cue material from the journal version;
  - or make the journal version cite the CAV paper;
  - or drop one of the two venues.
- **Text reuse.** Definitions, the Bank Login figures and Algorithm 1 must be summarised and cited, not copied. Keep background to about 2 pages.
- **Double-blind (regular track).** Cite [SCP26] in the third person ("Sandu et al. introduced …"). The GitHub URL `rish-kar/X-Men_2.0`, the "X-Men 2.0" name in self-referential phrasing, and the KCL/CASD affiliations identify the authors. Use an anonymised artifact link at submission. The tool track is single-blind and avoids this issue.
- **Author overlap.** The SCP author list includes A. Balordi. Agree authorship and contribution boundaries for the CAV paper with all [SCP26] co-authors before writing.

### 3.5 Positioning beyond [SCP26]'s related work

[SCP26] §8 compares only with Basin et al. (CSF 2016), Bella et al. (JCS 2022) and Fila and Radomirović (CSF 2024). It argues that their non-monotonic availability sets enlarge Tamarin's search space. For CAV, add:

- **Fila and Radomirović, revisited.** Their "losing" ceremonies model information loss *inside* Tamarin. Forget-DL keeps knowledge monotone and decides *outside* the verifier. Make this a measured comparison (cost and expressiveness) on one shared example, not only an argument. [SCP26] asserts the search-space explosion but does not measure it.
- **Fortis (FMCAD 2023):** environmental deviations and robustness ([link](https://acme.able.cs.cmu.edu/pubs/uploads/pdf/fortis-fmcad.pdf)).
- **Translation validation:** Pnueli, Siegel and Singerman, TACAS 1998.
- **Defeasible Logic:** Nute; Antoniou et al. TOCL 2001; Governatori et al. (SPINdle, ASP encodings); Maher (complexity).
- **Cognitive grounding for procedural memory and cues:** Ryle; Cohen and Squire; Anderson; Tulving's encoding specificity. [SCP26] grounds only declarative forgetting (Bartlett; password studies).
- **Earlier X-Men work:** Sempreboni and Viganò, JCS 2023 ([link](https://kclpure.kcl.ac.uk/portal/en/publications/a-mutation-based-approach-for-the-formal-and-automated-analysis-o/)); Quamara and Viganò, ICSA-C 2025 (Disorder).

---

## 4. What we have

### 4.1 Contributions and their status

Status key:

- **Proved**: a proof exists in file 3.
- **Done**: implemented and tested.
- **Partial**: some evidence, but not enough for the claim.
- **Open**: nothing usable yet.

"New vs [SCP26]" says whether the item counts as a contribution.

| ID | Contribution | New vs [SCP26] | Status |
|---|---|---|---|
| C1 | Typed human state: $K$, $\mathit{Forget}$ (from [SCP26]), plus $\mathit{PForget} = (\bar R, \bar\Pi)$ and the cue model | Partly ($\mathit{PForget}$ and cues) | Done; file 3 §3 |
| C2 | Compilation to DL(∂) with derivation-indexed `Via`, three defeater kinds, per-hypothesis reinstaters | Yes | Done; file 3 §4 |
| C3 | Algorithm 2: state update, Neglect, re-acquisition, lazy candidates, joint decision, coverage policy | Yes (refines [SCP26] Alg. 1) | Done (errata E1–E8 resolved) |
| C4a | Boolean characterisation (P6), per-hypothesis recovery, joint = separate, monotonicity, inertness of $\omega > d$ | Yes | **Proved** (file 3, Thm 1, Cor. 1, Props. 1–3); needs co-author check |
| C4b | Conservative extension of [SCP26] Def. 4 (P4) | Yes | Proved relative to a common derivation set (Prop. 4). The just-received-$m_1$ divergence (§3.3) must be stated. **Not** a comparison with the [SCP26] tool |
| C4c | Procedural expressiveness separation from the [SCP26] Forget-set semantics (P5) | Yes | **Proved** (Prop. 5), scoped to fixed $K$, Case 1 (the case [SCP26] adopts) and the observed targets |
| C4d | Soundness of positive decisions under truncation; completeness without exact-procedure lapses | Yes; also repairs [SCP26] §4.2 | **Proved** (Props. 6–7) |
| C4e | Relative completeness with exact-procedure lapses (P7) | Yes | **Open**; implementation is conservative |
| C5 | Translation soundness (T1) | Yes; [SCP26] makes no such claim | **Open**. Host certification (symbolic) plus one hand-instrumented certificate demo |
| C6 | Cognitive explanation trace | Yes | Done |
| C7 | Tool: Forget-DL in X-Men 2.0, request-local processing, explicit unsupported/unknown handling, validated reasoner | Update over [SCP26] §6 | Done |
| C8 | Evaluation on a ceremony benchmark | Yes; [SCP26] has one example | **Partial**: Invoice fixtures plus a restricted Bank model; no fair baseline |
| — | Side result: a team-defeat attacker-binding defect in a public ASP encoding of DL(∂) | Yes | Done; report upstream before citing |

### 4.2 Implementation strengths reviewers can check

- **Size and isolation.** The pure-Java core is about 2.4 kLOC, isolated from the [SCP26] Algorithm 1 path. DL mode is selected per request, so the [SCP26] baseline remains runnable side by side, which is useful for the evaluation.
- **Semantic distinctions are preserved:**
  - stored knowledge versus accessibility (the [SCP26] principle, extended to cues);
  - a defeater versus a proof of negation;
  - rule-level versus exact-procedure lapses;
  - message-specific cues;
  - the intended send preferred over replacements.
- **Honest incompleteness.** `UNKNOWN_COVERAGE` replaces [SCP26]'s implicit assumption of a finite, complete derivation search.
- **Host integration** re-evaluates later unannotated sends under persisting lapses. It applies structural substitutions inside function arguments, certifies surviving operands and outputs (closing the gap in the Neglect branch of [SCP26] Alg. 1), and rejects unsupported inputs with HTTP 400.
- **Current scope:**
  - a linear acyclic State chain per human;
  - terminal alternatives;
  - restricted directed-receive rebinding;
  - the free-constructor/encryption fragment;
  - static, scenario-annotated cues.

### 4.3 Executed evidence

| Evidence | Result | What it establishes |
|---|---|---|
| Core Maven run (5 Oct 2026) | **85 tests, 0 failures/errors/skips** | Paper examples, properties, enumeration and reasoner checks pass |
| ASP differential oracle, seed 1 (5 Oct) | **600 theories, 14,034 literals, 0 mismatches** against the corrected encoding | Reasoner agreement on the generator's fragment |
| Same, upstream encoding | 14 mismatches, all inconsistent literals; none in Java | Reproduces the upstream binding defect |
| Earlier oracle, seeds 1–5 (historical) | 3,000 theories, 69,066 literals, 0 mismatches | Not rerun |
| Full host suite (2 Oct) | 210 tests green (excluding 2 GUI classes) plus 13 GUI tests in isolation; the combined run had 1 failure and 2 errors in existing GUI classes | **Not** an all-green combined run |
| HTTP integration tests (2 Oct) | 17 passing | Later sends, procedural and cue annotations, channels, unsafe-use rejection |
| Invoice, HTTP-generated outputs | 9 lemma judgments closed at bound 25. No cue: wrong-account trace, safety falsified. Cue: safety verified. Sign lapse: send skipped, safety **vacuous** | Mechanism demonstration on **minimal authored fixtures**. The only DL-specific end-to-end evidence so far |
| Bank, current HTTP output | Parses and closes; **every lemma search unfinished** (timeouts at bounds 40 and 18) | No result. **Conflicts with [SCP26] §6.2** (see §3.3) |
| Bank, single-setup restricted model (5 Oct) | Mutant: `Forget_Attack_Only_B1_Compromised` **verified** (12 steps). Baseline: same witness **falsified** (6 steps) | Reproduces the *kind* of attack in [SCP26] under a restricted trace set. **Not new relative to [SCP26]**, and uses a weaker lemma than its SG1 |
| Bank certificate, hand-instrumented | Correct model **verified** (149 steps); faulty model **falsified** (7 steps) | The no-reuse invariant catches one output-rewriting fault. Events were **hand-written** |

Logs for the 5 October runs are in `experiments/cav2027-assessment/`, and the Bank models in `experiments/bank/`. Tamarin runs used `--quit-on-warning --bound=25 --derivcheck-timeout=20`. They ran concurrently, so their times are not performance data.

---

## 5. What is missing

### 5.1 Blocking gaps (P0: must close for a regular paper)

| # | Gap | Why reviewers will object | Completion criterion |
|---|---|---|---|
| G1 | **The advance over [SCP26] must be visible in results, not only in definitions** | [SCP26]'s only example (Bank, password replacement) is reproducible with classical Forget. A paper whose case studies are also expressible in [SCP26] does not show the value of the extension | At least one realistic ceremony where the verdict differs between [SCP26] Algorithm 1 semantics and Forget-DL, *caused by* a procedural lapse or a cue (M3 or M2 in file 2). Prop. 5 (done) gives the formal separation; this gives the practical one |
| G2 | **Decision-to-execution connection (T1)** | [SCP26] gives none, so a CAV paper is the place to supply it. A correct symbolic decision does not imply that Tamarin's instantiated rules respect the cognitive state | A scoped translation theorem, **or** a validator with a soundness theorem whose instrumentation and recovery events are independently justified (file 2, Part C) |
| G3 | **Fair end-to-end evaluation** | [SCP26] evaluated one ceremony; the CAV paper must clearly exceed that | A baseline that is a *faithful* executable [SCP26] Algorithm 1 (not the defective legacy code); realistic, diverse, fully analysed ceremonies; completed verdicts plus execution witnesses; automated extraction (file 2, Part D) |
| G4 | **Overlap with the long / journal manuscript** | If the long manuscript containing the DL section is under review elsewhere, the CAV submission violates the simultaneous-submission rule | Written confirmation of the long manuscript's status, and a split of material agreed by all [SCP26] co-authors (§3.4) |

### 5.2 Important gaps (P1)

| # | Gap | Completion criterion |
|---|---|---|
| G5 | **Reproduce [SCP26]'s own Bank result** | Re-run its published model (GitHub version cited in [SCP26]) and lemmas SG1 and SG2. Either obtain the published verdicts, or document why the current model differs. A CAV reviewer who reproduces [SCP26] and the artifact must see consistent results |
| G6 | **Claims that disagree with the implementation**, including [SCP26]'s (§§3.3, 6) | Coverage, normal-derivation finiteness, proof identity, complexity and host-scope claims aligned with the code |
| G7 | **"Why Defeasible Logic?"** | State Thm 1 openly. Present DL as the specification and explanation layer that makes three blocker kinds and prioritised recovery uniform. Show the explanation value in the evaluation |
| G8 | **Human abstraction and cue threat model** | [SCP26] grounds forgetting in password-memory studies. Procedural lapses and cues need their own grounding (Ryle, Cohen and Squire, Tulving), plus explicit cue provenance and control. Claims are about modelled possibilities, not prediction |
| G9 | **Reproducible artifact** | A clean-machine, headless workflow; pinned versions; an anonymised link for double-blind review |
| G10 | **Relative completeness with exact-procedure lapses (P7)** | A proof for the characterised fragment, or an explicit limitation statement |

---

## 6. Claims to correct before writing

| Current claim (in [SCP26], the manuscript section, or earlier notes) | Correction |
|---|---|
| "The finite space of normal derivations" ([SCP26] §4.2; manuscript §3.2) | False (E3). Use a finite basis with explicit coverage (file 3, Rem. 1, Props. 6–7). Present this as a refinement of [SCP26] |
| An atom's format is the atom itself ([SCP26] §4.1) | Use format `atom` (A2); otherwise [SCP26]'s own PW1→PW2 replacement is not admissible |
| Forgetting the just-received message leaves Forget unchanged ([SCP26] Alg. 1, Ex. 2) versus adds it (DL section) | Pick one, justify it, and state it in Prop. 4 |
| Neglect leaves the send unaffected "assuming" the forgotten value is not needed ([SCP26] §5.3) | Make it a checked condition (host certification), not an assumption |
| "$\omega > d_\pi$ makes the defeater win" | Inert in DL(∂) (Prop. 3). What matters is that $d > \omega$ is absent and $\rho > \omega$ is present |
| "The DL(∂) reasoner runs in linear time" | Not true of this implementation. A justified bound is a constant number of rounds, at most quadratic in $\lvert D\rvert$ (file 3 §8.2) |
| "Polynomial for fixed derivation depth and replacements" | State precisely or drop |
| "The engine and compiler are entirely untrusted" (certificate) | Premature; recovery and use labels must be validated independently (file 3 §7.3) |
| "A wrong translation fails its certificate" | Only for faults visible to the instrumented invariant |
| "Completeness follows from L1" | A local decision characterisation does not give completeness of enumeration, candidates or emitted behaviour |
| `OnlyOnceSetup` is "a termination fix" | It changes the analysed trace set. Present it as a restricted scenario |
| Invoice signing-lapse "safe" | Vacuous, because the send is skipped. Pair with executability lemmas |
| "Agrees with Algorithm 1" (test) | Compares against the *new* enumerator, not the [SCP26] implementation |
| Bank lemmas "should HOLD" (source comments) | Unverified. Use [SCP26]'s SG1 (human-intent authentication via `U_LoginRequest`) and SG2 as the property definitions instead |
| "Drop ceremonies with no flips" | Keep no-change controls and unsupported or unresolved cases |

---

## 7. Plan to submission

| When | Milestone | Exit criterion |
|---|---|---|
| By 20 Oct 2026 | **Overlap and authorship.** Status of the long / journal manuscript; split of material agreed with all [SCP26] co-authors; frozen contribution list *stated as deltas over [SCP26]*; choice of translation guarantee | G4 resolved; one-page "beyond [SCP26]" claims list agreed |
| By 31 Oct 2026 | **Baseline.** Re-run [SCP26]'s Bank model and lemmas (G5); build a faithful executable [SCP26] Algorithm 1 for comparison | [SCP26] verdicts reproduced or discrepancies documented |
| By 15 Nov 2026 | **Go/no-go.** One realistic DL-specific ceremony (M3 preferred, else M2) compared against [SCP26] semantics, with completed verdicts and witnesses; translation obligations closed for it | A verdict difference attributable to procedural lapses or cues, which [SCP26] cannot produce |
| By 15 Dec 2026 | Benchmark expanded; sensitivity scan automated; phase costs, memory, coverage measured; failures and timeouts retained; optional cost comparison with Fila and Radomirović's in-verifier forgetting | Scripted tables reproduce from a clean checkout |
| By 5 Jan 2027 | Clean Linux reproduction through a headless entry point; anonymised artifact | Second person reproduces every table |
| By 10 Jan 2027 | **Readiness decision**: regular paper, tool paper, or later venue | Regular only if G1–G4 are closed |
| 20 Jan 2027 | Submit | — |

**Not prerequisites:** a user study, SPINdle integration, new equational theories, a second verifier, or a general competing-cue language. Also **not**: revisiting [SCP26]'s primitive/derivable classification.

---

## 8. Draft paper shape (regular track, 18 pages)

**Working title.** *Forgetting What You Know and How You Know It: Procedural Lapses and Cued Recovery in Mutation-Based Ceremony Verification.* The subtitle signals the delta over [SCP26]'s declarative Forget.

| § | Content | Pages |
|---|---|---|
| 1 | Introduction: what [SCP26]'s Forget cannot express (a procedural lapse, a cue), the running example (SMS-OTP or Invoice + CoP), contributions *as deltas* | 2 |
| 2 | Background, condensed and cited: Tamarin ceremonies, X-Men mutations, [SCP26] Forget (Forget set, Cases 1–3, Def. 4, Alg. 1), DL(∂) | 1.5 |
| 3 | Cognitive state and semantic usability; conservative extension of [SCP26] (Prop. 4) | 2 |
| 4 | Compilation and Algorithm 2, including the refinements of [SCP26] (finite basis, admissible variants, checked Neglect) | 2.5 |
| 5 | Metatheory: Thm 1, Props. 1–7, separations; proofs in the appendix | 2 |
| 6 | From decisions to verifier models: T1 or validator soundness | 2.5 |
| 7 | Evaluation: RQ1 (vs [SCP26] semantics), RQ2 cues, RQ3 cost, RQ4 coverage | 3.5 |
| 8 | Related work (beyond [SCP26] §8), limitations, conclusion | 1.5 |

**Anticipated objections and answers.**

| Objection | Answer |
|---|---|
| "This is [SCP26] again." | The contributions table states deltas. Prop. 5 gives a formal separation, and G1 shows a verdict that [SCP26] cannot produce. Bank appears only as a compatibility check. |
| "[SCP26] says the derivation space is finite; why the coverage machinery?" | It is not finite (E3). The coverage policy and Props. 6–7 are what make negative decisions sound. |
| "DL is overkill; this is a Boolean test." | Agreed and proved (Thm 1). DL is the specification and explanation layer. Show its explanation value in the evaluation. |
| "Tamarin's matching can reintroduce forgotten values." | T1 or the validator (G2), plus host certification. State the trusted base. [SCP26] does not address this. |
| "The cognitive model is not validated." | Lapses and cues are analyst-supplied hypotheses. Grounding is cited; behaviour is not predicted. |
| "Enumeration is incomplete or exponential." | Props. 6–7, the conservative coverage policy, and measured caps and coverage (RQ4). |
| "Only toy examples." | The realistic benchmark of G3, with controls. |

---

## 9. Reproducing the current evidence

From `X-Men_2.0`, with JDK 21 and the Maven cache:

```sh
./mvnw -B -ntp -o '-Dtest=com.xmen.service.forget.dl.*Test' test
python3 scripts/dl-oracle/asp_crosscheck.py /path/to/Defeasible-Deontic-Logic-679c638 \
        target/classes:target/test-classes 400 200 1
```

From the workspace root, with Tamarin 1.12.0 and Maude 3.5.1:

```sh
for f in mut base; do
  tamarin-prover --with-maude=/path/to/maude --prove=Forget_Attack_Only_B1_Compromised \
    --bound=25 --quit-on-warning --derivcheck-timeout=20 experiments/bank/$f.spthy
done
for f in cert_ok cert_bug; do
  tamarin-prover --with-maude=/path/to/maude --prove=Certificate_No_Forgotten_Reuse \
    --bound=25 --quit-on-warning --derivcheck-timeout=20 experiments/bank/$f.spthy
done
```

The tool versions used were Temurin 21.0.12.1+1, Tamarin 1.12.0, Maude 3.5.1 and clingo 5.8.0. The review caches were under `/private/tmp/xmen-dl-tools`, which is not an artifact distribution.

To reproduce [SCP26]'s published Bank result (G5), use the model at the GitHub repository cited in [SCP26] (`rish-kar/X-Men_2.0`) with lemmas `Human_intends_Bank2_if_Bank2_OK` and `Password2_confidential`.

The detailed semantic contract (assumptions A1–A13, errata E1–E8, host discrepancies D1–D10) and the validation artifacts are in `X-Men_2.0/docs/forget-dl/`. Delivery patches are `forget-dl-reviewed-d8c6404.patch` (complete, against upstream `d8c6404`) and `forget-dl-review-fixes.patch` (incremental).
