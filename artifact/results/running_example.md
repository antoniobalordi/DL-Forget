# Running example: Bank Login, transition receiving c and sending m3
m3 = senc(<H,pw1>,k(nH,nB))
c  = aenc(<nH,nB>,pk(ltkH))

## Normal derivations of m3 from K^{i+1} (2)
- pi_a24259029aef21a7 (pi_a, nH from memory) Hyp=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] Rules=[adec, k, pair, proj, senc]
    senc ⇒ senc(<H,pw1>,k(nH,nB))
      pair ⇒ <H,pw1>
        Initial ⇒ H
        Initial ⇒ pw1
      k ⇒ k(nH,nB)
        Initial ⇒ nH
        proj ⇒ nB
          adec ⇒ <nH,nB>
            Initial ⇒ aenc(<nH,nB>,pk(ltkH))
            Initial ⇒ ltkH
- pi_158577907882ee20 (pi_b, nH from c) Hyp=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] Rules=[adec, k, pair, proj, senc]
    senc ⇒ senc(<H,pw1>,k(nH,nB))
      pair ⇒ <H,pw1>
        Initial ⇒ H
        Initial ⇒ pw1
      k ⇒ k(nH,nB)
        proj ⇒ nH
          adec ⇒ <nH,nB>
            Initial ⇒ aenc(<nH,nB>,pk(ltkH))
            Initial ⇒ ltkH
        proj ⇒ nB
          adec ⇒ <nH,nB>
            Initial ⇒ aenc(<nH,nB>,pk(ltkH))
            Initial ⇒ ltkH

## Decisions
| id | scenario | Forget-DL (X-Men 2.0) | variants | |T| | |D| facts/rules/sup | Alg. 1 (SCP26 semantics) | no team defeat | ambiguity propagation |
|---|---|---|---|---|---|---|---|---|
| S0 | no lapse | PRESERVE | [] | 1 | 9/25/21 | PRESERVE | PRESERVE | PRESERVE |
| S1 | Forget(pw1), no cue | REPLACE | [senc(<H,pw2>,k(nH,nB))] | 2 | 10/52/44 | REPLACE [senc(<H,pw2>,k(nH,nB))] | REPLACE | REPLACE |
| S2 | Forget(pw1), hint present | PRESERVE | [] | 1 | 11/27/23 | REPLACE [senc(<H,pw2>,k(nH,nB))] | PRESERVE | PRESERVE |
| S3 | Forget(pw1) + ForgetRule(adec) | SKIP | [] | 2 | 11/52/44 | REPLACE [senc(<H,pw2>,k(nH,nB))] | SKIP | SKIP |
| S4 | ForgetRule(adec) only | SKIP | [] | 1 | 10/25/21 | PRESERVE | SKIP | SKIP |
| S5 | ForgetProcedure(pi_b) only | PRESERVE | [] | 1 | 10/25/21 | PRESERVE | PRESERVE | PRESERVE |
| S6 | ForgetProcedure(pi_a)+ForgetProcedure(pi_b) | SKIP | [] | 1 | 11/25/21 | PRESERVE | SKIP | SKIP |
| S7 | Forget(pw1)+Forget(nH), no cue | REPLACE | [senc(<H,pw2>,k(nH,nB))] | 2 | 11/52/44 | REPLACE [senc(<H,pw2>,k(nH,nB))] | REPLACE | REPLACE |
| S8 | Forget(pw1)+Forget(nH), hint present | PRESERVE | [] | 1 | 12/27/23 | REPLACE [senc(<H,pw2>,k(nH,nB))] | PRESERVE | PRESERVE |
| S9 | Forget(pw1)+Forget(H), hint only | SKIP | [] | 1 | 12/29/25 | SKIP | SKIP | SKIP |
| S10 | Forget(pw1)+Forget(H), hint and autofill | PRESERVE | [] | 1 | 13/29/25 | SKIP | SKIP | PRESERVE |

## Cognitive trace S1
```
status=OK decision=REPLACE
intended senc(<H,pw1>,k(nH,nB)): -∂Usable
  pi_a24259029aef21a7 -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_a24259029aef21a7]
    standing DECLARATIVE pw1 [w:pi_a24259029aef21a7:A3:pw1]
  pi_158577907882ee20 -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_158577907882ee20]
    standing DECLARATIVE pw1 [w:pi_158577907882ee20:A3:pw1]
variant  senc(<H,pw2>,k(nH,nB)): +∂Usable
  pi_a51c0d4deae32b8d +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw2] blocked=[] rules=[adec, k, pair, proj, senc] support=[d:pi_a51c0d4deae32b8d]
  pi_810b3da8bd70228a +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw2] blocked=[] rules=[adec, k, pair, proj, senc] support=[d:pi_810b3da8bd70228a]
```

