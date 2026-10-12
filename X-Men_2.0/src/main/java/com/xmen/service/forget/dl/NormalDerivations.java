package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Enumerates &#928;<sub>m</sub>: the normal derivations of a target from a finite knowledge set
 * (paper Section 3.2), as {@link Derivation} trees using the X-Men conventions ("Initial" leaves).
 *
 * <p>Inference rules (labels used by procedural forgetting, Definition 7):
 *
 * <ul>
 *   <li>composition: {@code pair}, {@code senc}, {@code aenc}, and every other public constructor
 *       under its own symbol (e.g. {@code sign}, {@code h}, {@code pk}, user functions);
 *   <li>decomposition: {@code proj} (pair decomposition, i ∈ {1,2}), {@code sdec} (premises:
 *       ciphertext, key), {@code adec} (premises: ciphertext {@code aenc(m,pk(sk))}, sk).
 * </ul>
 *
 * <p>Normality: a decomposition is never applied to the output of its inverse composition. In
 * addition, no term is repeated on a root-to-leaf branch (assumption A3); this subsumes the
 * "nor vice versa" cases and makes &#928;<sub>m</sub> finite, because every term of such a
 * derivation is a subterm of K ∪ {m}. Decryption nodes always carry both the ciphertext and the
 * key premise, so both are hypotheses when they are leaves.
 *
 * <p>Coverage is explicit: exceeding a limit yields {@link Coverage#TRUNCATED}, never a silently
 * shortened "complete" set.
 */
public final class NormalDerivations {

  public static final String INITIAL = "Initial";

  public enum Coverage {
    COMPLETE,
    TRUNCATED,
    UNSUPPORTED
  }

  /** Enumeration limits; reaching either one marks the result as truncated. */
  public record Limits(int maxDerivationsPerGoal, int maxSteps) {
    public static final Limits DEFAULT = new Limits(512, 250_000);

    public Limits {
      if (maxDerivationsPerGoal < 1 || maxSteps < 1) {
        throw new IllegalArgumentException("enumeration limits must be positive");
      }
    }
  }

  /**
   * Result for the finite, nonrepeating basis. {@code cyclicDerivations} reports pruned cyclic
   * decryption dependencies: completeness for whole-procedure forgetting is then unestablished.
   */
  public record Result(Message target, List<Derivation> derivations, Coverage coverage,
      String note, boolean cyclicDerivations) {
    public Result {
      derivations = List.copyOf(derivations);
    }
  }

  private final Set<String> privateFunctions;
  private final Limits limits;

  public NormalDerivations() {
    this(Set.of(), Limits.DEFAULT);
  }

  public NormalDerivations(Set<String> privateFunctions, Limits limits) {
    this.privateFunctions = Set.copyOf(privateFunctions);
    this.limits = limits;
  }

  public Result enumerate(Message target, Set<Message> knowledge) {
    Optional<String> bad = Terms.unsupportedConstruct(target);
    if (bad.isEmpty()) {
      for (Message k : knowledge) {
        Optional<String> unsupported = Terms.unsupportedConstruct(k);
        if (unsupported.isPresent()
            && (bad.isEmpty() || unsupported.get().compareTo(bad.get()) < 0)) {
          bad = unsupported;
        }
      }
    }
    if (bad.isPresent()) {
      return new Result(target, List.of(), Coverage.UNSUPPORTED, bad.get(), false);
    }
    Run run = new Run(knowledge);
    List<Derivation> found = run.derive(target, new HashMap<>(), null, 0);
    List<Derivation> sorted = new ArrayList<>(new LinkedHashSet<>(found));
    sorted.sort(Comparator.comparing(NormalDerivations::canonicalTree));
    return new Result(target, sorted,
        run.truncated ? Coverage.TRUNCATED : Coverage.COMPLETE,
        run.truncated ? "limit reached: " + limits
            : run.cyclicDerivations ? "cyclic decryption dependencies outside the finite basis" : "",
        run.cyclicDerivations);
  }

  /** Hyp<sub>π</sub>: the knowledge leaves of a derivation. */
  public static Set<Message> hypotheses(Derivation d) {
    Set<Message> out = new LinkedHashSet<>();
    collectHyps(d, out);
    return out;
  }

  private static void collectHyps(Derivation d, Set<Message> out) {
    if (INITIAL.equals(d.getRule())) {
      out.add(d.getGoal());
      return;
    }
    for (Derivation p : d.getPremises()) {
      collectHyps(p, out);
    }
  }

  /** Rules(π): the distinct inference-rule labels used by a derivation (Definition 7). */
  public static Set<String> rules(Derivation d) {
    Set<String> out = new TreeSet<>();
    collectRules(d, out);
    return out;
  }

  private static void collectRules(Derivation d, Set<String> out) {
    if (!INITIAL.equals(d.getRule())) {
      out.add(d.getRule());
    }
    for (Derivation p : d.getPremises()) {
      collectRules(p, out);
    }
  }

  /** Canonical tree rendering: identical trees have identical strings at every state. */
  public static String canonicalTree(Derivation d) {
    StringBuilder sb = new StringBuilder();
    appendTree(d, sb);
    return sb.toString();
  }

  private static void appendTree(Derivation d, StringBuilder sb) {
    sb.append(d.getRule()).append('[').append(Terms.canonical(d.getGoal())).append(']');
    if (!d.getPremises().isEmpty()) {
      sb.append('(');
      for (int i = 0; i < d.getPremises().size(); i++) {
        if (i > 0) {
          sb.append(';');
        }
        appendTree(d.getPremises().get(i), sb);
      }
      sb.append(')');
    }
  }

  /**
   * Stable procedure identifier of a derivation tree (Definition 7): independent of display
   * labels, state, context revision and enumeration order.
   */
  public static String procedureId(Derivation d) {
    try {
      StringBuilder key = new StringBuilder();
      appendIdentityTree(d, key);
      byte[] h = MessageDigest.getInstance("SHA-256")
          .digest(key.toString().getBytes(StandardCharsets.UTF_8));
      return "pi_" + HexFormat.of().formatHex(h, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void appendIdentityTree(Derivation d, StringBuilder sb) {
    sb.append(Terms.field(d.getRule())).append(Terms.identity(d.getGoal()))
        .append(d.getPremises().size()).append(':');
    for (Derivation p : d.getPremises()) {
      appendIdentityTree(p, sb);
    }
  }

  /** Human-readable multi-line rendering of a derivation tree. */
  public static String render(Derivation d) {
    StringBuilder sb = new StringBuilder();
    render(d, 0, sb);
    return sb.toString();
  }

  private static void render(Derivation d, int depth, StringBuilder sb) {
    sb.append("  ".repeat(depth)).append(d.getRule()).append(" ⇒ ")
        .append(Terms.canonical(d.getGoal())).append('\n');
    for (Derivation p : d.getPremises()) {
      render(p, depth + 1, sb);
    }
  }

  /** One enumeration run over a fixed knowledge set. */
  private final class Run {
    private final Set<Message> knowledge;
    private final Map<Message, List<Message>> pairsContaining = new HashMap<>();
    private final Map<Message, List<Terms.Cipher>> ciphersOf = new HashMap<>();
    private int steps;
    private boolean truncated;
    private boolean exhausted;
    private boolean cyclicDerivations;

    Run(Set<Message> knowledge) {
      this.knowledge = Set.copyOf(knowledge);
      for (Message s : Terms.sorted(Terms.subterms(knowledge))) {
        if (s instanceof Pair p) {
          pairsContaining.computeIfAbsent(p.getLeft(), k -> new ArrayList<>()).add(p);
          if (!p.getRight().equals(p.getLeft())) {
            pairsContaining.computeIfAbsent(p.getRight(), k -> new ArrayList<>()).add(p);
          }
        }
        Terms.symmetric(s).ifPresent(
            c -> ciphersOf.computeIfAbsent(c.plaintext(), k -> new ArrayList<>()).add(c));
        Terms.asymmetric(s).ifPresent(
            c -> ciphersOf.computeIfAbsent(c.plaintext(), k -> new ArrayList<>()).add(c));
      }
    }

    private boolean full(List<Derivation> out) {
      if (out.size() >= limits.maxDerivationsPerGoal()) {
        truncated = true;
        return true;
      }
      return false;
    }

    List<Derivation> derive(Message goal, Map<Message, Integer> branch,
        String forbiddenConstructor, int keyEdges) {
      List<Derivation> out = new ArrayList<>();
      if (exhausted) {
        return out;
      }
      if (branch.containsKey(goal)) {
        cyclicDerivations |= keyEdges > branch.get(goal);
        return out;
      }
      if (steps++ >= limits.maxSteps()) {
        truncated = true;
        exhausted = true;
        return out;
      }
      Map<Message, Integer> next = new HashMap<>(branch);
      next.put(goal, keyEdges);

      // Hypothesis (leaf).
      if (knowledge.contains(goal)) {
        out.add(new Derivation(goal, INITIAL, List.of()));
      }

      // Composition.
      Optional<String> ctor = Terms.constructorLabel(goal, privateFunctions);
      if (ctor.isPresent() && !ctor.get().equals(forbiddenConstructor)) {
        List<List<Derivation>> options = new ArrayList<>();
        for (Message a : Terms.args(goal)) {
          List<Derivation> ds = derive(a, next, null, keyEdges);
          if (ds.isEmpty()) {
            options = null;
            break;
          }
          options.add(ds);
        }
        if (options != null) {
          product(goal, ctor.get(), options, 0, new ArrayList<>(), out);
        }
      }

      // Pair decomposition: main premise must not come from pair composition.
      for (Message p : pairsContaining.getOrDefault(goal, List.of())) {
        if (exhausted) {
          return out;
        }
        for (Derivation dp : derive(p, next, "pair", keyEdges)) {
          if (full(out)) {
            return out;
          }
          out.add(new Derivation(goal, "proj", List.of(dp)));
        }
      }

      // Decryption: ciphertext must not come from the matching encryption.
      for (Terms.Cipher c : ciphersOf.getOrDefault(goal, List.of())) {
        if (exhausted) {
          return out;
        }
        List<Derivation> cipherDs = derive(c.ciphertext(), next, c.encryptLabel(), keyEdges);
        if (cipherDs.isEmpty()) {
          continue;
        }
        List<Derivation> keyDs = derive(c.decryptionKey(), next, null, keyEdges + 1);
        for (Derivation dc : cipherDs) {
          for (Derivation dk : keyDs) {
            if (full(out)) {
              return out;
            }
            out.add(new Derivation(goal, c.decryptLabel(), List.of(dc, dk)));
          }
        }
      }
      return out;
    }

    private void product(Message goal, String label, List<List<Derivation>> options, int i,
        List<Derivation> acc, List<Derivation> out) {
      if (i == options.size()) {
        if (!full(out)) {
          out.add(new Derivation(goal, label, List.copyOf(acc)));
        }
        return;
      }
      for (Derivation d : options.get(i)) {
        if (truncated && out.size() >= limits.maxDerivationsPerGoal()) {
          return;
        }
        acc.add(d);
        product(goal, label, options, i + 1, acc, out);
        acc.remove(acc.size() - 1);
      }
    }
  }
}
