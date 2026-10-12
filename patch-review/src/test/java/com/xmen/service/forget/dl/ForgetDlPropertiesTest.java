package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seeded property tests over random scenarios. The oracle is the paper's template semantics
 * (Lemma 1 generalised): Usable(t) iff some π ∈ Π_t has no lapsed rule, is not a forgotten
 * procedure, and every blocked hypothesis has a present associated cue.
 */
@DisplayName("Properties (seeded random scenarios)")
class ForgetDlPropertiesTest {

  private static final int RUNS = 400;
  private static final List<String> RULES = List.of("pair", "proj", "senc", "sdec", "aenc", "adec");

  record Scenario(Set<Message> k, Set<Message> forget, BlockingMode mode,
      ProceduralState proc, CueModel cues, List<Message> targets) {}

  static Scenario random(Random r) {
    List<Message> atoms = List.of(a("x0"), a("x1"), a("x2"), a("k0"), a("k1"), a("sk"));
    Set<Message> k = new LinkedHashSet<>();
    for (Message at : atoms) {
      if (r.nextInt(3) > 0) {
        k.add(at);
      }
    }
    int extra = 1 + r.nextInt(3);
    for (int i = 0; i < extra; i++) {
      Message x = atoms.get(r.nextInt(3));
      Message y = atoms.get(r.nextInt(atoms.size()));
      switch (r.nextInt(3)) {
        case 0 -> k.add(pair(x, y));
        case 1 -> k.add(senc(x, atoms.get(3 + r.nextInt(2))));
        default -> k.add(aenc(x, atoms.get(5)));
      }
    }
    List<Message> kl = Terms.sorted(k);
    Set<Message> forget = new LinkedHashSet<>();
    for (Message m : kl) {
      if (r.nextInt(3) == 0) {
        forget.add(m);
      }
    }
    BlockingMode mode = BlockingMode.values()[r.nextInt(3)];
    List<Message> targets = new ArrayList<>(List.of(atoms.get(r.nextInt(3)),
        pair(atoms.get(r.nextInt(3)), atoms.get(r.nextInt(3))),
        senc(atoms.get(r.nextInt(3)), atoms.get(3))));
    Set<String> rules = new LinkedHashSet<>();
    for (String rule : RULES) {
      if (r.nextInt(6) == 0) {
        rules.add(rule);
      }
    }
    Set<String> procs = new LinkedHashSet<>();
    NormalDerivations nd = new NormalDerivations();
    for (Message t : targets) {
      for (Derivation d : nd.enumerate(t, k).derivations()) {
        if (r.nextInt(5) == 0) {
          procs.add(NormalDerivations.procedureId(d));
        }
      }
    }
    Set<String> cueIds = Set.of("c0", "c1", "c2");
    Set<Association> assoc = new LinkedHashSet<>();
    for (String c : cueIds) {
      for (Message m : kl) {
        if (r.nextInt(4) == 0) {
          assoc.add(new Association(c, m));
        }
      }
    }
    Set<String> present = new LinkedHashSet<>();
    for (String c : cueIds) {
      if (r.nextBoolean()) {
        present.add(c);
      }
    }
    return new Scenario(k, forget, mode, new ProceduralState(rules, procs),
        new CueModel(cueIds, assoc, present), targets);
  }

  private static boolean oracleVia(Derivation d, Scenario s, ForgetBlocking fb) {
    if (NormalDerivations.rules(d).stream().anyMatch(s.proc().forgottenRules()::contains)) {
      return false;
    }
    if (s.proc().forgottenProcedures().contains(NormalDerivations.procedureId(d))) {
      return false;
    }
    for (Message h : fb.blocked(NormalDerivations.hypotheses(d))) {
      boolean cued = s.cues().associations().stream()
          .anyMatch(x -> x.message().equals(h) && s.cues().present().contains(x.cue()));
      if (!cued) {
        return false;
      }
    }
    return true;
  }

