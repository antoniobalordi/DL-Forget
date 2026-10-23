package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * PRELIMINARY model of adversarial cues (not implemented in X-Men 2.0). It extends the theory
 * produced by the unmodified ForgetDlCompiler with
 * <ul>
 *   <li>facts AdvCue(c,h) for present adversarial cues c interfering with h (Int ⊆ Cue × K);
 *   <li>interference rules ι: AdvCue(c,h) ⇒ ¬Via(π,t) for every derivation using h (defeasible
 *       rules, so they create genuine two-sided conflicts with d_π and ρ);
 *   <li>reinstaters ρ for genuine cues associated with interfered hypotheses, and superiority
 *       between ρ and ι following a salience order ≻ on cues;
 *   <li>an in-logic decision layer s_t: Usable(t) ⇒ Send(t), p_t': Usable(m2) ⇒ ¬Send(t'),
 *       p_t' > s_t', which coincides with Algorithm 2's decision on the base fragment.
 * </ul>
 * Evaluated with the production reasoner (ambiguity blocking) and the research prototype of
 * ambiguity propagation.
 */
public final class ResearchAdversarialCues {

  record Adv(Set<Association> interference, Set<String> presentAdv, Map<String, Set<String>> above) {
    boolean higher(String x, String y) {
      return above.getOrDefault(x, Set.of()).contains(y);
    }
  }

  static DefeasibleTheory extend(Set<Message> k, ForgetBlocking fb, ProceduralState proc,
      CueModel cues, Map<Message, List<Derivation>> byTarget, Message m2, Adv adv,
      Map<Message, Literal> sendOut) {
    return extend(k, fb, proc, cues, byTarget, m2, adv, sendOut, true);
  }

  /** @param keepDefeaterPriority keep the pairs ω > d of the base compilation (inert for ∂). */
  static DefeasibleTheory extend(Set<Message> k, ForgetBlocking fb, ProceduralState proc,
      CueModel cues, Map<Message, List<Derivation>> byTarget, Message m2, Adv adv,
      Map<Message, Literal> sendOut, boolean keepDefeaterPriority) {
    ForgetDlCompiler.Compiled base = new ForgetDlCompiler().compile(k, fb, proc, cues, byTarget);
    DefeasibleTheory.Builder b = DefeasibleTheory.builder();
    DefeasibleTheory th = base.theory();
    th.atoms().forEach(b::declare);
    th.facts().forEach(b::fact);
    th.rules().forEach(b::rule);
    th.superiorityPairs().forEach(p -> {
      boolean defeaterOverBase = p[1].startsWith("d:");
      if (keepDefeaterPriority || !defeaterOverBase) {
        b.superior(p[0], p[1]);
      }
    });
    Set<Literal> facts = new LinkedHashSet<>(th.facts());
    for (Association x : adv.interference()) {
      if (adv.presentAdv().contains(x.cue())) {
        Literal f = Literal.pos("AdvCue(" + x.cue() + "," + Terms.identity(x.message()) + ")");
        b.fact(f);
        facts.add(f);
      }
    }
    for (Map.Entry<Message, List<ForgetDlCompiler.DerivationEntry>> e : base.entries().entrySet()) {
      for (ForgetDlCompiler.DerivationEntry d : e.getValue()) {
        for (Association x : adv.interference()) {
          Message h = x.message();
          if (!d.hypotheses().contains(h)) {
            continue;
          }
          String iota = "iota:" + d.procedureId() + ":" + Terms.identity(h) + ":" + x.cue();
          b.defeasible(iota, d.via().complement(),
              Literal.pos("AdvCue(" + x.cue() + "," + Terms.identity(h) + ")"));
          for (Association g : cues.associations()) {
            if (!g.message().equals(h)) {
              continue;
            }
            String rho = "rho:" + d.procedureId() + ":" + Terms.identity(h) + ":" + Terms.field(g.cue());
            Literal cc = ForgetDlCompiler.contextCue(g.cue(), h);
            if (!th.rules().stream().anyMatch(r -> r.label().equals(rho)) && !builderHas(b, rho)) {
              b.defeasible(rho, d.via(), cc); // genuine cue confirming an interfered value
            }
            if (cues.present().contains(g.cue()) && !facts.contains(cc)) {
              b.fact(cc);
              facts.add(cc);
            }
            if (adv.higher(g.cue(), x.cue())) {
              b.superior(rho, iota);
            } else if (adv.higher(x.cue(), g.cue())) {
              b.superior(iota, rho);
            }
          }
        }
      }
    }
    for (Message t : base.usable().keySet()) {
      Literal send = Literal.pos("Send(" + Terms.identity(t) + ")");
      sendOut.put(t, send);
      b.defeasible("s:" + Terms.identity(t), send, base.usable().get(t));
      if (!t.equals(m2)) {
        b.defeasible("p:" + Terms.identity(t), send.complement(), base.usable().get(m2));
        b.superior("p:" + Terms.identity(t), "s:" + Terms.identity(t));
      }
    }
    return b.build();
  }

