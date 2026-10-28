# `service/forget/dl` — Forget under Defeasible Logic (paper §7, Algorithm 2)

Pure-Java core (no Spring, no Lombok). Entry points: `ForgetDlEngine.run(TransitionInput)` and,
for the host, `ForgetDlBridge.decideTransition(TransitionInput)` (`decide(...)` remains compatible).

| File | Role |
|---|---|
| `Terms` | message algebra helpers: canonical form, subterms, format, substitution, cipher views |
| `NormalDerivations` | Π_m with explicit coverage; Hyp_π, Rules(π), stable procedure ids |
| `UsabilityDecider` | exact restricted saturation when no whole procedure is forgotten |
| `ForgetBlocking` | Blocked(Hyp, Forget), Cases 1–3 |
| `DefeasibleTheory`, `DefeasibleReasoner`, `AmbiguityBlockingReasoner` | DL(∂) theories and proof tags |
| `ForgetDlCompiler` | compile(H, i+1, T, K): facts, d_π, use_π, defeaters, reinstaters, superiority |
| `ForgetDlModel` | typed state: K, Forget, PForget, cue model, lapse events |
| `ForgetDlEngine` | Algorithm 2: state update, re-acquisition, candidates, decision with coverage |
| `ForgetDlResult` | decision, candidates, cognitive trace, `explain()` |
| `ForgetDlBridge` | host representation (tildes, public constants, labels) |

Enable in the host with the request header `Forget-Semantics: DL` (default `ALGORITHM1`).
Request syntax, semantics, validation and supported host models: `docs/forget-dl/`.

Procedure IDs use structural term identities; regenerate IDs obtained from the original patch.
Negative decisions with potentially surviving cyclic procedures return `UNKNOWN_COVERAGE`.

The standard engine settles truncated target searches with restricted saturation when no exact
procedure is forgotten and re-acquisition is conclusive. `usabilityFallback()` records these
Boolean results separately; candidate/trace tags still describe the enumerated DL theory.
`Skip` requires exhaustive candidates, which can also be established when no eligible
substitution changes the intended target. Injected alternative reasoners retain enumeration.
