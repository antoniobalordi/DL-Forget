package com.xmen.service.forget.dl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A finite, ground, propositional defeasible theory D = (F, R, &gt;) as used in Section 7.1 of the
 * Forget paper (Antoniou et al., ambiguity-blocking DL(&#8706;) with team defeat).
 *
 * <p>Literals carry an explicit polarity: {@code ¬p} is the complementary literal of {@code p},
 * never negation-as-failure. The superiority relation must be acyclic and may only relate rules
 * with complementary heads (paper, Section 7.1).
 */
public final class DefeasibleTheory {

  /** Rule types: strict (→), defeasible (⇒), defeater (⇝). */
  public enum RuleKind {
    STRICT("->"),
    DEFEASIBLE("=>"),
    DEFEATER("~>");

    private final String arrow;

    RuleKind(String arrow) {
      this.arrow = arrow;
    }

    public String arrow() {
      return arrow;
    }
  }

  /** A literal: an atom or its explicit complement. */
  public record Literal(String atom, boolean negated) implements Comparable<Literal> {
    public Literal {
      Objects.requireNonNull(atom, "atom");
      if (atom.isBlank()) {
        throw new IllegalArgumentException("atom must not be blank");
      }
    }

    public static Literal pos(String atom) {
      return new Literal(atom, false);
    }

    public static Literal neg(String atom) {
      return new Literal(atom, true);
    }

    public Literal complement() {
      return new Literal(atom, !negated);
    }

    @Override
    public int compareTo(Literal o) {
      int c = atom.compareTo(o.atom);
      return c != 0 ? c : Boolean.compare(negated, o.negated);
    }

    @Override
    public String toString() {
      return negated ? "¬" + atom : atom;
    }
  }

  /** A labelled rule {@code label: body kind head}. */
  public record Rule(String label, RuleKind kind, List<Literal> body, Literal head) {
    public Rule {
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(head, "head");
      body = List.copyOf(body);
      if (label.isBlank()) {
        throw new IllegalArgumentException("rule label must not be blank");
      }
    }

    @Override
    public String toString() {
      return label + ": " + String.join(", ", body.stream().map(Literal::toString).toList())
          + " " + kind.arrow() + " " + head;
    }
  }

  private final Set<Literal> facts;
  private final List<Rule> rules;
  private final Map<String, Rule> rulesByLabel;
  private final Map<String, Set<String>> superior; // t -> {s | t > s}
  private final Set<String> atoms;

  private DefeasibleTheory(
      Set<Literal> facts, List<Rule> rules, Map<String, Set<String>> superior,
      Set<String> declaredAtoms) {
    this.facts = Collections.unmodifiableSet(new TreeSet<>(facts));
    this.rules = List.copyOf(rules);
    Map<String, Rule> byLabel = new LinkedHashMap<>();
    for (Rule r : rules) {
      if (byLabel.put(r.label(), r) != null) {
        throw new IllegalArgumentException("duplicate rule label: " + r.label());
      }
    }
    this.rulesByLabel = Collections.unmodifiableMap(byLabel);
    Map<String, Set<String>> sup = new HashMap<>();
    superior.forEach((k, v) -> sup.put(k, Collections.unmodifiableSet(new TreeSet<>(v))));
    this.superior = Collections.unmodifiableMap(sup);
    Set<String> all = new TreeSet<>(declaredAtoms);
    facts.forEach(f -> all.add(f.atom()));
    for (Rule r : rules) {
      all.add(r.head().atom());
      r.body().forEach(b -> all.add(b.atom()));
    }
    this.atoms = Collections.unmodifiableSet(all);
    validateSuperiority();
  }

  private void validateSuperiority() {
    for (Map.Entry<String, Set<String>> e : superior.entrySet()) {
      Rule t = rulesByLabel.get(e.getKey());
      if (t == null) {
        throw new IllegalArgumentException("superiority refers to unknown rule: " + e.getKey());
      }
      for (String sLabel : e.getValue()) {
        Rule s = rulesByLabel.get(sLabel);
        if (s == null) {
          throw new IllegalArgumentException("superiority refers to unknown rule: " + sLabel);
        }
        if (!t.head().equals(s.head().complement())) {
          throw new IllegalArgumentException(
              "superiority " + t.label() + " > " + s.label() + " relates non-complementary heads");
        }
      }
    }
    // Acyclicity (DFS with colours).
    Map<String, Integer> colour = new HashMap<>();
    for (String start : superior.keySet()) {
      if (hasCycle(start, colour)) {
        throw new IllegalArgumentException("superiority relation is cyclic (involves " + start + ")");
      }
    }
  }

  private boolean hasCycle(String node, Map<String, Integer> colour) {
    Integer c = colour.get(node);
    if (c != null) {
      return c == 1;
    }
    colour.put(node, 1);
    for (String next : superior.getOrDefault(node, Set.of())) {
      if (hasCycle(next, colour)) {
        return true;
      }
    }
    colour.put(node, 2);
    return false;
  }

  public Set<Literal> facts() {
    return facts;
  }

  public List<Rule> rules() {
    return rules;
  }

  public Rule rule(String label) {
    return rulesByLabel.get(label);
  }

  /** All atoms of the theory, including atoms declared only for querying. */
  public Set<String> atoms() {
    return atoms;
  }

  /** Returns true iff {@code t > s}. */
  public boolean isSuperior(String t, String s) {
    return superior.getOrDefault(t, Set.of()).contains(s);
  }

  /** Deterministic pairs (t, s) with t &gt; s. */
  public List<String[]> superiorityPairs() {
    List<String[]> out = new ArrayList<>();
    new TreeSet<>(superior.keySet())
        .forEach(t -> superior.get(t).forEach(s -> out.add(new String[] {t, s})));
    return out;
  }

  /** Canonical, deterministic text rendering (stable across runs; used for export and tests). */
  public String toText() {
    StringBuilder sb = new StringBuilder();
    for (String a : atoms) {
      sb.append("atom ").append(a).append('\n');
    }
    for (Literal f : facts) {
      sb.append("fact ").append(f).append('\n');
    }
    rules.stream()
        .sorted((a, b) -> a.label().compareTo(b.label()))
        .forEach(r -> sb.append("rule ").append(r).append('\n'));
    for (String[] p : superiorityPairs()) {
      sb.append("sup ").append(p[0]).append(" > ").append(p[1]).append('\n');
    }
    return sb.toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder; {@link #build()} validates labels and superiority. */
  public static final class Builder {
    private final Set<Literal> facts = new LinkedHashSet<>();
    private final List<Rule> rules = new ArrayList<>();
    private final Set<String> labels = new HashSet<>();
    private final Map<String, Set<String>> superior = new LinkedHashMap<>();
    private final Set<String> declared = new LinkedHashSet<>();

    public Builder fact(Literal l) {
      facts.add(l);
      return this;
    }

    public Builder rule(Rule r) {
      if (!labels.add(r.label())) {
        throw new IllegalArgumentException("duplicate rule label: " + r.label());
      }
      rules.add(r);
      return this;
    }

    public boolean hasRule(String label) {
      return labels.contains(label);
    }

    public Builder strict(String label, Literal head, Literal... body) {
      return rule(new Rule(label, RuleKind.STRICT, List.of(body), head));
    }

    public Builder defeasible(String label, Literal head, Literal... body) {
      return rule(new Rule(label, RuleKind.DEFEASIBLE, List.of(body), head));
    }

    public Builder defeater(String label, Literal head, Literal... body) {
      return rule(new Rule(label, RuleKind.DEFEATER, List.of(body), head));
    }

    /** Records {@code t > s}. */
    public Builder superior(String t, String s) {
      superior.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(s);
      return this;
    }

    /** Declares an atom so that it receives tags even if no fact or rule mentions it. */
    public Builder declare(String atom) {
      declared.add(atom);
      return this;
    }

    public DefeasibleTheory build() {
      return new DefeasibleTheory(facts, rules, superior, declared);
    }
  }
}
