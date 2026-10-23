package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sensitivity of the compiled Forget theories to the choice of DL proof theory.
 *
 * <p>For random Forget scenarios (the generator of ForgetDlPropertiesTest), the compiled theory
 * is evaluated with (i) the production reasoner (ambiguity blocking, team defeat), (ii) ambiguity
 * blocking without team defeat, (iii) ambiguity propagation with team defeat. Checked claims:
 * <ul>
 *   <li>C1 (Thm. 1): production tags = Boolean survival predicate (Def. 6) on Via and Usable.
 *   <li>C2 (Prop. variant-AP): ambiguity propagation assigns the same tags to every literal.
 *   <li>C3 (Prop. variant-NTD): without team defeat, +∂Via(π,t) iff no procedural blocker and
 *       at most one blocked hypothesis, which (if present) is cued; the two variants differ on
 *       Via exactly when ≥ 2 blocked hypotheses are all cued and no procedural blocker applies.
 * </ul>
 * Also evaluates random general theories (with conflicts) to confirm that the three variants are
 * genuinely different reasoners outside the compiled fragment, and the textbook ambiguity example.
 */
public final class ResearchVariantStudy {

  public static void main(String[] args) {
    int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 5;
    int runs = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
    AmbiguityBlockingReasoner ab = new AmbiguityBlockingReasoner();
    ResearchVariantReasoners.NoTeamDefeat ntd = new ResearchVariantReasoners.NoTeamDefeat();
    ResearchVariantReasoners.AmbiguityPropagating ap = new ResearchVariantReasoners.AmbiguityPropagating();

    long theories = 0, literals = 0, viaAtoms = 0, usableAtoms = 0;
    long c1Fail = 0, c2Fail = 0, c3Fail = 0, ntdDiffVia = 0, ntdDiffUsable = 0, predictedDiff = 0;
    long twoPlusCued = 0;
    for (int seed = 1; seed <= seeds; seed++) {
      Random r = new Random(1000L + seed);
      for (int i = 0; i < runs; i++) {
        ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(r);
        DlFixtures.Compiled c = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(), s.cues(),
            s.targets().toArray(Message[]::new));
        DefeasibleTheory th = c.compiled().theory();
        Conclusions cab = c.conclusions();
        Conclusions cntd = ntd.reason(th);
        Conclusions cap = ap.reason(th);
        theories++;
        for (String atom : th.atoms()) {
          for (Literal l : List.of(Literal.pos(atom), Literal.neg(atom))) {
            literals++;
            if (!cab.partialTag(l).equals(cap.partialTag(l))
                || cab.plusDelta().contains(l) != cap.plusDelta().contains(l)) {
              c2Fail++;
            }
          }
        }
        ForgetBlocking fb = new ForgetBlocking(s.forget(), s.mode(), Set.of());
        for (Message t : new LinkedHashSet<>(s.targets())) {
          boolean anyAb = false, anyNtd = false;
          for (ForgetDlCompiler.DerivationEntry e : c.compiled().entries().get(t)) {
            viaAtoms++;
            Derivation d = e.derivation();
            boolean proc = NormalDerivations.rules(d).stream().anyMatch(s.proc().forgottenRules()::contains)
                || s.proc().forgottenProcedures().contains(NormalDerivations.procedureId(d));
            Set<Message> blocked = fb.blocked(NormalDerivations.hypotheses(d));
            int cued = 0;
            for (Message h : blocked) {
              boolean isCued = s.cues().associations().stream()
                  .anyMatch(x -> x.message().equals(h) && s.cues().present().contains(x.cue()));
              if (isCued) {
                cued++;
              }
            }
            boolean def6 = !proc && cued == blocked.size();
            boolean predNtd = !proc && (blocked.isEmpty() || (blocked.size() == 1 && cued == 1));
            if (!proc && blocked.size() >= 2 && cued == blocked.size()) {
              twoPlusCued++;
            }
            String tAb = cab.partialTag(e.via());
            String tNtd = cntd.partialTag(e.via());
            if (!tAb.equals(def6 ? "+∂" : "-∂")) {
              c1Fail++;
            }
            if (!tNtd.equals(predNtd ? "+∂" : "-∂")) {
              c3Fail++;
            }
            if (!tAb.equals(tNtd)) {
              ntdDiffVia++;
            }
            if (def6 != predNtd) {
              predictedDiff++;
            }
            anyAb |= def6;
            anyNtd |= predNtd;
          }
          usableAtoms++;
          Literal u = c.compiled().usable().get(t);
          if (!cab.partialTag(u).equals(anyAb ? "+∂" : "-∂")) {
            c1Fail++;
          }
          if (!cntd.partialTag(u).equals(anyNtd ? "+∂" : "-∂")) {
            c3Fail++;
          }
          if (!cab.partialTag(u).equals(cntd.partialTag(u))) {
            ntdDiffUsable++;
          }
        }
      }
    }
    System.out.println("## Compiled Forget theories");
    System.out.printf("seeds=%d runs/seed=%d theories=%d literals=%d Via=%d Usable=%d%n", seeds, runs,
        theories, literals, viaAtoms, usableAtoms);
    System.out.printf("C1 Thm1 (production tags = Def.6) failures=%d%n", c1Fail);
    System.out.printf("C2 ambiguity propagation = blocking on every literal: failures=%d%n", c2Fail);
    System.out.printf("C3 no-team-defeat characterisation failures=%d%n", c3Fail);
    System.out.printf("   Via atoms with >=2 blocked hypotheses, all cued, no procedural blocker: %d%n", twoPlusCued);
    System.out.printf("   Via tags differing (team vs no team defeat)=%d (predicted %d); Usable tags differing=%d%n",
        ntdDiffVia, predictedDiff, ntdDiffUsable);

