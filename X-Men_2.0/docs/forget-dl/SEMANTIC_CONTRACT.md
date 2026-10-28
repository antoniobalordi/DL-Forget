# Forget-DL — semantic contract

The section numbers in the correspondence tables refer to the original Forget-DL
manuscript reviewed on 2026-10-02. The conference manuscript is in
[`paper/`](../../../paper/); the assumptions below also include the subsequent
re-acquisition, Neglect and cue-aware replacement corrections.
Code: `com.xmen.service.forget.dl` (pure Java; depends only on the Lombok-free message model).

## 1. Paper requirements implemented

| Ref | Requirement | Where |
|---|---|---|
| Def. 4, Alg. 2 l.2–4 | Two declarative lapse cases; K monotone; Forget only grows except by re-acquisition | `ForgetDlEngine.run` |
| Def. 5 | `Blocked(Hyp_π, Forget)` for Cases 1–3 | `ForgetBlocking` |
| Def. 6, §3.2 | Derivability under forgetting over normal derivations | `NormalDerivations` + compiler |
| §7.1 | DL(∂), ambiguity blocking, team defeat, four tags | `AmbiguityBlockingReasoner` |
| §7.1, §7.3 | `Known`, `Lapsed`, `d_π`, strict `use_π`, `ω_{π,h}` for every hypothesis | `ForgetDlCompiler` |
| Def. 7, §7.6 | `Rules(π)`, stable procedure identity, `Lapsed(Rule(r))`, `Lapsed(Proc(π))`, rule/procedure defeaters | `NormalDerivations`, compiler |
| Def. 8, §7.5 | Cue model; `ContextCue` facts for blocked h only; reinstater per (π, h, c), superior to the matching ω only | compiler |
| §7.6 | Typed procedural state; persistence; explicit relearning by set difference; cues never restore competences | engine |
| Alg. 2 l.5–6 | Message lapse used in an internal action → Neglect and return | engine |
| Alg. 2 l.13, Ex. 3 | Re-acquisition before candidate generation | engine (A6) |
| Alg. 2 l.14–19 | Candidate set T from declarative blockers only; Cartesian product per π | engine |
| Alg. 2 l.20–31 | One theory over T; preserve m2 if usable; else Replace each usable variant; else Skip | engine |
| §7.4 | Cognitive trace: support, overruled and standing blockers by kind; Π = ∅ distinguished | `ForgetDlResult` |

## 2. Implementation assumptions (paper silent or underspecified)

