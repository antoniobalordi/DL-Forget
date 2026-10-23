package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Differential check of ResearchClosureDecider against the production pipeline (enumeration +
 * compilation + DL(∂) reasoning) on random scenarios without exact-procedure lapses, plus a
 * timing comparison on the exponential family F4 beyond the enumeration cap.
 */
public final class ResearchClosureCheck {

  public static void main(String[] args) {
    int seeds = 5, runs = 2000;
    ResearchClosureDecider closure = new ResearchClosureDecider(Set.of());
    NormalDerivations nd = new NormalDerivations();
    long targets = 0, agree = 0, disagree = 0, skippedTruncated = 0, positives = 0;
    List<String> examples = new ArrayList<>();
    for (int seed = 1; seed <= seeds; seed++) {
      Random r = new Random(5000L + seed);
      for (int i = 0; i < runs; i++) {
        ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(r);
        ProceduralState proc = new ProceduralState(s.proc().forgottenRules(), Set.of());
        DlFixtures.Compiled c = DlFixtures.compile(s.k(), s.forget(), s.mode(), proc, s.cues(),
            s.targets().toArray(Message[]::new));
        ForgetBlocking fb = new ForgetBlocking(s.forget(), s.mode(), Set.of());
        for (Message t : new LinkedHashSet<>(s.targets())) {
          if (nd.enumerate(t, s.k()).coverage() != NormalDerivations.Coverage.COMPLETE) {
            skippedTruncated++;
            continue;
          }
          targets++;
          boolean dl = c.usable(t).equals("+∂");
          boolean cl = closure.usable(t, s.k(), fb, proc.forgottenRules(), s.cues());
          positives += dl ? 1 : 0;
          if (dl == cl) {
            agree++;
          } else {
            disagree++;
            if (examples.size() < 5) {
              examples.add(Terms.canonical(t) + " K=" + Terms.sorted(s.k()).stream().map(Terms::canonical).toList()
                  + " F=" + s.forget() + " mode=" + s.mode() + " R=" + proc.forgottenRules() + " dl=" + dl + " cl=" + cl);
            }
          }
        }
      }
    }
    // Cyclic knowledge of erratum E3: infinitely many normal derivations, finite basis.
    Message a = new Atom("a"), b = new Atom("b");
    Set<Message> kc = Set.of(fn("senc", a, b), fn("senc", b, a), a);
    ForgetBlocking none = new ForgetBlocking(Set.of(), com.xmen.service.forget.ForgetContext.BlockingMode.CASE1_WEAK, Set.of());
    boolean cyc = closure.usable(b, kc, none, Set.of(), CueModel.NONE);
    System.out.println("## Closure decider vs production pipeline (no exact-procedure lapses)");
    System.out.printf("targets=%d agree=%d disagree=%d (usable=%d) skipped(truncated)=%d%n",
        targets, agree, disagree, positives, skippedTruncated);
    examples.forEach(e -> System.out.println("  DIFF " + e));
    System.out.println("E3 cyclic knowledge, target b usable: " + cyc);

    System.out.println("\n## F4 family beyond the enumeration cap: engine vs closure decider");
    System.out.println("| d | variant | engine decision | engine ms | closure usable(m2) | closure ms |");
    System.out.println("|---|---|---|---|---|---|");
    ForgetDlEngine engine = new ForgetDlEngine();
    for (String v : List.of("F4+", "F4-")) {
      for (int d : new int[] {8, 10, 12, 16, 20, 32, 64}) {
        Set<Message> k = new LinkedHashSet<>();
        List<Message> comps = new ArrayList<>();
        for (int i = 0; i < d; i++) {
          Message xi = new Atom("x" + i), ka = new Atom("ka" + i), kb = new Atom("kb" + i);
          k.addAll(List.of(ka, kb, fn("senc", xi, ka), fn("senc", xi, kb)));
          comps.add(xi);
        }
        Message target = comps.get(d - 1);
        for (int i = d - 2; i >= 0; i--) {
          target = new Pair(comps.get(i), target);
        }
        Set<Message> forget = v.equals("F4+") ? Set.of(new Atom("ka0"))
            : Set.of(new Atom("ka0"), new Atom("kb0"));
        var st = new ForgetDlModel.HumanState(k, forget, ProceduralState.EMPTY);
        var in = ForgetDlModel.TransitionInput.builder(st).send(target).build();
        ForgetBlocking fb = new ForgetBlocking(forget, com.xmen.service.forget.ForgetContext.BlockingMode.CASE1_WEAK, Set.of());
        String dec = "";
        boolean u = false;
        double[] te = new double[7], tc = new double[7];
        for (int rep = 0; rep < 10; rep++) {
          long t0 = System.nanoTime();
          dec = engine.run(in).decision().toString();
          long t1 = System.nanoTime();
          u = closure.usable(target, k, fb, Set.of(), CueModel.NONE);
          long t2 = System.nanoTime();
          if (rep >= 3) {
            te[rep - 3] = (t1 - t0) / 1e6;
            tc[rep - 3] = (t2 - t1) / 1e6;
          }
        }
        System.out.printf("| %d | %s | %s | %.2f | %s | %.3f |%n", d, v, dec,
            ResearchScalability.med(te), u, ResearchScalability.med(tc));
      }
    }
  }

  static Message fn(String f, Message... args) {
    return new PredictiveFunction(f, List.of(args));
  }
}