    // General random theories: the three reasoners are different logics outside the fragment.
    Random g = new Random(42L);
    int n = 3000, diffAp = 0, diffNtd = 0, lits = 0, abIncoherent = 0;
    for (int i = 0; i < n; i++) {
      DefeasibleTheory th = randomGeneral(g);
      Conclusions x = ab.reason(th), y = ap.reason(th), z = ntd.reason(th);
      for (String atom : th.atoms()) {
        for (Literal l : List.of(Literal.pos(atom), Literal.neg(atom))) {
          lits++;
          if (!x.partialTag(l).equals(y.partialTag(l))) {
            diffAp++;
          }
          if (!x.partialTag(l).equals(z.partialTag(l))) {
            diffNtd++;
          }
          if (x.partialTag(l).equals("both")) {
            abIncoherent++;
          }
        }
      }
    }
    System.out.println("\n## Random general theories (with conflicting defeasible rules)");
    System.out.printf("theories=%d literals=%d differ(AB vs AP)=%d differ(AB vs NTD)=%d AB-incoherent=%d%n",
        n, lits, diffAp, diffNtd, abIncoherent);

    // Textbook ambiguity example (Antoniou et al. 2000): r1: r=>p, r2: s=>~p, r3: p=>q, r4: =>~q, r3>r4.
    DefeasibleTheory tb = DefeasibleTheory.builder()
        .fact(Literal.pos("r")).fact(Literal.pos("s"))
        .defeasible("r1", Literal.pos("p"), Literal.pos("r"))
        .defeasible("r2", Literal.neg("p"), Literal.pos("s"))
        .defeasible("r3", Literal.pos("q"), Literal.pos("p"))
        .defeasible("r4", Literal.neg("q"))
        .superior("r3", "r4").build();
    Conclusions tab = ab.reason(tb), tap = ap.reason(tb);
    Set<Literal>[] sup = ap.support();
    System.out.println("\n## Textbook ambiguity example");
    for (Literal l : List.of(Literal.pos("p"), Literal.neg("p"), Literal.pos("q"), Literal.neg("q"))) {
      System.out.printf("%s  blocking:%s  propagation:%s  support:%s%n", l, tab.partialTag(l),
          tap.partialTag(l), sup[0].contains(l) ? "+∫" : sup[1].contains(l) ? "-∫" : "none");
    }
  }

  /** Acyclic random theory with genuine conflicts (mirrors the oracle generator's shape). */
  static DefeasibleTheory randomGeneral(Random g) {
    int n = 3 + g.nextInt(5);
    DefeasibleTheory.Builder b = DefeasibleTheory.builder();
    for (int i = 0; i < n; i++) {
      b.declare("p" + i);
    }
    for (int i = 0; i < g.nextInt(3); i++) {
      b.fact(g.nextInt(5) > 0 ? Literal.pos("p" + i) : Literal.neg("p" + i));
    }
    List<DefeasibleTheory.Rule> rules = new ArrayList<>();
    int nr = 2 + g.nextInt(8);
    for (int r = 0; r < nr; r++) {
      int h = 1 + g.nextInt(n - 1);
      Literal head = g.nextDouble() < .6 ? Literal.pos("p" + h) : Literal.neg("p" + h);
      List<Literal> body = new ArrayList<>();
      int nb = g.nextInt(Math.min(2, h) + 1);
      for (int j = 0; j < nb; j++) {
        int bi = g.nextInt(h);
        body.add(g.nextDouble() < .7 ? Literal.pos("p" + bi) : Literal.neg("p" + bi));
      }
      DefeasibleTheory.RuleKind kind = switch (g.nextInt(4)) {
        case 0 -> DefeasibleTheory.RuleKind.DEFEATER;
        default -> DefeasibleTheory.RuleKind.DEFEASIBLE;
      };
      DefeasibleTheory.Rule rule = new DefeasibleTheory.Rule("r" + r, kind, body, head);
      rules.add(rule);
      b.rule(rule);
    }
    double[] rank = new double[rules.size()];
    for (int i = 0; i < rank.length; i++) {
      rank[i] = g.nextDouble();
    }
    for (int i = 0; i < rules.size(); i++) {
      for (int j = i + 1; j < rules.size(); j++) {
        if (rules.get(i).head().equals(rules.get(j).head().complement()) && g.nextBoolean()) {
          int t = rank[i] < rank[j] ? i : j, s = t == i ? j : i;
          b.superior(rules.get(t).label(), rules.get(s).label());
        }
      }
    }
    return b.build();
  }

  @SuppressWarnings("unused")
  private static boolean cued(ForgetDlPropertiesTest.Scenario s, Message h) {
    for (Association a : s.cues().associations()) {
      if (a.message().equals(h) && s.cues().present().contains(a.cue())) {
        return true;
      }
    }
    return false;
  }
}