  @Test
  @DisplayName("DL tags equal the template oracle for every Via and Usable atom")
  void dlMatchesTemplateOracle() {
    Random r = new Random(20261002L);
    int checked = 0;
    for (int i = 0; i < RUNS; i++) {
      Scenario s = random(r);
      DlFixtures.Compiled c = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(), s.cues(),
          s.targets().toArray(Message[]::new));
      ForgetBlocking fb = new ForgetBlocking(s.forget(), s.mode(), Set.of());
      for (Message t : new LinkedHashSet<>(s.targets())) {
        boolean any = false;
        List<ForgetDlCompiler.DerivationEntry> es = c.compiled().entries().get(t);
        for (int j = 0; j < es.size(); j++) {
          boolean expected = oracleVia(es.get(j).derivation(), s, fb);
          any |= expected;
          assertEquals(expected ? "+∂" : "-∂", c.via(t, j), "run " + i + " " + es.get(j));
          checked++;
        }
        assertEquals(any ? "+∂" : "-∂", c.usable(t), "run " + i + " target " + t);
      }
    }
    assertTrue(checked > RUNS, "non-trivial coverage: " + checked);
  }

  @Test
  @DisplayName("without procedural lapses or cues, DL agrees with Algorithm 1 line 12")
  void agreesWithAlgorithm1() {
    Random r = new Random(7L);
    for (int i = 0; i < RUNS; i++) {
      Scenario s = random(r);
      DlFixtures.Compiled c = DlFixtures.compile(s.k(), s.forget(), s.mode(),
          ProceduralState.EMPTY, CueModel.NONE, s.targets().toArray(Message[]::new));
      ForgetBlocking fb = new ForgetBlocking(s.forget(), s.mode(), Set.of());
      NormalDerivations nd = new NormalDerivations();
      for (Message t : new LinkedHashSet<>(s.targets())) {
        boolean alg1 = nd.enumerate(t, s.k()).derivations().stream()
            .anyMatch(d -> fb.blocked(NormalDerivations.hypotheses(d)).isEmpty());
        assertEquals(alg1 ? "+∂" : "-∂", c.usable(t), "run " + i);
      }
    }
  }

  @Test
  @DisplayName("§7.7: joint compilation over T gives the same tags as separate compilation")
  void jointEqualsSeparate() {
    Random r = new Random(11L);
    for (int i = 0; i < RUNS; i++) {
      Scenario s = random(r);
      DlFixtures.Compiled joint = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(),
          s.cues(), s.targets().toArray(Message[]::new));
      for (Message t : new LinkedHashSet<>(s.targets())) {
        DlFixtures.Compiled sep = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(),
            s.cues(), t);
        assertEquals(sep.usable(t), joint.usable(t), "run " + i);
        for (int j = 0; j < sep.compiled().entries().get(t).size(); j++) {
          assertEquals(sep.via(t, j), joint.via(t, j));
        }
      }
    }
  }

  @Test
  @DisplayName("§7.5: an irrelevant present cue is conservative for Via/Usable")
  void irrelevantCueIsConservative() {
    Random r = new Random(13L);
    for (int i = 0; i < RUNS; i++) {
      Scenario s = random(r);
      DlFixtures.Compiled base = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(),
          s.cues(), s.targets().toArray(Message[]::new));
      Set<Message> lapsed = base.compiled().lapsedMessages();
      Message unblocked = s.k().stream().filter(m -> !lapsed.contains(m)).findFirst().orElse(null);
      if (unblocked == null) {
        continue;
      }
      Set<String> cues = new LinkedHashSet<>(s.cues().cues());
      cues.add("irrelevant");
      Set<Association> assoc = new LinkedHashSet<>(s.cues().associations());
      assoc.add(new Association("irrelevant", unblocked));
      Set<String> present = new LinkedHashSet<>(s.cues().present());
      present.add("irrelevant");
      DlFixtures.Compiled more = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(),
          new CueModel(cues, assoc, present), s.targets().toArray(Message[]::new));
      for (Message t : new LinkedHashSet<>(s.targets())) {
        assertEquals(base.usable(t), more.usable(t));
      }
    }
  }
}
