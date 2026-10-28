package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scaling behaviour of the implemented decision procedure on four synthetic families. For each
 * instance it reports |Π| (derivations of all candidates), |T|, the size of the compiled theory
 * (facts + rules + superiority pairs), the decision, and the median wall time (ms) of the three
 * phases: derivation enumeration, compilation, DL(∂) reasoning, plus the whole engine run.
 * Times are medians of 9 runs after 5 warm-up runs, single thread.
 */
public final class ResearchScalability {

  static Message a(String v) {
    return new Atom(v);
  }

  static Message fn(String f, Message... args) {
    return new PredictiveFunction(f, List.of(args));
  }

  static Message senc(Message m, Message k) {
    return fn("senc", m, k);
  }

  record Instance(String family, int param, TransitionInput input) {}

  public static void main(String[] args) {
    List<Instance> all = new ArrayList<>();
    // F1: n alternative keys for one ciphertext each: K = {senc(x,k_i), k_i}; target <x,y>.
    for (int n : new int[] {1, 2, 4, 8, 16, 32, 64, 128, 256}) {
      Set<Message> k = new LinkedHashSet<>();
      Message x = a("x"), y = a("y");
      k.add(y);
      for (int i = 0; i < n; i++) {
        k.add(senc(x, a("k" + i)));
        k.add(a("k" + i));
      }
      HumanState st = new HumanState(k, Set.of(a("k0")), ProceduralState.EMPTY);
      all.add(new Instance("F1 alternative derivations", n,
          TransitionInput.builder(st).send(new Pair(x, y)).build()));
    }
    // F2: r same-label substitutes for a forgotten password: |T| = r + 1.
    for (int r : new int[] {1, 2, 4, 8, 16, 32, 64, 128, 255}) {
      Set<Message> k = new LinkedHashSet<>(List.of(a("H"), a("key")));
      Map<Message, String> labels = new LinkedHashMap<>();
      for (int i = 0; i <= r; i++) {
        k.add(a("pw" + i));
        labels.put(a("pw" + i), "password");
      }
      HumanState st = new HumanState(k, Set.of(a("pw0")), ProceduralState.EMPTY);
      all.add(new Instance("F2 replacement candidates", r, TransitionInput.builder(st)
          .send(senc(new Pair(a("H"), a("pw0")), a("key"))).labels(labels).build()));
    }
    // F3: c cues associated with the forgotten password, all present.
    for (int c : new int[] {1, 2, 4, 8, 16, 32, 64, 128, 256}) {
      Set<Message> k = new LinkedHashSet<>(List.of(a("H"), a("key"), a("pw0")));
      Set<String> cues = new LinkedHashSet<>();
      Set<Association> assoc = new LinkedHashSet<>();
      for (int i = 0; i < c; i++) {
        cues.add("c" + i);
        assoc.add(new Association("c" + i, a("pw0")));
      }
      HumanState st = new HumanState(k, Set.of(a("pw0")), ProceduralState.EMPTY);
      all.add(new Instance("F3 cues per blocked hypothesis", c, TransitionInput.builder(st)
          .send(senc(new Pair(a("H"), a("pw0")), a("key")))
          .cues(new CueModel(cues, assoc, cues)).build()));
    }
    // F4: tuple of d components, each obtainable by decrypting one of two ciphertexts:
    // 2^d derivations of the target; the enumeration cap (512) is reached at d = 10.
    // F4+ forgets ka0 (some derivation survives: Preserve, also under truncation, Prop. 6);
    // F4- forgets ka0 and kb0 (every derivation blocked: Skip with complete coverage,
    // UNKNOWN_COVERAGE once the enumeration is truncated, Prop. 7 and policy A11).
    for (String variant : List.of("F4+ exponential derivations, one key forgotten",
        "F4- exponential derivations, both keys of layer 0 forgotten")) {
      for (int d : new int[] {2, 4, 6, 8, 9, 10, 11, 12}) {
        Set<Message> k = new LinkedHashSet<>();
        List<Message> comps = new ArrayList<>();
        for (int i = 0; i < d; i++) {
          Message xi = a("x" + i), ka = a("ka" + i), kb = a("kb" + i);
          k.addAll(List.of(ka, kb, senc(xi, ka), senc(xi, kb)));
          comps.add(xi);
        }
        Message target = comps.get(d - 1);
        for (int i = d - 2; i >= 0; i--) {
          target = new Pair(comps.get(i), target);
        }
        Set<Message> forget = variant.startsWith("F4+") ? Set.of(a("ka0")) : Set.of(a("ka0"), a("kb0"));
        HumanState st = new HumanState(k, forget, ProceduralState.EMPTY);
        all.add(new Instance(variant, d, TransitionInput.builder(st).send(target).build()));
      }
    }

    ForgetDlEngine engine = new ForgetDlEngine();
    ForgetDlEngine wide = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(100_000, 50_000_000), 256));
    for (int n : new int[] {512, 1024, 2048, 4096}) {
      Set<Message> k = new LinkedHashSet<>();
      Message x = a("x"), y = a("y");
      k.add(y);
      for (int i = 0; i < n; i++) {
        k.add(senc(x, a("k" + i)));
        k.add(a("k" + i));
      }
      HumanState st = new HumanState(k, Set.of(a("k0")), ProceduralState.EMPTY);
      all.add(new Instance("F5 alternative derivations, raised limits", n,
          TransitionInput.builder(st).send(new Pair(x, y)).build()));
    }
    NormalDerivations nd = new NormalDerivations();
    ForgetDlCompiler compiler = new ForgetDlCompiler();
    AmbiguityBlockingReasoner reasoner = new AmbiguityBlockingReasoner();
    System.out.println("| family | n | decision | candidate coverage | |T| | |Π| | facts | rules | sup | |D| | "
        + "enum ms | compile ms | reason ms | engine ms |");
    System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|");
    for (Instance inst : all) {
      ForgetDlEngine eng = inst.family().startsWith("F5") ? wide : engine;
      NormalDerivations nd2 = inst.family().startsWith("F5")
          ? new NormalDerivations(Set.of(), new NormalDerivations.Limits(100_000, 50_000_000)) : nd;
      ForgetDlResult res = eng.run(inst.input());
      if (res.compiled() == null) {
        System.out.println("| " + inst.family() + " | " + inst.param() + " | " + res.decision() + " | "
            + res.candidateCoverage() + " | - | - | - | - | - | - | - | - | - | - |");
        continue;
      }
      Map<Message, List<Derivation>> byTarget = new LinkedHashMap<>();
      res.compiled().entries().forEach((t, es) ->
          byTarget.put(t, es.stream().map(ForgetDlCompiler.DerivationEntry::derivation).toList()));
      int pi = byTarget.values().stream().mapToInt(List::size).sum();
      Set<Message> kAfter = res.after().knowledge();
      ForgetBlocking fb = new ForgetBlocking(res.after().forget(), inst.input().closure(), Set.of());
      double[] tEnum = new double[9], tComp = new double[9], tReas = new double[9], tEng = new double[9];
      for (int rep = 0; rep < 14; rep++) {
        long t0 = System.nanoTime();
        for (Message t : byTarget.keySet()) {
          nd2.enumerate(t, kAfter);
        }
        long t1 = System.nanoTime();
        ForgetDlCompiler.Compiled c = compiler.compile(kAfter, fb, res.after().procedural(),
            inst.input().cues(), byTarget);
        long t2 = System.nanoTime();
        reasoner.reason(c.theory());
        long t3 = System.nanoTime();
        eng.run(inst.input());
        long t4 = System.nanoTime();
        if (rep >= 5) {
          tEnum[rep - 5] = (t1 - t0) / 1e6;
          tComp[rep - 5] = (t2 - t1) / 1e6;
          tReas[rep - 5] = (t3 - t2) / 1e6;
          tEng[rep - 5] = (t4 - t3) / 1e6;
        }
      }
      DefeasibleTheory th = res.compiled().theory();
      int f = th.facts().size(), r = th.rules().size(), s = th.superiorityPairs().size();
      System.out.printf("| %s | %d | %s | %s | %d | %d | %d | %d | %d | %d | %.2f | %.2f | %.2f | %.2f |%n",
          inst.family(), inst.param(), res.decision(), res.candidateCoverage(), res.candidates().size(), pi,
          f, r, s, f + r + s, med(tEnum), med(tComp), med(tReas), med(tEng));
    }
  }

  static double med(double[] x) {
    double[] y = x.clone();
    Arrays.sort(y);
    return y[y.length / 2];
  }
}
