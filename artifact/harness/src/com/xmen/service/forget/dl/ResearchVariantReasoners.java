package com.xmen.service.forget.dl;

import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.DefeasibleTheory.Rule;
import com.xmen.service.forget.dl.DefeasibleTheory.RuleKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Research prototypes of two other members of the Defeasible Logic family, used only to study
 * how the Forget-DL decision depends on the choice of proof theory. They are NOT part of X-Men 2.0.
 *
 * <ul>
 *   <li>{@link NoTeamDefeat}: ambiguity blocking, individual defeat (a single supporting rule must
 *       be superior to every applicable attacker).
 *   <li>{@link AmbiguityPropagating}: DL(δ) with support ∫ (Antoniou, Billington, Governatori,
 *       Maher, Rock 2000), team defeat. The δ tags are reported in the ∂ slots of
 *       {@link DefeasibleReasoner.Conclusions}; the ∫ tags are available through {@link
 *       AmbiguityPropagating#support()}.
 * </ul>
 *
 * Both compute least fixpoints of their (monotone) proof conditions, as the production reasoner.
 */
public final class ResearchVariantReasoners {

  private ResearchVariantReasoners() {}

  /** Shared indexes and the definite layer (+Δ/−Δ), identical for every variant. */
  private static final class Index {
    final Map<Literal, List<Rule>> all = new HashMap<>();
    final Map<Literal, List<Rule>> strict = new HashMap<>();
    final Map<Literal, List<Rule>> support = new HashMap<>();
    final List<Literal> universe = new ArrayList<>();
    final Set<Literal> pd = new HashSet<>();
    final Set<Literal> md = new HashSet<>();

    Index(DefeasibleTheory theory) {
      for (Rule r : theory.rules()) {
        all.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
        if (r.kind() == RuleKind.STRICT) {
          strict.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
        }
        if (r.kind() != RuleKind.DEFEATER) {
          support.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
        }
      }
      for (String a : theory.atoms()) {
        universe.add(Literal.pos(a));
        universe.add(Literal.neg(a));
      }
      boolean changed = true;
      while (changed) {
        changed = false;
        for (Literal q : universe) {
          List<Rule> sq = strict.getOrDefault(q, List.of());
          if (!pd.contains(q) && (theory.facts().contains(q)
              || sq.stream().anyMatch(r -> allIn(r.body(), pd)))) {
            pd.add(q);
            changed = true;
          }
          if (!md.contains(q) && !theory.facts().contains(q)
              && sq.stream().allMatch(r -> anyIn(r.body(), md))) {
            md.add(q);
            changed = true;
          }
        }
      }
    }

    List<Rule> sup(Literal q) {
      return support.getOrDefault(q, List.of());
    }

    List<Rule> att(Literal q) {
      return all.getOrDefault(q.complement(), List.of());
    }
  }

  /** Ambiguity blocking without team defeat. */
  public static final class NoTeamDefeat implements DefeasibleReasoner {
    @Override
    public Conclusions reason(DefeasibleTheory theory) {
      Index ix = new Index(theory);
      Set<Literal> pp = new HashSet<>();
      Set<Literal> mp = new HashSet<>();
      boolean changed = true;
      while (changed) {
        changed = false;
        for (Literal q : ix.universe) {
          Literal nq = q.complement();
          if (!pp.contains(q)) {
            boolean plus = ix.pd.contains(q);
            if (!plus && ix.md.contains(nq)) {
              for (Rule r : ix.sup(q)) {
                if (!allIn(r.body(), pp)) {
                  continue;
                }
                boolean beatsAll = true;
                for (Rule s : ix.att(q)) {
                  if (!anyIn(s.body(), mp) && !theory.isSuperior(r.label(), s.label())) {
                    beatsAll = false;
                    break;
                  }
                }
                if (beatsAll) {
                  plus = true;
                  break;
                }
              }
            }
            if (plus) {
              pp.add(q);
              changed = true;
            }
          }
          if (!mp.contains(q) && ix.md.contains(q)) {
            boolean minus = ix.pd.contains(nq);
            if (!minus) {
              minus = true;
              for (Rule r : ix.sup(q)) {
                if (anyIn(r.body(), mp)) {
                  continue;
                }
                boolean answered = false;
                for (Rule s : ix.att(q)) {
                  if (allIn(s.body(), pp) && !theory.isSuperior(r.label(), s.label())) {
                    answered = true;
                    break;
                  }
                }
                if (!answered) {
                  minus = false;
                  break;
                }
              }
            }
            if (minus) {
              mp.add(q);
              changed = true;
            }
          }
        }
      }
      return new Conclusions(ix.pd, ix.md, pp, mp);
    }
  }

  /** Ambiguity propagation: DL(δ) with support ∫, team defeat. */
  public static final class AmbiguityPropagating implements DefeasibleReasoner {
    private Set<Literal> ps = Set.of();
    private Set<Literal> ms = Set.of();

    /** +∫ and −∫ of the last theory reasoned about. */
    public Set<Literal>[] support() {
      @SuppressWarnings("unchecked")
      Set<Literal>[] out = new Set[] {ps, ms};
      return out;
    }

    @Override
    public Conclusions reason(DefeasibleTheory theory) {
      Index ix = new Index(theory);
      Set<Literal> pdl = new HashSet<>(); // +δ
      Set<Literal> mdl = new HashSet<>(); // −δ
      Set<Literal> psu = new HashSet<>(); // +∫
      Set<Literal> msu = new HashSet<>(); // −∫
      boolean changed = true;
      while (changed) {
        changed = false;
        for (Literal q : ix.universe) {
          Literal nq = q.complement();
          // +∫q: +Δq or ∃r∈Rsd[q]: A(r)⊆+∫ and ∀s∈R[∼q]: ∃a∈A(s): −δa, or not s>r
          if (!psu.contains(q)) {
            boolean v = ix.pd.contains(q);
            for (Rule r : ix.sup(q)) {
              if (v) {
                break;
              }
              if (!allIn(r.body(), psu)) {
                continue;
              }
              boolean ok = true;
              for (Rule s : ix.att(q)) {
                if (!anyIn(s.body(), mdl) && theory.isSuperior(s.label(), r.label())) {
                  ok = false;
                  break;
                }
              }
              v = ok;
            }
            if (v) {
              psu.add(q);
              changed = true;
            }
          }
          // −∫q: −Δq and ∀r∈Rsd[q]: ∃a∈A(r): −∫a, or ∃s∈R[∼q]: A(s)⊆+δ and s>r
          if (!msu.contains(q) && ix.md.contains(q)) {
            boolean v = true;
            for (Rule r : ix.sup(q)) {
              if (anyIn(r.body(), msu)) {
                continue;
              }
              boolean killed = false;
              for (Rule s : ix.att(q)) {
                if (allIn(s.body(), pdl) && theory.isSuperior(s.label(), r.label())) {
                  killed = true;
                  break;
                }
              }
              if (!killed) {
                v = false;
                break;
              }
            }
            if (v) {
              msu.add(q);
              changed = true;
            }
          }
          // +δq: +Δq or (∃r∈Rsd[q]: A(r)⊆+δ) and −Δ∼q and ∀s∈R[∼q]: (∃a∈A(s): −∫a) or
          //      (∃t∈Rsd[q]: A(t)⊆+δ and t>s)
          if (!pdl.contains(q)) {
            boolean v = ix.pd.contains(q);
            if (!v && ix.md.contains(nq) && ix.sup(q).stream().anyMatch(r -> allIn(r.body(), pdl))) {
              v = true;
              for (Rule s : ix.att(q)) {
                if (anyIn(s.body(), msu)) {
                  continue;
                }
                boolean overruled = ix.sup(q).stream()
                    .anyMatch(t -> allIn(t.body(), pdl) && theory.isSuperior(t.label(), s.label()));
                if (!overruled) {
                  v = false;
                  break;
                }
              }
            }
            if (v) {
              pdl.add(q);
              changed = true;
            }
          }
          // −δq: −Δq and ( ∀r∈Rsd[q]: ∃a∈A(r): −δa  or  +Δ∼q  or
          //      ∃s∈R[∼q]: A(s)⊆+∫ and ∀t∈Rsd[q]: ∃a∈A(t): −δa or not t>s )
          if (!mdl.contains(q) && ix.md.contains(q)) {
            boolean v = ix.sup(q).stream().allMatch(r -> anyIn(r.body(), mdl)) || ix.pd.contains(nq);
            if (!v) {
              for (Rule s : ix.att(q)) {
                if (!allIn(s.body(), psu)) {
                  continue;
                }
                boolean unanswered = ix.sup(q).stream()
                    .allMatch(t -> anyIn(t.body(), mdl) || !theory.isSuperior(t.label(), s.label()));
                if (unanswered) {
                  v = true;
                  break;
                }
              }
            }
            if (v) {
              mdl.add(q);
              changed = true;
            }
          }
        }
      }
      ps = psu;
      ms = msu;
      return new Conclusions(ix.pd, ix.md, pdl, mdl);
    }
  }

  static boolean allIn(List<Literal> body, Set<Literal> set) {
    for (Literal l : body) {
      if (!set.contains(l)) {
        return false;
      }
    }
    return true;
  }

  static boolean anyIn(List<Literal> body, Set<Literal> set) {
    for (Literal l : body) {
      if (set.contains(l)) {
        return true;
      }
    }
    return false;
  }
}
