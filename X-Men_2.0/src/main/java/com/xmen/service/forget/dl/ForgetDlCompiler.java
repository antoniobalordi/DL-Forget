package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code compile(H, i+1, T, K)} of Algorithm 2, line 20. One theory is compiled for the whole
 * candidate set T; atoms are indexed by target, so joint and separate compilation coincide.
 *
 * <p>Vocabulary (ground propositional atoms; the agent/state context is implicit in the theory):
 *
 * <pre>
 * Known(x)            fact, x ∈ K                                  (§7.1)
 * Lapsed(h)           fact, h ∈ ⋃_{t∈T} ⋃_{π∈Π_t} Blocked(Hyp_π)   (§7.1, §7.3)
 * ContextCue(c,h)     fact, h lapsed, c ∈ Pres, (c,h) ∈ Assoc      (§7.5)
 * LapsedRule(r)       fact, r ∈ R_H  (paper: Lapsed(Rule(r)))      (§7.6)
 * LapsedProc(π)       fact, π ∈ Π_H  (paper: Lapsed(Proc(π)))      (§7.6)
 * d:π      Known(h1..hk) ⇒ Via(π,t)
 * use:π    Via(π,t) → Usable(t)
 * w:π:h    Lapsed(h) ⇝ ¬Via(π,t)         for every h ∈ Hyp_π       w:π:h > d:π
 * rho:π:h:c ContextCue(c,h) ⇒ Via(π,t)   h ∈ Blocked(Hyp_π), (c,h) ∈ Assoc; rho:π:h:c > w:π:h
 * wr:π:r   LapsedRule(r) ⇝ ¬Via(π,t)     for every r ∈ Rules(π)    wr:π:r > d:π
 * wp:π     LapsedProc(π) ⇝ ¬Via(π,t)                               wp:π > d:π
 * </pre>
 *
 * Defeaters and reinstaters are emitted whether or not they are applicable; facts decide
 * applicability. Usable(t) is declared for every t ∈ T, including targets without derivations.
 */
public final class ForgetDlCompiler {

  /** Per-derivation compilation record used for decisions and the cognitive trace. */
  public record DerivationEntry(
      Message target,
      Derivation derivation,
      String procedureId,
      Set<Message> hypotheses,
      Set<Message> blockedHypotheses,
      Set<String> rules,
      Literal via,
      String baseLabel,
      Map<Message, String> declarativeDefeaters,
      Map<String, String> ruleDefeaters,
      String procedureDefeater,
      Map<String, Message> reinstaters) {}

  /** Compiled theory plus the index needed to read it back. */
  public record Compiled(
      DefeasibleTheory theory,
      Map<Message, Literal> usable,
      Map<Message, List<DerivationEntry>> entries,
      Set<Message> lapsedMessages) {}

  public Compiled compile(
      Set<Message> knowledge,
      ForgetBlocking blocking,
      ProceduralState procedural,
      CueModel cues,
      Map<Message, List<Derivation>> derivationsByTarget) {

    DefeasibleTheory.Builder b = DefeasibleTheory.builder();
    for (Message x : Terms.sorted(knowledge)) {
      b.fact(known(x));
    }

    // Lapsed(h): union of blocked hypotheses over all derivations of all targets.
    Set<Message> lapsed = new LinkedHashSet<>();
    Map<Message, List<Derivation>> ordered = new TreeMap<>(Terms.ORDER);
    ordered.putAll(derivationsByTarget);
    for (List<Derivation> ds : ordered.values()) {
      for (Derivation d : ds) {
        lapsed.addAll(blocking.blocked(NormalDerivations.hypotheses(d)));
      }
    }
    for (Message h : Terms.sorted(lapsed)) {
      b.fact(lapsedAtom(h));
    }
    for (Association a : cues.associations()) {
      if (lapsed.contains(a.message()) && cues.present().contains(a.cue())) {
        b.fact(contextCue(a.cue(), a.message()));
      }
    }
    for (String r : procedural.forgottenRules()) {
      b.fact(lapsedRule(r));
    }
    for (String p : procedural.forgottenProcedures()) {
      b.fact(lapsedProc(p));
    }

    Map<Message, Literal> usable = new LinkedHashMap<>();
    Map<Message, List<DerivationEntry>> entries = new LinkedHashMap<>();
    Map<String, Message> seenIds = new LinkedHashMap<>();
    for (Map.Entry<Message, List<Derivation>> e : ordered.entrySet()) {
      Message t = e.getKey();
      Literal u = Literal.pos("Usable(" + Terms.identity(t) + ")");
      usable.put(t, u);
      b.declare(u.atom());
      List<DerivationEntry> list = new ArrayList<>();
      for (Derivation d : e.getValue()) {
        list.add(compileDerivation(b, t, d, u, blocking, cues, seenIds));
      }
      entries.put(t, Collections.unmodifiableList(list));
    }
    return new Compiled(b.build(), Collections.unmodifiableMap(usable),
        Collections.unmodifiableMap(entries), Collections.unmodifiableSet(lapsed));
  }

