#!/usr/bin/env python3
"""Differential check of a DL(delta) reasoner (ambiguity propagation, team defeat, support) against
Governatori's ASP encoding gvdgdo/Defeasible-Deontic-Logic@679c638, Basic/defeasible-ap.asp.

Usage:
  ap_crosscheck.py emit N SEED > random.dl
      print N random theories (same generator as asp_crosscheck.gen_random) in the harness format
      {theory ID / declare A / fact [-]A / rule LABEL S|D|F [-]HEAD [-]BODY... / sup T S / end}
  ap_crosscheck.py check <ddl-repo-dir> THEORIES.dl TAGS.txt [--support]
      THEORIES.dl: theories in the harness format (random, compiled, Sect. 9 extended)
      TAGS.txt:    the reasoner's output, one line "ID LITERAL TAGS", TAGS a concatenation of
                   +D -D +d -d (delta) and, with --support, +S -S (support)
      Compares delta tags (and support tags with --support) on every literal of every theory
      against the upstream encoding and against a locally patched copy (PATCHES below).
Exit code 0 iff the patched encoding agrees on every compared tag.

The upstream file departs from the DL(delta) conditions of [Antoniou et al., AAAI 2000] as stated in
Sect. B of the paper. PATCHES makes four local changes; each upstream fragment is asserted verbatim.
  F1 -delta: only strict/defeasible rules of q count, and a supported attacker refutes q only if no
     applicable strict/defeasible rule for q is superior to it (team defeat); upstream ignores >.
  F2 -support: only strict/defeasible rules of q count; the clause for an applicable superior
     attacker is malformed upstream (rule(X,Y), applicable/1) and never fires.
  F3 support: no -Delta~q / +Delta~q conditions, as in the definition of support in Sect. B.
     This aligns a variant; it is not a bug fix. It never matters on compiled theories.
  F4 +delta: a rule that overrules an attacker must be strict/defeasible with a +delta body;
     upstream accepts any supported rule, including defeaters.
"""
import os, random, shutil, subprocess, sys, tempfile
import clingo

PATCHES = [
 ("""supported(X) :- 
    definiteRefuted(X1) : opposes(X,X1);
    rule(R,X), not defeater(R,X), support(R,X), """,
  """supported(X) :- 
    rule(R,X), not defeater(R,X), support(R,X), """),                                        # F3
 ("""unsupported(X) :- opposes(X,Y), definite(Y).
""", ""),                                                                                     # F3
 ("""unsupported(X) :-literal(X), not definite(X), 
    msigma(R,X) : rule(R,X).""",
  """unsupported(X) :-literal(X), not definite(X), 
    msigma(R,X) : rule(R,X), not defeater(R,X)."""),                                          # F2
 ("""msigma(R,X) :- rule(R,X), rule(X,Y), opposes(X,Y), 
    applicable(Y), superior(S,R).""",
  """msigma(R,X) :- rule(R,X), opposes(X,Y), rule(S,Y),
    applicable(S,Y), superior(S,R)."""),                                                      # F2
 ("""defeated(R,X) :- opposes(X,X2), rule(R,X),
    rule(R2,X2), support(R2,X2), superior(R2,R).""",
  """defeated(R,X) :- opposes(X,X2), rule(R,X),
    rule(R2,X2), not defeater(R2,X2), applicable(R2,X2), superior(R2,R)."""),                 # F4
 ("""refuted(X) :- literal(X), not definite(X), 
    refutedOrAttacked(R,X) : rule(R,X).""",
  """refuted(X) :- literal(X), not definite(X), 
    refutedOrAttacked(R,X) : rule(R,X), not defeater(R,X)."""),                               # F1
 ("""attacked(R,X) :- rule(R,X), opposes(X,X2),
    rule(R2,X2), support(R2,X2).""",
  """attacked(R,X) :- rule(R,X), opposes(X,X2),
    rule(R2,X2), support(R2,X2),
    discardedOrNotSuperior(T,X,R2) : rule(T,X), not defeater(T,X).

discardedOrNotSuperior(T,X,S) :- rule(T,X), opposes(X,Y), rule(S,Y), discarded(T,X).
discardedOrNotSuperior(T,X,S) :- rule(T,X), opposes(X,Y), rule(S,Y), not superior(T,S)."""),  # F1
]

def comp(l): return l[1:] if l.startswith("-") else "-" + l

def gen_random(rng, idx):  # identical to asp_crosscheck.gen_random
    n = rng.randint(3, 7)
    atoms = [f"p{i}" for i in range(n)]
    lines, rules, fact_lits = [f"theory rnd{idx}"], [], set()
    for a in atoms:
        lines.append(f"declare {a}")
    for a in atoms[: rng.randint(0, 2)]:
        lit = a if rng.random() < .8 else "-" + a
        fact_lits.add(lit); lines.append(f"fact {lit}")
    strict_heads = set(fact_lits)
    for r in range(rng.randint(2, 9)):
        h = rng.randint(1, n - 1)
        head = atoms[h] if rng.random() < .6 else "-" + atoms[h]
        body = [(atoms[b] if rng.random() < .7 else "-" + atoms[b])
                for b in rng.sample(range(h), rng.randint(0, min(2, h)))]
        kind = rng.choice("SDDDF")
        if kind == "S" and comp(head) in strict_heads:
            kind = "D"
        if kind == "S":
            strict_heads.add(head)
        rules.append((f"r{r}", kind, head, body))
    nondefeater_heads = {h for (_, k, h, _) in rules if k != "F"}
    sups, rank = [], {l: rng.random() for (l, _, _, _) in rules}
    for (l1, k1, h1, _) in rules:
        for (l2, k2, h2, _) in rules:
            if h1 == comp(h2) and l1 < l2 and rng.random() < .5:
                t, s = (l1, l2) if rank[l1] < rank[l2] else (l2, l1)
                kt, ht = next((k, h) for (l, k, h, _) in rules if l == t)
                if kt == "F" and ht in nondefeater_heads:
                    continue
                sups.append((t, s))
    lines += [" ".join(["rule", l, k, h] + b) for (l, k, h, b) in rules]
    lines += [f"sup {t} {s}" for t, s in sups]
    return "\n".join(lines + ["end"]) + "\n"