| ID | Assumption | Reason |
|---|---|---|
| A1 | Supported algebra: atoms, pairs, `senc` (and legacy `{m}_k`), `aenc(m, pk(sk))` with private key `sk`, and every other **public** function symbol as a free constructor (`sign`, `h`, `pk`, user functions). Rule labels: `pair`, `proj` (both projections), `senc`, `sdec`, `aenc`, `adec`, and the symbol name for other constructors; manuscript `unpair` is accepted as an alias for `proj`. Equational symbols (XOR, DH, bilinear) and destructor symbols inside terms → `UNSUPPORTED`. | Tamarin semantics for public functions; Def. 1 "other operators … handled in the standard way". |
| A2 | `format(atom) = "atom"`; otherwise top symbol/arity. | Def. 3 read literally (`f = m`) forbids the paper's own PW1→PW2 replacement. |
| A3 | Enumerate a finite basis of derivation trees in which no decomposition consumes the output of its inverse composition **and no term repeats on a root-to-leaf branch**. Report cyclic decryption dependencies separately. When exact whole-procedure lapses coexist with such cycles, negative decisions have truncated coverage and return `UNKNOWN_COVERAGE`; a witnessed positive derivation can still preserve the send. | The finite basis is not all manuscript-normal trees: an omitted cyclic tree can be a distinct, unforgotten procedure. See E3 and the counterexample below. |
| A4 | A blocked hypothesis without a label has `R = ∅`. | §3.3: labels exist to preserve type safety. |
| A5 | `m̄′ ≠ m̄` in `R_m̄`, and only uncued blocked hypotheses are replaced: a cued hypothesis is kept as itself. | Under Cases 2–3 the syntactic test admits m̄ itself. A cue gives access to its own message at this step, so the variant that keeps it and replaces the uncued hypotheses is the one the human can build; replacing it too would drop the variant entirely when its label is unique (review R3.1). T therefore depends on Forget and on the present cues. |
| A6 | `f` is re-acquired iff `f = m1`, or some derivation of `f` in the finite basis from K′ has `m1` as a hypothesis, no blocked hypothesis, and no currently forgotten inference rule or exact procedure; iterate to a fixpoint. Never undo a declarative lapse of the same transition or use a receipt that was not retained. Explicit procedural relearning is applied before this derived recovery test. The search is incomplete (`UNKNOWN_COVERAGE`) if it is truncated, or if `f` has cyclic derivations while an exact procedure is forgotten. | Ex. 3 and §4.3 ("re-received or derivable from the received message"); unrestricted re-derivation would erase retrieval lapses, and procedural forgetting must also constrain the derivation used to recover a message. Direct receipt re-acquires the message independently of old inferential competence. |
| A7 | Several lapse events at one transition are applied in sequence before the decision; explicit rule/procedure relearning is then applied to the resulting procedural state, including on Neglect transitions. | Host allows several lapse and recovery events per rule; Alg. 2 shows one ℓ. Declarative re-acquisition (A6) also runs on a Neglect transition. |
| A8 | Re-acquisition (A6) runs first, also at a Neglect transition. Then an internal action is neglected if a message occurring in it is inaccessible: blocked by the resulting Forget set and not cued. Lapses of earlier transitions count; procedural lapses do not (a check that needs a lost skill fails host certification). The host separately certifies surviving operational checks and outputs before export. | See E7. Testing only the lapses of the current transition kept checks whose reference was forgotten earlier (review R2.1). Testing after re-acquisition keeps a check on a re-received message; in the host a value recovered from m1 is bound by the receive pattern, so its explicit check is redundant with the match (R2.2). With no earlier lapse, no cue and Case 1, the test equals the manuscript's. |
| A9 | Host atoms are tilde-insensitive (`~pw1 ≡ pw1`). | Host `canonicalize` already does this. |
| A10 | Public constants (`$X`, `'c'`) occurring in the intended send are available for derivation; they are not written back to host state. | Honest agents can always use public names; host K extraction omits them. |
| A11 | Coverage policy: Preserve is justified by a witnessed surviving proof under truncated **target** enumeration; when no exact procedure is forgotten, the standard reasoner pipeline settles truncated target usability by restricted saturation (Theorem 16), recording the result separately from the enumerated DL tags. Without this fallback, a negative intended target under truncation or omitted cyclic procedure proofs → `UNKNOWN_COVERAGE`. An unsuccessful re-acquisition search with truncation **or omitted cyclic receipt proofs under an exact-procedure lapse** makes the successor state inconclusive, so all transition outcomes, including Preserve and No-Send, return `UNKNOWN_COVERAGE`. Replace under incomplete candidate/variant coverage is emitted and flagged `partial`; Skip only with exhaustive candidates and conclusively unusable targets. Even with truncated enumeration, T = {m2} is exhaustive if no uncued blocked knowledge term occurring in m2 has an eligible replacement. Cartesian products are generated lazily and both candidate count and combination work are bounded. | §7.7 completeness is relative to T and Π. An uncertain persistent state must not cause a false decision at a later transition. Search limits must not become an unfounded negative proof or exhaust memory before a cap is checked. |
| A12 | `m2[C]` = simultaneous, outermost syntactic substitution of every occurrence, including inside function arguments; a blocked hypothesis that does not occur in m2 yields no variant. | §7.7 "syntactic variant m2[C]". |
| A13 | Procedure identity hashes a structural term/tree encoding, independent of display labels and state; generated theory atoms and rule labels also use structural identities. | Pretty printing can identify different terms, such as an atom named `h(a)` and an actual function application. Procedure identifiers generated by the earlier patch must be regenerated. |

