package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reference implementation of the published Algorithm 1 decision (Sandu et al., lines 11-34),
 * evaluated on the SAME enumerated derivation basis, blocking function, format and label maps as
 * the DL engine, so that a difference in outcome is attributable to the semantics and not to the
 * legacy derivation code (host discrepancies D1-D10). Research harness only.
 *
 * <p>Faithful to the published text: a variant is emitted for every combination once every
 * R_m̄ is non-empty (no usability test of the variant), and R_m̄ uses the syntactic test
 * m̄' ∉ Forget without excluding m̄ itself. Procedural lapses and cues are not part of its state.
 */
public final class ResearchAlg1 {

  private ResearchAlg1() {}

  /**
   * @param variants every variant emitted by the published algorithm
   * @param properVariants variants produced by at least one combination that replaces every
   *     blocked hypothesis by a different message (no self-replacement)
   */
  public record Outcome(String decision, Set<Message> variants, boolean selfReplacement,
      Set<Message> properVariants) {}

  public static Outcome decide(Set<Message> k, Set<Message> forget, BlockingMode mode,
      Map<Message, String> labels, Message m2) {
    NormalDerivations nd = new NormalDerivations();
    ForgetBlocking fb = new ForgetBlocking(forget, mode, Set.of());
    List<Derivation> pis = nd.enumerate(m2, k).derivations();
    for (Derivation pi : pis) {
      if (fb.blocked(NormalDerivations.hypotheses(pi)).isEmpty()) {
        return new Outcome("PRESERVE", Set.of(), false, Set.of());
      }
    }
    Set<Message> variants = new LinkedHashSet<>();
    Set<Message> proper = new LinkedHashSet<>();
    boolean self = false;
    List<Message> kSorted = Terms.sorted(k);
    outer:
    for (Derivation pi : pis) {
      List<Message> keys = new ArrayList<>(fb.blocked(NormalDerivations.hypotheses(pi)));
      List<List<Message>> sets = new ArrayList<>();
      for (Message b : keys) {
        String label = labels.get(b);
        List<Message> rb = new ArrayList<>();
        if (label != null) {
          for (Message x : kSorted) {
            if (!forget.contains(x) && Terms.format(x).equals(Terms.format(b))
                && label.equals(labels.get(x))) {
              rb.add(x);
            }
          }
        }
        if (rb.isEmpty()) {
          continue outer;
        }
        sets.add(rb);
      }
      for (Map<Message, Message> c : product(keys, sets)) {
        Message v = Terms.substitute(m2, c);
        if (v.equals(m2)) {
          self = true;
        }
        variants.add(v);
        if (c.entrySet().stream().noneMatch(e -> e.getKey().equals(e.getValue()))) {
          proper.add(v);
        }
      }
    }
    return variants.isEmpty() ? new Outcome("SKIP", Set.of(), false, Set.of())
        : new Outcome("REPLACE", variants, self, proper);
  }

  private static List<Map<Message, Message>> product(List<Message> keys, List<List<Message>> sets) {
    List<Map<Message, Message>> out = new ArrayList<>();
    out.add(new LinkedHashMap<>());
    for (int i = 0; i < keys.size(); i++) {
      List<Map<Message, Message>> next = new ArrayList<>();
      for (Map<Message, Message> partial : out) {
        for (Message x : sets.get(i)) {
          Map<Message, Message> m = new LinkedHashMap<>(partial);
          m.put(keys.get(i), x);
          next.add(m);
        }
      }
      out = next;
      if (out.size() > 4096) {
        break;
      }
    }
    return out;
  }
}
