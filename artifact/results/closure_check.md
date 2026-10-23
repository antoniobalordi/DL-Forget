## Closure decider vs production pipeline (no exact-procedure lapses)
targets=30000 agree=30000 disagree=0 (usable=14021) skipped(truncated)=0
E3 cyclic knowledge, target b usable: true

## F4 family beyond the enumeration cap: engine vs closure decider
| d | variant | engine decision | engine ms | closure usable(m2) | closure ms |
|---|---|---|---|---|---|
| 8 | F4+ | PRESERVE | 12,73 | true | 0,025 |
| 10 | F4+ | UNKNOWN_COVERAGE | 31,25 | true | 0,026 |
| 12 | F4+ | UNKNOWN_COVERAGE | 37,06 | true | 0,013 |
| 16 | F4+ | UNKNOWN_COVERAGE | 53,20 | true | 0,016 |
| 20 | F4+ | UNKNOWN_COVERAGE | 67,22 | true | 0,020 |
| 32 | F4+ | UNKNOWN_COVERAGE | 131,46 | true | 0,029 |
| 64 | F4+ | UNKNOWN_COVERAGE | 380,48 | true | 0,066 |
| 8 | F4- | SKIP | 10,59 | false | 0,006 |
| 10 | F4- | UNKNOWN_COVERAGE | 29,81 | false | 0,010 |
| 12 | F4- | UNKNOWN_COVERAGE | 36,63 | false | 0,011 |
| 16 | F4- | UNKNOWN_COVERAGE | 51,79 | false | 0,015 |
| 20 | F4- | UNKNOWN_COVERAGE | 85,99 | false | 0,023 |
| 32 | F4- | UNKNOWN_COVERAGE | 125,61 | false | 0,026 |
| 64 | F4- | UNKNOWN_COVERAGE | 365,35 | false | 0,043 |
