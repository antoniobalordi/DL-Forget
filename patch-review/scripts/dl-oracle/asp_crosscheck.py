#!/usr/bin/env python3
"""Differential check of the Java DL(∂) reasoner against Governatori's ASP encoding.

Usage: asp_crosscheck.py <ddl-repo-dir> <java-classpath> [n_random] [n_compiled] [seed]

* Random general theories are acyclic, have consistent facts/strict heads, and never make a
  defeater superior for a literal that also has strict/defeasible rules. Under these conditions
  the ASP variant (defeaters may rebut as team members) coincides with Antoniou et al. 2001.
* Compiled theories come from random Forget scenarios exported by DlOracleHarness.
The upstream encoding (gvdgdo/Defeasible-Deontic-Logic@679c638, Basic/defeasible-ab.asp) loses
the binding of the attacker S between overruled/2 and discardedOrNotSuperior/2, so under team
defeat it can derive both defeasible(X) and refuted(X). The script therefore checks against the
upstream file AND against a locally patched copy in which S is bound (DL(∂) condition -∂ 2.3).
Exit code 0 iff the patched encoding agrees on every tag of every literal.
"""
import random, subprocess, sys, tempfile, os, shutil
import clingo

UPSTREAM_OVERRULED = """overruled(R,X) :- rule(R,X), rule(S,Y), opposes(X,Y), applicable(S,Y),
    discardedOrNotSuperior(T,X) : rule(T,X).

discardedOrNotSuperior(T,X) :- rule(T,X), discarded(T,X).
discardedOrNotSuperior(T,X) :- rule(T,X), opposes(X,Y), rule(S,Y), 
    applicable(S,Y), not superior(T,S)."""
PATCHED_OVERRULED = """overruled(R,X) :- rule(R,X), rule(S,Y), opposes(X,Y), applicable(S,Y),
    discardedOrNotSuperior(T,X,S) : rule(T,X).

discardedOrNotSuperior(T,X,S) :- rule(T,X), opposes(X,Y), rule(S,Y), discarded(T,X).
discardedOrNotSuperior(T,X,S) :- rule(T,X), opposes(X,Y), rule(S,Y), not superior(T,S)."""

def prepare(ddl, patched):
    d = tempfile.mkdtemp(prefix="ddl_")
    shutil.copytree(os.path.join(ddl, "Basic"), os.path.join(d, "Basic"))
    shutil.copy(os.path.join(ddl, "language.asp"), d)
    f = os.path.join(d, "Basic", "defeasible-ab.asp")
    if patched:
        text = open(f).read()
        assert UPSTREAM_OVERRULED in text, "upstream encoding changed; review the patch"
        open(f, "w").write(text.replace(UPSTREAM_OVERRULED, PATCHED_OVERRULED))
    return d

def gen_random(rng, idx):
    n = rng.randint(3, 7)
    atoms = [f"p{i}" for i in range(n)]
    lines, rules, heads_by_lit, fact_lits = [f"theory rnd{idx}"], [], {}, set()
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
        comp = head[1:] if head.startswith("-") else "-" + head
        if kind == "S" and comp in strict_heads:
            kind = "D"
        if kind == "S":
            strict_heads.add(head)
        rules.append((f"r{r}", kind, head, body))
    nondefeater_heads = {h for (_, k, h, _) in rules if k != "F"}
    sups = []
    rank = {l: rng.random() for (l, _, _, _) in rules}  # orient edges by rank: acyclic by construction
    for (l1, k1, h1, _) in rules:
        for (l2, k2, h2, _) in rules:
            comp = h2[1:] if h2.startswith("-") else "-" + h2
            if h1 == comp and l1 < l2 and rng.random() < .5:
                t, s = (l1, l2) if rank[l1] < rank[l2] else (l2, l1)
                kt, ht = next((k, h) for (l, k, h, _) in rules if l == t)
                if kt == "F" and ht in nondefeater_heads:
                    continue
                sups.append((t, s))
    for (l, k, h, b) in rules:
        lines.append(" ".join(["rule", l, k, h] + b))
    for t, s in sups:
        lines.append(f"sup {t} {s}")
    lines.append("end")
    return "\n".join(lines) + "\n"

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