## 3. Paper issues (errata and remarks)

| ID | Location | Issue | Resolution |
|---|---|---|---|
| E1 | Earlier manuscript, Alg. 2 | The earlier contract reported `[[K]] := K ∪ {m}` dropping m1. | **Already corrected in the current supplied section**, which uses `K ∪ {m1}`. The regression remains useful; this is not a current manuscript defect. |
| E2 | Def. 3 | Atom format is the atom itself. | A2. |
| E3 | §3.2 | "finite space of normal derivations" is false under immediate-inverse normality alone: K = {{\|a\|}b, {\|b\|}a, a} has infinitely many normal derivations of a. | A3. Test `finitenessCounterexample`. |
| E4 | §7.3, §7.6 | `ω > d_π` (and `ω^rule > d_π`) is inert in DL(∂): an applicable defeater already blocks +∂ unless a superior supporter applies. What matters is the absence of `d_π > ω` and the presence of `ρ > ω`. | Kept for fidelity; mutant M5 is equivalent. |
| E5 | §8.2 | "single normal derivation π_S" holds only if srv/amt are not also obtainable from the received reminder ⟨S,H,srv,amt⟩. | Decisions unchanged; test `invoiceWithReceivedReminder`. |
| E6 | §7.7 l.17 | Syntactic `m̄′ ∉ Forget` admits m̄ under Cases 2–3. | A5. |
| E7 | Alg. 2 l.6 | Early return skips re-acquisition (l.13), so a Neglect transition passed on a stale Forget set. | A8: re-acquisition first, then the Neglect test. |
| E8 | Current Alg. 2, initial successor state | `Forget^{i+1} := Forget^i ∪ {m}` occurs before determining the lapse type; `m` is unbound for rule/procedure lapses. | Initialize from `Forget^i`; add the message in the message-lapse branch, including the just-received case. See the [framework definitions](../../../paper/sections/04-framework.tex). |
| — | p.26 | The "+∂ for both tags" typo reported in the supplied roadmap is **not** present in this revision. | none |

The current manuscript explicitly adds a newly received forgotten message to `Forget` while
leaving successor knowledge at the previous knowledge set. The old patch/test expectation that
this case leaves `Forget` unchanged has been corrected. A procedural lapse leaves `Forget`
unchanged.

**Cycle counterexample (E3).** Let `K = {senc(a,b), senc(b,a), a}`, target `a`, and forget only
the whole direct procedure `Initial[a]`. The distinct tree
`sdec(senc(a,b), sdec(senc(b,a), Initial[a]))` has no immediately inverted constructor/destructor
pair and can survive the exact procedure lapse. A nonrepeating basis omits it, so a negative
result cannot be called manuscript-complete. The engine now reports this uncertainty.

Cycles can also change receipt provenance in A6. With old knowledge `{a, h(a)}`, forgotten
`h(a)`, and receipt `senc(a,a)`, the normal tree `h(sdec(Initial[senc(a,a)], Initial[a]))` uses
the receipt but lies outside the finite basis. A6 is defined on the finite basis, so it is no
witness. Without forgotten procedures this loses nothing the receipt contributes: shortening such
a witness gives a surviving derivation of `f` in the finite basis that, having no basis witness,
does not use `m1`. With a forgotten procedure the shortened tree may be the forgotten one, so the
search is reported incomplete, as for targets (A3).

**Neglect boundary (A8).** Re-acquisition runs first; the core then neglects every internal action
in which an inaccessible message occurs, without running the usual send-decision branch at that
transition. The host then certifies surviving operational operands and output payloads under the
successor knowledge, lapses and current cues. A co-send that cannot be certified is rejected with
HTTP 400, as is a remaining check whose operand needs a lost skill. Supply
proof/instrumentation facts such as `PasswordAttempt` through `Witness-Actions` so they are not
mistaken for an internal check. This guard and tested fixtures do not establish a universal
theorem that Tamarin matching cannot reintroduce every forgotten value.

