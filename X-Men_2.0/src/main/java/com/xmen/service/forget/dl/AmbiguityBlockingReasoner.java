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
 * Reference implementation of the proof conditions of ambiguity-blocking DL(&#8706;) with team
 * defeat, as given by Antoniou, Billington, Governatori and Maher, "Representation Results for
 * Defeasible Logic" (ACM TOCL 2001), cited as [29] in the paper:
 *
 * <pre>
 * +Δq: q ∈ F, or ∃r ∈ Rs[q] ∀a ∈ A(r): +Δa
 * -Δq: q ∉ F and ∀r ∈ Rs[q] ∃a ∈ A(r): -Δa
 * +∂q: +Δq, or (2.1) ∃r ∈ Rsd[q] ∀a ∈ A(r): +∂a, (2.2) -Δ∼q, and
 *      (2.3) ∀s ∈ R[∼q]: (2.3.1) ∃a ∈ A(s): -∂a, or
 *                        (2.3.2) ∃t ∈ Rsd[q] with ∀a ∈ A(t): +∂a and t &gt; s
 * -∂q: -Δq and ( (2.1) ∀r ∈ Rsd[q] ∃a ∈ A(r): -∂a, or (2.2) +Δ∼q, or
 *      (2.3) ∃s ∈ R[∼q]: (2.3.1) ∀a ∈ A(s): +∂a and
 *                        (2.3.2) ∀t ∈ Rsd[q]: ∃a ∈ A(t): -∂a, or not t &gt; s )
 * </pre>
 *
 * Conclusions are the least fixpoint of these (monotone) conditions, i.e. exactly the tags that
 * have finite proofs. Literals on positive loops therefore receive neither +&#8706; nor -&#8706;.
 * Defeaters never support their head and never act as team members in (2.3.2).
 */
public final class AmbiguityBlockingReasoner implements DefeasibleReasoner {

  @Override
  public Conclusions reason(DefeasibleTheory theory) {
    Map<Literal, List<Rule>> all = new HashMap<>();
    Map<Literal, List<Rule>> strict = new HashMap<>();
    Map<Literal, List<Rule>> support = new HashMap<>(); // strict + defeasible
    for (Rule r : theory.rules()) {
      all.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
      if (r.kind() == RuleKind.STRICT) {
        strict.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
      }
      if (r.kind() != RuleKind.DEFEATER) {
        support.computeIfAbsent(r.head(), k -> new ArrayList<>()).add(r);
      }
    }
    List<Literal> universe = new ArrayList<>();
    for (String a : theory.atoms()) {
      universe.add(Literal.pos(a));
      universe.add(Literal.neg(a));
    }

    Set<Literal> pd = new HashSet<>();
    Set<Literal> md = new HashSet<>();
    Set<Literal> pp = new HashSet<>();
    Set<Literal> mp = new HashSet<>();
    Set<Literal> facts = theory.facts();

    boolean changed = true;
    while (changed) {
      changed = false;
      for (Literal q : universe) {
        List<Rule> sq = strict.getOrDefault(q, List.of());
        if (!pd.contains(q)
            && (facts.contains(q) || sq.stream().anyMatch(r -> allIn(r.body(), pd)))) {
          pd.add(q);
          changed = true;
        }
        if (!md.contains(q)
            && !facts.contains(q)
            && sq.stream().allMatch(r -> anyIn(r.body(), md))) {
          md.add(q);
          changed = true;
        }
      }
      for (Literal q : universe) {
        Literal nq = q.complement();
        List<Rule> supQ = support.getOrDefault(q, List.of());
        List<Rule> attackers = all.getOrDefault(nq, List.of());
        if (!pp.contains(q) && plusPartial(theory, q, nq, supQ, attackers, pd, md, pp, mp)) {
          pp.add(q);
          changed = true;
        }
        if (!mp.contains(q) && minusPartial(theory, q, nq, supQ, attackers, pd, md, pp, mp)) {
          mp.add(q);
          changed = true;
        }
      }
    }
    return new Conclusions(pd, md, pp, mp);
  }

  private static boolean plusPartial(
      DefeasibleTheory theory, Literal q, Literal nq, List<Rule> supQ, List<Rule> attackers,
      Set<Literal> pd, Set<Literal> md, Set<Literal> pp, Set<Literal> mp) {
    if (pd.contains(q)) {
      return true;
    }
    boolean applicableSupport = supQ.stream().anyMatch(r -> allIn(r.body(), pp));
    if (!applicableSupport || !md.contains(nq)) {
      return false;
    }
    for (Rule s : attackers) {
      boolean discarded = anyIn(s.body(), mp);
      if (discarded) {
        continue;
      }
      boolean overruled = supQ.stream()
          .anyMatch(t -> allIn(t.body(), pp) && theory.isSuperior(t.label(), s.label()));
      if (!overruled) {
        return false;
      }
    }
    return true;
  }

  private static boolean minusPartial(
      DefeasibleTheory theory, Literal q, Literal nq, List<Rule> supQ, List<Rule> attackers,
      Set<Literal> pd, Set<Literal> md, Set<Literal> pp, Set<Literal> mp) {
    if (!md.contains(q)) {
      return false;
    }
    if (supQ.stream().allMatch(r -> anyIn(r.body(), mp))) {
      return true;
    }
    if (pd.contains(nq)) {
      return true;
    }
    for (Rule s : attackers) {
      if (!allIn(s.body(), pp)) {
        continue;
      }
      boolean unanswered = supQ.stream()
          .allMatch(t -> anyIn(t.body(), mp) || !theory.isSuperior(t.label(), s.label()));
      if (unanswered) {
        return true;
      }
    }
    return false;
  }

  private static boolean allIn(List<Literal> body, Set<Literal> set) {
    for (Literal l : body) {
      if (!set.contains(l)) {
        return false;
      }
    }
    return true;
  }

  private static boolean anyIn(List<Literal> body, Set<Literal> set) {
    for (Literal l : body) {
      if (set.contains(l)) {
        return true;
      }
    }
    return false;
  }
}