def parse(text):
    theories, cur = [], None
    for line in text.splitlines():
        t = line.split()
        if not t: continue
        if t[0] == "theory": cur = {"id": t[1], "atoms": [], "facts": [], "rules": [], "sup": []}
        elif t[0] == "declare": cur["atoms"].append(t[1])
        elif t[0] == "fact": cur["facts"].append(t[1])
        elif t[0] == "rule": cur["rules"].append((t[1], t[2], t[3], t[4:]))
        elif t[0] == "sup": cur["sup"].append((t[1], t[2]))
        elif t[0] == "end": theories.append(cur)
    return theories

def prepare(ddl, patched):
    d = tempfile.mkdtemp(prefix="ddl_ap_")
    shutil.copytree(os.path.join(ddl, "Basic"), os.path.join(d, "Basic"))
    shutil.copy(os.path.join(ddl, "language.asp"), d)
    f = os.path.join(d, "Basic", "defeasible-ap.asp")
    if patched:
        text = open(f).read()
        for old, new in PATCHES:
            assert text.count(old) == 1, "upstream encoding changed; review the patch: " + old[:50]
            text = text.replace(old, new)
        open(f, "w").write(text)
    return d

TAG = {"definite": "+D", "definiteRefuted": "-D", "supported": "+S", "unsupported": "-S",
       "defeasible": "+d", "refuted": "-d"}

def asp_tags(th, enc):
    amap = {a: f"a{i}" for i, a in enumerate(th["atoms"])}
    rmap = {r[0]: f"r{i}" for i, r in enumerate(th["rules"])}
    L = lambda s: f"non({amap[s[1:]]})" if s.startswith("-") else amap[s]
    prog = [f"atom({v})." for v in amap.values()] + [f"fact({L(f)})." for f in th["facts"]]
    kinds = {"S": "strictRule", "D": "defeasibleRule", "F": "defeater"}
    for (l, k, h, b) in th["rules"]:
        prog.append(f"{kinds[k]}({rmap[l]},{L(h)}).")
        prog += [f"body({rmap[l]},{L(x)})." for x in b]
    prog += [f"superior({rmap[t]},{rmap[s]})." for (t, s) in th["sup"]]
    ctl = clingo.Control(["0", "--warn=none"])
    ctl.load(os.path.join(enc, "Basic", "defeasible-ap.asp"))
    ctl.add("base", [], "\n".join(prog)); ctl.ground([("base", [])])
    models = []
    ctl.solve(on_model=lambda m: models.append([str(s) for s in m.symbols(shown=True)]))
    assert len(models) == 1, f"{th['id']}: {len(models)} stable models"
    inv = {v: k for k, v in amap.items()}
    out = {lit: set() for a in th["atoms"] for lit in (a, "-" + a)}
    for s in models[0]:
        pred, _, arg = s.partition("(")
        if pred not in TAG or "," in arg: continue
        arg = arg[:-1]
        out["-" + inv[arg[4:-1]] if arg.startswith("non(") else inv[arg]].add(TAG[pred])
    return out

def pick(tags, keys): return "".join(k for k in keys if k in tags)

def check(ddl, theories_path, tags_path, support):
    theories = parse(open(theories_path, encoding="utf-8").read())
    mine = {}
    for line in open(tags_path, encoding="utf-8").read().splitlines():
        i, lit, *tag = line.split(" ")
        mine[(i, lit)] = tag[0] if tag else ""
    keys = ("+d", "-d", "+S", "-S") if support else ("+d", "-d")
    result = {}
    for label, patched in (("upstream", False), ("patched", True)):
        enc = prepare(ddl, patched)
        mismatches, lits, both = [], 0, 0
        for th in theories:
            for lit, tag in asp_tags(th, enc).items():
                lits += 1
                both += ("+d" in tag and "-d" in tag)
                a, b = pick(mine.get((th["id"], lit), ""), keys), pick(tag, keys)
                if a != b:
                    mismatches.append((th["id"], lit, a, b))
        print(f"[{label}] theories={len(theories)} literals={lits} mismatches={len(mismatches)}"
              f" asp_inconsistent_literals={both}")
        for m in mismatches[:5]:
            print("  MISMATCH", m)
        result[label] = len(mismatches)
    sys.exit(1 if result["patched"] else 0)

if __name__ == "__main__":
    if sys.argv[1] == "emit":
        rng = random.Random(int(sys.argv[3]))
        sys.stdout.write("".join(gen_random(rng, i) for i in range(int(sys.argv[2]))))
    else:
        check(sys.argv[2], sys.argv[3], sys.argv[4], "--support" in sys.argv)