  private static boolean builderHas(DefeasibleTheory.Builder b, String label) {
    return b.hasRule(label);
  }

  static String decision(Conclusions c, Map<Message, Literal> send, Message m2) {
    if (c.provable(send.get(m2))) {
      return "PRESERVE";
    }
    List<String> v = new ArrayList<>();
    send.forEach((t, l) -> {
      if (!t.equals(m2) && c.provable(l)) {
        v.add(Terms.canonical(t));
      }
    });
    if (!v.isEmpty()) {
      return "REPLACE " + v;
    }
    boolean undecided = send.values().stream().anyMatch(l -> !c.decided(l));
    return undecided ? "UNDECIDED" : "SKIP";
  }

  public static void main(String[] args) {
    Message m3 = ResearchRunningExample.M3;
    Message pw1 = ResearchRunningExample.PW1, pw2 = ResearchRunningExample.PW2;
    Set<Message> k = ResearchRunningExample.knowledge();
    k.add(ResearchRunningExample.C);
    Message m3v = Terms.substitute(m3, Map.of(pw1, pw2));
    NormalDerivations nd = new NormalDerivations();
    Map<Message, List<Derivation>> byTarget = new LinkedHashMap<>();
    byTarget.put(m3, nd.enumerate(m3, k).derivations());
    byTarget.put(m3v, nd.enumerate(m3v, k).derivations());

    Set<String> cueIds = Set.of("hint", "banner");
    Set<Association> assoc = Set.of(new Association("hint", pw1));
    Set<Association> inter = Set.of(new Association("banner", pw1));
    record Sc(String id, String text, Set<Message> forget, Set<String> pres, Set<String> adv,
        Map<String, Set<String>> above) {}
    List<Sc> sc = List.of(
        new Sc("A0", "no lapse, no cue", Set.of(), Set.of(), Set.of(), Map.of()),
        new Sc("A1", "no lapse, adversarial banner", Set.of(), Set.of(), Set.of("banner"), Map.of()),
        new Sc("A2", "no lapse, banner + hint, hint > banner", Set.of(), Set.of("hint"), Set.of("banner"),
            Map.of("hint", Set.of("banner"))),
        new Sc("A3", "no lapse, banner + hint, no order", Set.of(), Set.of("hint"), Set.of("banner"), Map.of()),
        new Sc("A4", "Forget(pw1), hint, no banner", Set.of(pw1), Set.of("hint"), Set.of(), Map.of()),
        new Sc("A5", "Forget(pw1), banner + hint, no order", Set.of(pw1), Set.of("hint"), Set.of("banner"), Map.of()),
        new Sc("A6", "Forget(pw1), banner + hint, banner > hint", Set.of(pw1), Set.of("hint"), Set.of("banner"),
            Map.of("banner", Set.of("hint"))),
        new Sc("A7", "Forget(pw1), banner + hint, hint > banner", Set.of(pw1), Set.of("hint"), Set.of("banner"),
            Map.of("hint", Set.of("banner"))));

    AmbiguityBlockingReasoner ab = new AmbiguityBlockingReasoner();
    ResearchVariantReasoners.AmbiguityPropagating ap = new ResearchVariantReasoners.AmbiguityPropagating();
    System.out.println("# Preliminary adversarial-cue model on the running example");
    System.out.println("T = {m3, m3[pw1->pw2]}; Int = {(banner, pw1)}; Assoc = {(hint, pw1)}\n");
    System.out.println("| id | scenario | Usable(m3) AB | decision AB | Usable(m3) AP (+∫?) | decision AP |");
    System.out.println("|---|---|---|---|---|---|");
    for (Sc s : sc) {
      ForgetBlocking fb = new ForgetBlocking(s.forget(), BlockingMode.CASE1_WEAK, Set.of());
      CueModel cues = new CueModel(cueIds, assoc, s.pres());
      Map<Message, Literal> send = new LinkedHashMap<>();
      DefeasibleTheory th = extend(k, fb, ProceduralState.EMPTY, cues, byTarget, m3,
          new Adv(inter, s.adv(), s.above()), send);
      Conclusions cab = ab.reason(th);
      Conclusions cap = ap.reason(th);
      Set<Literal>[] sup = ap.support();
      Literal u = Literal.pos("Usable(" + Terms.identity(m3) + ")");
      System.out.printf("| %s | %s | %s | %s | %s (%s) | %s |%n", s.id(), s.text(), cab.partialTag(u),
          decision(cab, send, m3), cap.partialTag(u), sup[0].contains(u) ? "+∫" : sup[1].contains(u) ? "-∫" : "?",
          decision(cap, send, m3));
    }

    // Role of the pairs ω > d under ambiguity propagation (A5, A6 without those pairs).
    System.out.println("\nWithout the pairs ω > d (inert for ∂):");
    for (Sc s : sc) {
      if (!s.id().equals("A5") && !s.id().equals("A6")) {
        continue;
      }
      ForgetBlocking fb = new ForgetBlocking(s.forget(), BlockingMode.CASE1_WEAK, Set.of());
      CueModel cues = new CueModel(cueIds, assoc, s.pres());
      Map<Message, Literal> send = new LinkedHashMap<>();
      DefeasibleTheory th = extend(k, fb, ProceduralState.EMPTY, cues, byTarget, m3,
          new Adv(inter, s.adv(), s.above()), send, false);
      System.out.printf("%s: decision AB %s, decision AP %s%n", s.id(),
          decision(ab.reason(th), send, m3), decision(ap.reason(th), send, m3));
    }

    // Conservativity check of the in-logic decision layer on the base fragment (no adversarial
    // cues): AB decision from Send tags equals the engine decision recomputed from Usable tags.
    Random r = new Random(77L);
    int agree = 0, total = 0;
    for (int i = 0; i < 3000; i++) {
      ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(r);
      Message m2 = s.targets().get(0);
      Map<Message, List<Derivation>> bt = new LinkedHashMap<>();
      for (Message t : new LinkedHashSet<>(s.targets())) {
        bt.put(t, nd.enumerate(t, s.k()).derivations());
      }
      ForgetBlocking fb = new ForgetBlocking(s.forget(), s.mode(), Set.of());
      Map<Message, Literal> send = new LinkedHashMap<>();
      DefeasibleTheory th = extend(s.k(), fb, s.proc(), s.cues(), bt, m2,
          new Adv(Set.of(), Set.of(), Map.of()), send);
      Conclusions c = ab.reason(th);
      ForgetDlCompiler.Compiled base = new ForgetDlCompiler().compile(s.k(), fb, s.proc(), s.cues(), bt);
      Conclusions cb = ab.reason(base.theory());
      String expected;
      if (cb.provable(base.usable().get(m2))) {
        expected = "PRESERVE";
      } else {
        List<String> v = new ArrayList<>();
        for (Message t : base.usable().keySet()) {
          if (!t.equals(m2) && cb.provable(base.usable().get(t))) {
            v.add(Terms.canonical(t));
          }
        }
        expected = v.isEmpty() ? "SKIP" : "REPLACE " + v;
      }
      total++;
      if (expected.equals(decision(c, send, m2))) {
        agree++;
      }
    }
    System.out.println("\nDecision layer on the base fragment: in-logic decision = Alg. 2 decision on "
        + agree + "/" + total + " random theories (targets as candidates)");
  }

  @SuppressWarnings("unused")
  private static Message pair(Message a, Message b) {
    return new Pair(a, b);
  }
}