## Cognitive trace S2
```
status=OK decision=PRESERVE
intended senc(<H,pw1>,k(nH,nB)): +∂Usable
  pi_a24259029aef21a7 +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_a24259029aef21a7, rho:pi_a24259029aef21a7:A3:pw1:4:hint]
    overruled DECLARATIVE pw1 [w:pi_a24259029aef21a7:A3:pw1] by [rho:pi_a24259029aef21a7:A3:pw1:4:hint]
  pi_158577907882ee20 +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_158577907882ee20, rho:pi_158577907882ee20:A3:pw1:4:hint]
    overruled DECLARATIVE pw1 [w:pi_158577907882ee20:A3:pw1] by [rho:pi_158577907882ee20:A3:pw1:4:hint]
```

## Cognitive trace S3
```
status=OK decision=SKIP
intended senc(<H,pw1>,k(nH,nB)): -∂Usable
  pi_a24259029aef21a7 -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_a24259029aef21a7]
    standing DECLARATIVE pw1 [w:pi_a24259029aef21a7:A3:pw1]
    standing RULE adec [wr:pi_a24259029aef21a7:adec]
  pi_158577907882ee20 -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_158577907882ee20]
    standing DECLARATIVE pw1 [w:pi_158577907882ee20:A3:pw1]
    standing RULE adec [wr:pi_158577907882ee20:adec]
variant  senc(<H,pw2>,k(nH,nB)): -∂Usable
  pi_a51c0d4deae32b8d -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw2] blocked=[] rules=[adec, k, pair, proj, senc] support=[d:pi_a51c0d4deae32b8d]
    standing RULE adec [wr:pi_a51c0d4deae32b8d:adec]
  pi_810b3da8bd70228a -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw2] blocked=[] rules=[adec, k, pair, proj, senc] support=[d:pi_810b3da8bd70228a]
    standing RULE adec [wr:pi_810b3da8bd70228a:adec]
```

## Cognitive trace S8
```
status=OK decision=PRESERVE
intended senc(<H,pw1>,k(nH,nB)): +∂Usable
  pi_a24259029aef21a7 -∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] blocked=[nH, pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_a24259029aef21a7, rho:pi_a24259029aef21a7:A3:pw1:4:hint]
    standing DECLARATIVE nH [w:pi_a24259029aef21a7:A2:nH]
    overruled DECLARATIVE pw1 [w:pi_a24259029aef21a7:A3:pw1] by [rho:pi_a24259029aef21a7:A3:pw1:4:hint]
  pi_158577907882ee20 +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] blocked=[pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_158577907882ee20, rho:pi_158577907882ee20:A3:pw1:4:hint]
    overruled DECLARATIVE pw1 [w:pi_158577907882ee20:A3:pw1] by [rho:pi_158577907882ee20:A3:pw1:4:hint]
```

## Cognitive trace S10
```
status=OK decision=PRESERVE
intended senc(<H,pw1>,k(nH,nB)): +∂Usable
  pi_a24259029aef21a7 +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, nH, pw1] blocked=[H, pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_a24259029aef21a7, rho:pi_a24259029aef21a7:A1:H:8:autofill, rho:pi_a24259029aef21a7:A3:pw1:4:hint]
    overruled DECLARATIVE H [w:pi_a24259029aef21a7:A1:H] by [rho:pi_a24259029aef21a7:A1:H:8:autofill]
    overruled DECLARATIVE pw1 [w:pi_a24259029aef21a7:A3:pw1] by [rho:pi_a24259029aef21a7:A3:pw1:4:hint]
  pi_158577907882ee20 +∂Via hypotheses=[H, aenc(<nH,nB>,pk(ltkH)), ltkH, pw1] blocked=[H, pw1] rules=[adec, k, pair, proj, senc] support=[d:pi_158577907882ee20, rho:pi_158577907882ee20:A1:H:8:autofill, rho:pi_158577907882ee20:A3:pw1:4:hint]
    overruled DECLARATIVE H [w:pi_158577907882ee20:A1:H] by [rho:pi_158577907882ee20:A1:H:8:autofill]
    overruled DECLARATIVE pw1 [w:pi_158577907882ee20:A3:pw1] by [rho:pi_158577907882ee20:A3:pw1:4:hint]
```