  private DerivationEntry compileDerivation(DefeasibleTheory.Builder b, Message t, Derivation d,
      Literal usable, ForgetBlocking blocking, CueModel cues, Map<String, Message> seenIds) {
    String pid = NormalDerivations.procedureId(d);
    if (seenIds.containsKey(pid)) {
      throw new IllegalStateException("procedure id collision or duplicate derivation: " + pid);
    }
    seenIds.put(pid, t);
    Set<Message> hyps = new LinkedHashSet<>(Terms.sorted(NormalDerivations.hypotheses(d)));
    Set<Message> blocked = blocking.blocked(hyps);
    Set<String> rules = NormalDerivations.rules(d);
    Literal via = Literal.pos("Via(" + pid + "," + Terms.identity(t) + ")");

    String base = "d:" + pid;
    b.rule(new DefeasibleTheory.Rule(base, DefeasibleTheory.RuleKind.DEFEASIBLE,
        hyps.stream().map(ForgetDlCompiler::known).toList(), via));
    b.strict("use:" + pid, usable, via);

    Map<Message, String> decl = new LinkedHashMap<>();
    for (Message h : hyps) {
      String w = "w:" + pid + ":" + Terms.identity(h);
      b.defeater(w, via.complement(), lapsedAtom(h));
      b.superior(w, base);
      decl.put(h, w);
    }
    Map<String, Message> reinstaters = new LinkedHashMap<>();
    for (Message h : blocked) {
      for (Association a : cues.associations()) {
        if (a.message().equals(h)) {
          String rho = "rho:" + pid + ":" + Terms.identity(h) + ":" + Terms.field(a.cue());
          if (!b.hasRule(rho)) {
            b.defeasible(rho, via, contextCue(a.cue(), h));
            b.superior(rho, decl.get(h));
            reinstaters.put(rho, h);
          }
        }
      }
    }
    Map<String, String> ruleDefs = new LinkedHashMap<>();
    for (String r : rules) {
      String wr = "wr:" + pid + ":" + r;
      b.defeater(wr, via.complement(), lapsedRule(r));
      b.superior(wr, base);
      ruleDefs.put(r, wr);
    }
    String wp = "wp:" + pid;
    b.defeater(wp, via.complement(), lapsedProc(pid));
    b.superior(wp, base);
    return new DerivationEntry(t, d, pid, Collections.unmodifiableSet(hyps),
        Collections.unmodifiableSet(blocked), rules, via, base,
        Collections.unmodifiableMap(decl), Collections.unmodifiableMap(ruleDefs), wp,
        Collections.unmodifiableMap(reinstaters));
  }

  public static Literal known(Message x) {
    return Literal.pos("Known(" + Terms.identity(x) + ")");
  }

  public static Literal lapsedAtom(Message x) {
    return Literal.pos("Lapsed(" + Terms.identity(x) + ")");
  }

  public static Literal contextCue(String cue, Message x) {
    return Literal.pos("ContextCue(" + Terms.field(cue) + "," + Terms.identity(x) + ")");
  }

  public static Literal lapsedRule(String r) {
    return Literal.pos("LapsedRule(" + r + ")");
  }

  public static Literal lapsedProc(String p) {
    return Literal.pos("LapsedProc(" + p + ")");
  }
}
