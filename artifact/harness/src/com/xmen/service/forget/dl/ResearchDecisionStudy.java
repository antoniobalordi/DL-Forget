package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Decision-level comparison on random transitions: Forget-DL (unmodified engine) versus the
 * published Algorithm 1 evaluated on the same derivation basis (ResearchAlg1), and versus the
 * engine run with the two research variants of the proof theory.
 *
 * <p>Two configurations: FULL (random procedural lapses and cues) and CLASSICAL (no procedural
 * lapses, no cues), where Prop. 4 predicts that DL and Alg. 1 may differ only through the
 * admissibility of replacement variants.
 */
public final class ResearchDecisionStudy {

  public static void main(String[] args) {
    int runs = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
    for (String config : List.of("CLASSICAL", "FULL")) {
      Random r = new Random(2027L);
      ForgetDlEngine prod = new ForgetDlEngine();
      ForgetDlEngine ntd = new ForgetDlEngine(new ResearchVariantReasoners.NoTeamDefeat(), Set.of(),
          ForgetDlEngine.Limits.DEFAULT);
      ForgetDlEngine ap = new ForgetDlEngine(new ResearchVariantReasoners.AmbiguityPropagating(),
          Set.of(), ForgetDlEngine.Limits.DEFAULT);
      Map<String, Integer> pairs = new TreeMap<>();
      Map<String, Integer> causes = new TreeMap<>();
      int ntdDiff = 0, apDiff = 0, valid = 0, prop8Checked = 0, prop8Fail = 0;
      for (int i = 0; i < runs; i++) {
        ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(r);
        ProceduralState proc = config.equals("FULL") ? s.proc() : ProceduralState.EMPTY;
        CueModel cues = config.equals("FULL") ? s.cues() : CueModel.NONE;
        Message m2 = s.targets().get(r.nextInt(s.targets().size()));
        Map<Message, String> labels = new LinkedHashMap<>();
        for (Message x : Terms.sorted(s.k())) {
          if (r.nextInt(4) > 0) {
            labels.put(x, r.nextBoolean() ? "L0" : "L1");
          }
        }
        TransitionInput in = TransitionInput.builder(new HumanState(s.k(), s.forget(), proc))
            .send(m2).cues(cues).closure(s.mode()).labels(labels).build();
        ForgetDlResult d = prod.run(in);
        if (d.status() != ForgetDlResult.Status.OK) {
          continue;
        }
        valid++;
        ResearchAlg1.Outcome a = ResearchAlg1.decide(d.after().knowledge(), d.after().forget(),
            s.mode(), labels, m2);
        String key = d.decision() + " / " + a.decision();
        pairs.merge(key, 1, Integer::sum);
        if (!sameDecision(d, a, m2)) {
          causes.merge(cause(d, a, m2, proc, cues) + " [" + s.mode() + "]", 1, Integer::sum);
        }
        if (config.equals("CLASSICAL") && d.decision() != Decision.UNKNOWN_COVERAGE) {
          // Prop. 8(b): Alg. 2 emits exactly the proper Alg. 1 variants (no self-replacement,
          // m2[C] != m2) that have a derivation without blocked hypotheses.
          prop8Checked++;
          ForgetBlocking fb = new ForgetBlocking(d.after().forget(), s.mode(), Set.of());
          NormalDerivations nd = new NormalDerivations();
          Set<Message> expected = new LinkedHashSet<>();
          if (a.decision().equals("REPLACE")) {
            for (Message v : a.properVariants()) {
              if (!v.equals(m2) && nd.enumerate(v, d.after().knowledge()).derivations().stream()
                  .anyMatch(p -> fb.blocked(NormalDerivations.hypotheses(p)).isEmpty())) {
                expected.add(v);
              }
            }
          }
          Set<Message> got = new LinkedHashSet<>();
          d.replacements().forEach(c -> got.add(c.target()));
          boolean preserveAgrees = (d.decision() == Decision.PRESERVE) == a.decision().equals("PRESERVE");
          if (!preserveAgrees || !got.equals(expected)) {
            prop8Fail++;
          }
        }
        if (ntd.run(in).decision() != d.decision()) {
          ntdDiff++;
        }
        if (ap.run(in).decision() != d.decision()) {
          apDiff++;
        }
      }
      System.out.println("## Configuration " + config + " (runs=" + runs + ", valid=" + valid + ")");
      System.out.println("| Forget-DL / Alg. 1 | count |\n|---|---|");
      pairs.forEach((k, v) -> System.out.println("| " + k + " | " + v + " |"));
      System.out.println("\nDifferences classified:");
      causes.forEach((k, v) -> System.out.println("- " + k + ": " + v));
      if (config.equals("CLASSICAL")) {
        System.out.println("Prop. 8 check (Preserve test identical; Replace set = proper usable Alg. 1 "
            + "variants): checked=" + prop8Checked + " failures=" + prop8Fail);
      }
      System.out.println("Engine decision differs with no-team-defeat reasoner: " + ntdDiff);
      System.out.println("Engine decision differs with ambiguity-propagating reasoner: " + apDiff + "\n");
    }
  }

  static boolean sameDecision(ForgetDlResult d, ResearchAlg1.Outcome a, Message m2) {
    if (!d.decision().name().equals(a.decision())) {
      return false;
    }
    if (d.decision() == Decision.REPLACE) {
      Set<Message> v = new LinkedHashSet<>();
      d.replacements().forEach(c -> v.add(c.target()));
      return v.equals(a.variants());
    }
    return true;
  }

  static String cause(ForgetDlResult d, ResearchAlg1.Outcome a, Message m2, ProceduralState proc,
      CueModel cues) {
    if (d.decision() == Decision.UNKNOWN_COVERAGE) {
      return "DL reports UNKNOWN_COVERAGE (procedure lapse with cyclic proofs, or truncation)";
    }
    boolean procLapse = !proc.forgottenRules().isEmpty() || !proc.forgottenProcedures().isEmpty();
    boolean cue = !cues.present().isEmpty();
    if (d.decision() == Decision.PRESERVE && !a.decision().equals("PRESERVE")) {
      return "cue reinstates the intended send (DL Preserve, Alg.1 " + a.decision() + ")";
    }
    if (a.decision().equals("PRESERVE") && d.decision() != Decision.PRESERVE) {
      return "procedural lapse blocks every derivation of the intended send (DL " + d.decision()
          + ", Alg.1 PRESERVE)";
    }
    Set<Message> v = new LinkedHashSet<>();
    d.replacements().forEach(c -> v.add(c.target()));
    Set<Message> av = new LinkedHashSet<>(a.variants());
    if (a.selfReplacement()) {
      return "Alg.1 emits m2 itself as a variant (m2[C] = m2: the blocked hypothesis does not occur in m2, or is replaced by itself under Case 2/3)";
    }
    if (av.containsAll(v)) {
      return procLapse ? "Alg.1 variant blocked by a lapse (procedural or declarative); DL emits a subset"
          : "Alg.1 variant has no derivation free of blocked hypotheses; DL emits a subset";
    }
    return (cue ? "cue-dependent variant set" : "other") + " (DL " + d.decision() + " " + v.size()
        + " vs Alg.1 " + a.decision() + " " + av.size() + ")";
  }
}
