# Preliminary adversarial-cue model on the running example
T = {m3, m3[pw1->pw2]}; Int = {(banner, pw1)}; Assoc = {(hint, pw1)}

| id | scenario | Usable(m3) AB | decision AB | Usable(m3) AP (+∫?) | decision AP |
|---|---|---|---|---|---|
| A0 | no lapse, no cue | +∂ | PRESERVE | +∂ (+∫) | PRESERVE |
| A1 | no lapse, adversarial banner | -∂ | REPLACE [senc(<H,pw2>,k(nH,nB))] | -∂ (+∫) | SKIP |
| A2 | no lapse, banner + hint, hint > banner | +∂ | PRESERVE | +∂ (+∫) | PRESERVE |
| A3 | no lapse, banner + hint, no order | -∂ | REPLACE [senc(<H,pw2>,k(nH,nB))] | -∂ (+∫) | SKIP |
| A4 | Forget(pw1), hint, no banner | +∂ | PRESERVE | +∂ (+∫) | PRESERVE |
| A5 | Forget(pw1), banner + hint, no order | -∂ | REPLACE [senc(<H,pw2>,k(nH,nB))] | -∂ (+∫) | SKIP |
| A6 | Forget(pw1), banner + hint, banner > hint | -∂ | REPLACE [senc(<H,pw2>,k(nH,nB))] | -∂ (-∫) | REPLACE [senc(<H,pw2>,k(nH,nB))] |
| A7 | Forget(pw1), banner + hint, hint > banner | +∂ | PRESERVE | +∂ (+∫) | PRESERVE |

Without the pairs ω > d (inert for ∂):
A5: decision AB REPLACE [senc(<H,pw2>,k(nH,nB))], decision AP SKIP
A6: decision AB REPLACE [senc(<H,pw2>,k(nH,nB))], decision AP SKIP

Decision layer on the base fragment: in-logic decision = Alg. 2 decision on 3000/3000 random theories (targets as candidates)