**Host scope.** DL requests use request-local parsing and in-memory ZIP/trace rendering. All
human transitions, including later unannotated sends, are evaluated along a linear, acyclic
`State` chain per human; terminal alternatives are supported. Ordering also respects
unambiguous directed four-argument channel dependencies. Native `Out`/`In` remain an
adversarial network: the host does not infer an exclusive sender or rewrite the receive.
Directed receive rebinding supports atomic messages and rejects compound or already-bound
patterns. Cue declarations/associations persist; presentation is transition-local. Unknown
coverage, unsupported inputs and unsafe operational uses fail the request instead of emitting
a successful empty ZIP. Supported `h`/`sign` arities follow the model's declared builtins.

## 4. Host discrepancies (verified by inspection at d8c6404; legacy path left unchanged)

| ID | Component | Discrepancy | DL mode |
|---|---|---|---|
| D1 | `DerivationServiceImpl.deriveAllRecursive` | Decryption node lacks the ciphertext premise → ciphertext never a hypothesis. | Fixed in `NormalDerivations`. |
| D2 | same | Decryption only of legacy `Encrypt`, not `senc`/`aenc` terms; no inverse key for `aenc`; destructors only on members of K. | Fixed. |
| D3 | `DerivationConfig` | User-defined functions never composed → Π = ∅ for `k(nH,nB)` → non-paper textual fallback using only the first replacement. | Public functions composable (A1). |
| D4 | `ForgetDerivationChecker.getAllDerivations` | Exceptions → empty set; truncation silent. | Explicit `Coverage`, `Status`. |
| D5 | `ReplacementComputer`/`applyVariantToRule` | No substitution inside function arguments; every variant rewritten with `iterator().next()` → alternatives lost or duplicated. | Structural substitution inside arguments; one branch per distinct usable target; guarded atomic directed-receive rebinding. Native `Out`/`In` receivers are retained. |
| D6 | `ReplacementComputer`, `TermFormat` | Mode-dependent `isBlocked` instead of syntactic `∉ Forget`; unknown label accepts any; name-heuristic formats. | A2, A4, Alg. 2 l.17. |
| D7 | `ForgetContext.updateForTransition`, `resolveForgetMessage` | Forget-just-received removes m1 from Forget; re-acquisition only by equality; unknown forgotten message fabricated as an atom. | Paper semantics; unknown → `INVALID_INPUT`. |
| D8 | `ForgetMutationStrategy` | Neglect and send analysis both run; Alg. 1/2 branch. | A8 accessibility test after re-acquisition, followed by supplemental host certification of surviving operational uses. |
| D9 | `ForgetMutationStrategy` | Only rules with a `Forget(x)` annotation are evaluated; later sends under a persisting lapse are not re-checked (§8.1, transition j). | Implemented for the supported acyclic State/channel ordering above; later unannotated sends are re-evaluated. |
| D10 | `BlockingChecker` Case 3 | Goal-directed, depth-bounded; decrypts `Encrypt` with the encryption key; only direct members. | Saturation in `ForgetBlocking`. |

## 5. External oracle finding

`gvdgdo/Defeasible-Deontic-Logic@679c638`, `Basic/defeasible-ab.asp`: `discardedOrNotSuperior(T,X)`
re-quantifies the attacker `S`, so `overruled/2` no longer requires one attacker unanswered by every
team member. Under team defeat (e.g. two lapses with two matching cues, Lemma 1) the encoding derives
both `defeasible(X)` and `refuted(X)`. `scripts/dl-oracle/asp_crosscheck.py` reports both the upstream
run and a run with `S` bound; only the patched run is used as the oracle.