## Compiled theory S2 (canonical text)
```
atom ContextCue(4:hint,A3:pw1)
atom Known(A1:H)
atom Known(A2:B1)
atom Known(A2:B2)
atom Known(A2:nH)
atom Known(A3:pw1)
atom Known(A3:pw2)
atom Known(A4:ltkH)
atom Known(A4:pkB1)
atom Known(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH)
atom Lapsed(A1:H)
atom Lapsed(A2:nH)
atom Lapsed(A3:pw1)
atom Lapsed(A4:ltkH)
atom Lapsed(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH)
atom LapsedProc(pi_158577907882ee20)
atom LapsedProc(pi_a24259029aef21a7)
atom LapsedRule(adec)
atom LapsedRule(k)
atom LapsedRule(pair)
atom LapsedRule(proj)
atom LapsedRule(senc)
atom Usable(F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
atom Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
atom Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
fact ContextCue(4:hint,A3:pw1)
fact Known(A1:H)
fact Known(A2:B1)
fact Known(A2:B2)
fact Known(A2:nH)
fact Known(A3:pw1)
fact Known(A3:pw2)
fact Known(A4:ltkH)
fact Known(A4:pkB1)
fact Known(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH)
fact Lapsed(A3:pw1)
rule d:pi_158577907882ee20: Known(A1:H), Known(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH), Known(A4:ltkH), Known(A3:pw1) => Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule d:pi_a24259029aef21a7: Known(A1:H), Known(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH), Known(A4:ltkH), Known(A2:nH), Known(A3:pw1) => Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule rho:pi_158577907882ee20:A3:pw1:4:hint: ContextCue(4:hint,A3:pw1) => Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule rho:pi_a24259029aef21a7:A3:pw1:4:hint: ContextCue(4:hint,A3:pw1) => Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule use:pi_158577907882ee20: Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB) -> Usable(F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule use:pi_a24259029aef21a7: Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB) -> Usable(F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_158577907882ee20:A1:H: Lapsed(A1:H) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_158577907882ee20:A3:pw1: Lapsed(A3:pw1) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_158577907882ee20:A4:ltkH: Lapsed(A4:ltkH) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_158577907882ee20:F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH: Lapsed(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_a24259029aef21a7:A1:H: Lapsed(A1:H) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_a24259029aef21a7:A2:nH: Lapsed(A2:nH) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_a24259029aef21a7:A3:pw1: Lapsed(A3:pw1) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_a24259029aef21a7:A4:ltkH: Lapsed(A4:ltkH) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule w:pi_a24259029aef21a7:F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH: Lapsed(F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wp:pi_158577907882ee20: LapsedProc(pi_158577907882ee20) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wp:pi_a24259029aef21a7: LapsedProc(pi_a24259029aef21a7) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_158577907882ee20:adec: LapsedRule(adec) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_158577907882ee20:k: LapsedRule(k) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_158577907882ee20:pair: LapsedRule(pair) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_158577907882ee20:proj: LapsedRule(proj) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_158577907882ee20:senc: LapsedRule(senc) ~> ¬Via(pi_158577907882ee20,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_a24259029aef21a7:adec: LapsedRule(adec) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_a24259029aef21a7:k: LapsedRule(k) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_a24259029aef21a7:pair: LapsedRule(pair) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_a24259029aef21a7:proj: LapsedRule(proj) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
rule wr:pi_a24259029aef21a7:senc: LapsedRule(senc) ~> ¬Via(pi_a24259029aef21a7,F4:senc2:PA1:HA3:pw1F1:k2:A2:nHA2:nB)
sup rho:pi_158577907882ee20:A3:pw1:4:hint > w:pi_158577907882ee20:A3:pw1
sup rho:pi_a24259029aef21a7:A3:pw1:4:hint > w:pi_a24259029aef21a7:A3:pw1
sup w:pi_158577907882ee20:A1:H > d:pi_158577907882ee20
sup w:pi_158577907882ee20:A3:pw1 > d:pi_158577907882ee20
sup w:pi_158577907882ee20:A4:ltkH > d:pi_158577907882ee20
sup w:pi_158577907882ee20:F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH > d:pi_158577907882ee20
sup w:pi_a24259029aef21a7:A1:H > d:pi_a24259029aef21a7
sup w:pi_a24259029aef21a7:A2:nH > d:pi_a24259029aef21a7
sup w:pi_a24259029aef21a7:A3:pw1 > d:pi_a24259029aef21a7
sup w:pi_a24259029aef21a7:A4:ltkH > d:pi_a24259029aef21a7
sup w:pi_a24259029aef21a7:F4:aenc2:PA2:nHA2:nBF2:pk1:A4:ltkH > d:pi_a24259029aef21a7
sup wp:pi_158577907882ee20 > d:pi_158577907882ee20
sup wp:pi_a24259029aef21a7 > d:pi_a24259029aef21a7
sup wr:pi_158577907882ee20:adec > d:pi_158577907882ee20
sup wr:pi_158577907882ee20:k > d:pi_158577907882ee20
sup wr:pi_158577907882ee20:pair > d:pi_158577907882ee20
sup wr:pi_158577907882ee20:proj > d:pi_158577907882ee20
sup wr:pi_158577907882ee20:senc > d:pi_158577907882ee20
sup wr:pi_a24259029aef21a7:adec > d:pi_a24259029aef21a7
sup wr:pi_a24259029aef21a7:k > d:pi_a24259029aef21a7
sup wr:pi_a24259029aef21a7:pair > d:pi_a24259029aef21a7
sup wr:pi_a24259029aef21a7:proj > d:pi_a24259029aef21a7
sup wr:pi_a24259029aef21a7:senc > d:pi_a24259029aef21a7
```