def asp_tags(th, ddl):
    amap = {a: f"a{i}" for i, a in enumerate(th["atoms"])}
    rmap = {r[0]: f"r{i}" for i, r in enumerate(th["rules"])}
    L = lambda s: f"non({amap[s[1:]]})" if s.startswith("-") else amap[s]
    prog = [f"atom({v})." for v in amap.values()]
    prog += [f"fact({L(f)})." for f in th["facts"]]
    kinds = {"S": "strictRule", "D": "defeasibleRule", "F": "defeater"}
    for (l, k, h, b) in th["rules"]:
        prog.append(f"{kinds[k]}({rmap[l]},{L(h)}).")
        prog += [f"body({rmap[l]},{L(x)})." for x in b]
    prog += [f"superior({rmap[t]},{rmap[s]})." for (t, s) in th["sup"]]
    ctl = clingo.Control(["0", "--warn=none"])
    ctl.load(os.path.join(ddl, "Basic", "defeasible-ab.asp"))
    ctl.add("base", [], "\n".join(prog)); ctl.ground([("base", [])])
    models = []
    ctl.solve(on_model=lambda m: models.append([str(s) for s in m.symbols(shown=True)]))
    assert len(models) == 1, f"{th['id']}: {len(models)} stable models"
    inv = {v: k for k, v in amap.items()}
    out = {}
    for a in th["atoms"]:
        for lit in (a, "-" + a):
            out[lit] = ""
    for s in models[0]:
        pred, arg = s.split("(", 1); arg = arg[:-1]
        tag = {"definite": "+D", "definiteRefuted": "-D", "defeasible": "+d", "refuted": "-d"}.get(pred)
        if not tag: continue
        lit = "-" + inv[arg[4:-1]] if arg.startswith("non(") else inv[arg]
        out[lit] += tag
    order = ["+D", "-D", "+d", "-d"]
    return {k: "".join(t for t in order if t in v) for k, v in out.items()}

def main():
    ddl, cp = sys.argv[1], sys.argv[2]
    n_rnd = int(sys.argv[3]) if len(sys.argv) > 3 else 300
    n_cmp = int(sys.argv[4]) if len(sys.argv) > 4 else 150
    seed = int(sys.argv[5]) if len(sys.argv) > 5 else 1
    rng = random.Random(seed)
    text = "".join(gen_random(rng, i) for i in range(n_rnd))
    text += subprocess.run(["java", "-cp", cp, "com.xmen.service.forget.dl.DlOracleHarness",
                            "export", str(n_cmp), str(seed)], capture_output=True, text=True,
                           check=True).stdout
    with tempfile.NamedTemporaryFile("w", suffix=".dl", delete=False, encoding="utf-8") as f:
        f.write(text); path = f.name
    java = {}
    for line in subprocess.run(["java", "-cp", cp, "com.xmen.service.forget.dl.DlOracleHarness",
                                "tags", path], capture_output=True, text=True, check=True).stdout.splitlines():
        i, lit, *tag = line.split(" ")
        java[(i, lit)] = tag[0] if tag else ""
    theories = parse(text)
    result = {}
    for label, patched in (("upstream", False), ("patched", True)):
        enc = prepare(ddl, patched)
        mismatches, lits, both = [], 0, 0
        for th in theories:
            for lit, tag in asp_tags(th, enc).items():
                lits += 1
                both += ("+d" in tag and "-d" in tag)
                if java[(th["id"], lit)] != tag:
                    mismatches.append((th["id"], lit, java[(th["id"], lit)], tag))
        java_both = sum(1 for v in java.values() if "+d" in v and "-d" in v)
        print(f"[{label}] theories={len(theories)} literals={lits} mismatches={len(mismatches)}"
              f" asp_inconsistent_literals={both} java_inconsistent_literals={java_both}")
        for m in mismatches[:5]:
            print("  MISMATCH", m)
        result[label] = len(mismatches)
    sys.exit(1 if result["patched"] else 0)

if __name__ == "__main__":
    main()
