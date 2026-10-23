## Configuration CLASSICAL (runs=5000, valid=5000)
| Forget-DL / Alg. 1 | count |
|---|---|
| PRESERVE / PRESERVE | 2184 |
| REPLACE / REPLACE | 423 |
| SKIP / REPLACE | 133 |
| SKIP / SKIP | 2260 |

Differences classified:
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE1_WEAK]: 14
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE2_PAIRING]: 97
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE3_FULL_DY]: 86
- Alg.1 variant has no derivation free of blocked hypotheses; DL emits a subset [CASE1_WEAK]: 2
- Alg.1 variant has no derivation free of blocked hypotheses; DL emits a subset [CASE2_PAIRING]: 7
- Alg.1 variant has no derivation free of blocked hypotheses; DL emits a subset [CASE3_FULL_DY]: 14
Prop. 8 check (Preserve test identical; Replace set = proper usable Alg. 1 variants): checked=5000 failures=0
Engine decision differs with no-team-defeat reasoner: 0
Engine decision differs with ambiguity-propagating reasoner: 0

## Configuration FULL (runs=5000, valid=5000)
| Forget-DL / Alg. 1 | count |
|---|---|
| PRESERVE / PRESERVE | 1652 |
| PRESERVE / REPLACE | 147 |
| PRESERVE / SKIP | 203 |
| REPLACE / PRESERVE | 25 |
| REPLACE / REPLACE | 259 |
| REPLACE / SKIP | 3 |
| SKIP / PRESERVE | 507 |
| SKIP / REPLACE | 150 |
| SKIP / SKIP | 2054 |

Differences classified:
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE1_WEAK]: 10
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE2_PAIRING]: 64
- Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3) [CASE3_FULL_DY]: 57
- Alg.1 variant blocked by a lapse (procedural or declarative); DL emits a subset [CASE1_WEAK]: 20
- Alg.1 variant blocked by a lapse (procedural or declarative); DL emits a subset [CASE2_PAIRING]: 25
- Alg.1 variant blocked by a lapse (procedural or declarative); DL emits a subset [CASE3_FULL_DY]: 32
- cue reinstates the intended send (DL Preserve, Alg.1 REPLACE) [CASE1_WEAK]: 37
- cue reinstates the intended send (DL Preserve, Alg.1 REPLACE) [CASE2_PAIRING]: 61
- cue reinstates the intended send (DL Preserve, Alg.1 REPLACE) [CASE3_FULL_DY]: 49
- cue reinstates the intended send (DL Preserve, Alg.1 SKIP) [CASE1_WEAK]: 67
- cue reinstates the intended send (DL Preserve, Alg.1 SKIP) [CASE2_PAIRING]: 66
- cue reinstates the intended send (DL Preserve, Alg.1 SKIP) [CASE3_FULL_DY]: 70
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 REPLACE 1) [CASE1_WEAK]: 2
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 REPLACE 1) [CASE2_PAIRING]: 1
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 REPLACE 1) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 REPLACE 2) [CASE2_PAIRING]: 1
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 REPLACE 2) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 1 vs Alg.1 SKIP 0) [CASE2_PAIRING]: 2
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 REPLACE 1) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 REPLACE 2) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 REPLACE 4) [CASE1_WEAK]: 1
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 REPLACE 4) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 REPLACE 6) [CASE3_FULL_DY]: 1
- cue-dependent variant set (DL REPLACE 2 vs Alg.1 SKIP 0) [CASE1_WEAK]: 1
- procedural lapse blocks every derivation of the intended send (DL REPLACE, Alg.1 PRESERVE) [CASE1_WEAK]: 13
- procedural lapse blocks every derivation of the intended send (DL REPLACE, Alg.1 PRESERVE) [CASE2_PAIRING]: 4
- procedural lapse blocks every derivation of the intended send (DL REPLACE, Alg.1 PRESERVE) [CASE3_FULL_DY]: 8
- procedural lapse blocks every derivation of the intended send (DL SKIP, Alg.1 PRESERVE) [CASE1_WEAK]: 201
- procedural lapse blocks every derivation of the intended send (DL SKIP, Alg.1 PRESERVE) [CASE2_PAIRING]: 156
- procedural lapse blocks every derivation of the intended send (DL SKIP, Alg.1 PRESERVE) [CASE3_FULL_DY]: 150
Engine decision differs with no-team-defeat reasoner: 40
Engine decision differs with ambiguity-propagating reasoner: 0

