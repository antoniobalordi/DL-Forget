package com.xmen.service.forget.dl;

import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.ForgetDlCompiler.Compiled;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.LapseEvent;
import com.xmen.service.forget.dl.ForgetDlModel.MessageLapse;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.ProcedureLapse;
import com.xmen.service.forget.dl.ForgetDlModel.RuleLapse;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import com.xmen.service.forget.dl.ForgetDlResult.Candidate;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import com.xmen.service.forget.dl.ForgetDlResult.Status;
import com.xmen.service.forget.dl.NormalDerivations.Coverage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Algorithm 2 (µ<sup>H</sup><sub>Forget−DL</sub>) of the paper, as a pure function from a
 * {@link TransitionInput} to a {@link ForgetDlResult}. Deterministic: every iteration is over
 * canonically sorted collections.
 *
 * <p>Implementation choices where the paper is silent are documented in
 * {@code docs/forget-dl/SEMANTIC_CONTRACT.md} (A1–A12) and referenced inline.
 */
public final class ForgetDlEngine {

  /** Engine limits. Exceeding any of them is reported as truncated coverage. */
  public record Limits(NormalDerivations.Limits derivation, int maxCandidates) {
    public static final Limits DEFAULT = new Limits(NormalDerivations.Limits.DEFAULT, 256);

    public Limits {
      Objects.requireNonNull(derivation, "derivation limits");
      if (maxCandidates < 1) {
        throw new IllegalArgumentException("candidate limit must be positive");
      }
    }
  }

  private final DefeasibleReasoner reasoner;
  private final NormalDerivations derivations;
  private final Set<String> privateFunctions;
  private final Limits limits;
  private final ForgetDlCompiler compiler = new ForgetDlCompiler();

  public ForgetDlEngine() {
    this(new AmbiguityBlockingReasoner(), Set.of(), Limits.DEFAULT);
  }

  public ForgetDlEngine(DefeasibleReasoner reasoner, Set<String> privateFunctions, Limits limits) {
    this.reasoner = reasoner;
    this.privateFunctions = Set.copyOf(privateFunctions);
    this.limits = limits;
    this.derivations = new NormalDerivations(this.privateFunctions, limits.derivation());
  }

  public ForgetDlResult run(TransitionInput in) {
    HumanState before = in.before();
    Message m1 = in.received();
    Message m2 = in.intendedSend();
    List<String> notes = new ArrayList<>();

    // ---- Input validation (explicit failures, never semantic decisions) ----
    List<Message> toCheck = new ArrayList<>(before.knowledge());
    toCheck.addAll(before.forget());
    toCheck.addAll(in.internalActionTerms());
    in.cues().associations().forEach(a -> toCheck.add(a.message()));
    in.lapses().forEach(e -> {
      if (e instanceof MessageLapse ml) toCheck.add(ml.message());
    });
    if (m1 != null) {
      toCheck.add(m1);
    }
    if (m2 != null) {
      toCheck.add(m2);
    }
    for (Message x : toCheck) {
      Optional<String> bad = Terms.unsupportedConstruct(x);
      if (bad.isPresent()) {
        return ForgetDlResult.failure(Status.UNSUPPORTED, List.of(bad.get()), before);
      }
    }
    for (LapseEvent e : in.lapses()) {
      if (e instanceof MessageLapse ml && !ml.message().equals(m1)
          && !before.knowledge().contains(ml.message())) {
        return ForgetDlResult.failure(Status.INVALID_INPUT, List.of("lapsed message "
            + Terms.canonical(ml.message()) + " is neither received nor in K^i (Def. 4)"), before);
      }
    }
    for (Association a : in.cues().associations()) {
      if (!in.cues().cues().contains(a.cue())) {
        return ForgetDlResult.failure(Status.INVALID_INPUT,
            List.of("association uses undeclared cue " + a.cue()), before);
      }
    }
    if (!in.cues().cues().containsAll(in.cues().present())) {
      return ForgetDlResult.failure(Status.INVALID_INPUT,
          List.of("Pres is not a subset of Cue (Def. 8)"), before);
    }

    // ---- Lines 2-12: state update ----
    Set<Message> k1 = new LinkedHashSet<>(before.knowledge());
    if (m1 != null) {
      k1.add(m1);
    }
    Set<Message> forget1 = new LinkedHashSet<>(before.forget());
    Set<String> rules1 = new TreeSet<>(before.procedural().forgottenRules());
    Set<String> procs1 = new TreeSet<>(before.procedural().forgottenProcedures());
    boolean forgotReceived = false;
    Set<Message> lapsedNow = new LinkedHashSet<>();
    for (LapseEvent e : in.lapses()) {
      switch (e) {
        case MessageLapse ml -> {
          forget1.add(ml.message());
          lapsedNow.add(ml.message());
          if (ml.message().equals(m1)) {
            forgotReceived = true; // line 4, first branch: K stays K^i
          }
        }
        case RuleLapse rl -> rules1.add(rl.rule());
        case ProcedureLapse pl -> procs1.add(pl.procedureId());
      }
    }
    if (forgotReceived) {
      k1 = new LinkedHashSet<>(before.knowledge());
    }
    for (Association a : in.cues().associations()) {
      if (!k1.contains(a.message())) {
        return ForgetDlResult.failure(Status.INVALID_INPUT, List.of("association ("
            + a.cue() + "," + Terms.canonical(a.message()) + ") outside K^{i+1} (Def. 8)"), before);
      }
    }

    // Independent recovery events still update persistent competence when Neglect returns.
    // Multiple events apply lapses first, then explicit recovery.
    rules1.removeAll(in.relearnedRules());
    procs1.removeAll(in.relearnedProcedures());

    // ---- Line 13: re-acquisition (A6), before the Neglect test and also on a Neglect transition (A8) ----
    ProceduralState proc1 = new ProceduralState(rules1, procs1);
    Set<Message> reacquired = new LinkedHashSet<>();
    Set<Message> unresolvedReacquisition = new LinkedHashSet<>();
    if (m1 != null && !forgotReceived) {
      boolean changed = true;
      while (changed) {
        changed = false;
        ForgetBlocking blocking = new ForgetBlocking(forget1, in.closure(), privateFunctions);
        for (Message f : Terms.sorted(forget1)) {
          if (lapsedNow.contains(f)) {
            continue; // a lapse of this transition is not undone by the same receipt
          }
          if (f.equals(m1) || recoverableVia(f, m1, k1, blocking, proc1, notes, unresolvedReacquisition)) {
            forget1.remove(f);
            unresolvedReacquisition.remove(f);
            reacquired.add(f);
            changed = true;
            break;
          }
        }
      }
    }
    HumanState after = new HumanState(k1, forget1, proc1);

    // ---- Lines 5-6: Neglect. A message occurring in an internal action is inaccessible if it is
    // blocked by the forget set after re-acquisition and not cued (A8). Lapses of earlier
    // transitions count; a message re-acquired from m1 does not. ----
    Set<Message> cued = cuedMessages(in.cues());
    ForgetBlocking blockingAfter = new ForgetBlocking(forget1, in.closure(), privateFunctions);
    Set<Message> neglected = new LinkedHashSet<>();
    for (Message a : Terms.sorted(in.internalActionTerms())) {
      for (Message s : Terms.sorted(Terms.subterms(a))) {
        if (blockingAfter.isBlocked(s) && !cued.contains(s)) {
          neglected.add(s);
        }
      }
    }
    if (!neglected.isEmpty()) {
      return new ForgetDlResult(Status.OK,
          unresolvedReacquisition.isEmpty() ? Decision.NEGLECT : Decision.UNKNOWN_COVERAGE,
          List.copyOf(notes), after, reacquired, neglected, List.of(), List.of(),
          Coverage.COMPLETE, false, null, null, List.of());
    }

    if (m2 == null) {
      return new ForgetDlResult(Status.OK,
          unresolvedReacquisition.isEmpty() ? Decision.NO_SEND : Decision.UNKNOWN_COVERAGE,
          List.copyOf(notes), after,
          reacquired, Set.of(), List.of(), List.of(), Coverage.COMPLETE, false, null, null,
          List.of());
    }

    // ---- Lines 14-19: candidate targets ----
    ForgetBlocking blocking = new ForgetBlocking(forget1, in.closure(), privateFunctions);
    NormalDerivations.Result r2 = derivations.enumerate(m2, after.knowledge());
    if (r2.coverage() == Coverage.UNSUPPORTED) {
      return ForgetDlResult.failure(Status.UNSUPPORTED, List.of(r2.note()), after);
    }
    Coverage candidateCoverage = decisionCoverage(r2, proc1, notes);
    Map<Message, List<Map<Message, Message>>> provenance = new LinkedHashMap<>();
    provenance.put(m2, new ArrayList<>());
    List<Message> kSorted = Terms.sorted(after.knowledge());
    int combinationsChecked = 0;
    outer:
    for (Derivation pi : r2.derivations()) {
      // Rep_π: only uncued blocked hypotheses are replaced; a cued one is kept (R3.1).
      Set<Message> blocked = blocking.blocked(NormalDerivations.hypotheses(pi));
      blocked.removeAll(cued);
      if (blocked.isEmpty()) {
        continue;
      }
      List<Message> keys = new ArrayList<>(blocked);
      List<List<Message>> sets = new ArrayList<>();
      for (Message b : keys) {
        List<Message> rb = replacementSet(b, kSorted, forget1, in.labels());
        if (rb.isEmpty()) {
          continue outer; // Algorithm 1 line 19-20: no replacement, next π
        }
        sets.add(rb);
      }
      for (Map<Message, Message> c : combinations(keys, sets)) {
        if (combinationsChecked++ >= limits.derivation().maxSteps()) {
          candidateCoverage = Coverage.TRUNCATED;
          notes.add("replacement combination work limit reached");
          break outer;
        }
        Message variant = Terms.substitute(m2, c);
        if (variant.equals(m2)) {
          continue; // no blocked hypothesis occurs in m2: m2[C] = m2, already in T
        }
        if (!provenance.containsKey(variant) && provenance.size() >= limits.maxCandidates()) {
          candidateCoverage = Coverage.TRUNCATED;
          notes.add("candidate limit " + limits.maxCandidates() + " reached");
          break outer;
        }
        List<Map<Message, Message>> prov = provenance.computeIfAbsent(variant, k -> new ArrayList<>());
        // One deterministic substitution witnesses a target; additional witnesses emit no trace.
        if (prov.isEmpty()) {
          prov.add(Map.copyOf(c));
        }
      }
    }

    // ---- Π_t for every t ∈ T ----
    Map<Message, List<Derivation>> byTarget = new LinkedHashMap<>();
    Map<Message, Coverage> coverage = new LinkedHashMap<>();
    for (Message t : provenance.keySet()) {
      NormalDerivations.Result rt = t.equals(m2) ? r2 : derivations.enumerate(t, after.knowledge());
      if (rt.coverage() == Coverage.UNSUPPORTED) {
        return ForgetDlResult.failure(Status.UNSUPPORTED, List.of(rt.note()), after);
      }
      byTarget.put(t, rt.derivations());
      coverage.put(t, decisionCoverage(rt, proc1, notes));
      if (rt.coverage() == Coverage.TRUNCATED) {
        notes.add("derivation enumeration truncated for " + Terms.canonical(t));
      }
    }

    // ---- Lines 20-21: one theory for all candidates ----
    Compiled compiled = compiler.compile(after.knowledge(), blocking, proc1, in.cues(), byTarget);
    Conclusions conc = reasoner.reason(compiled.theory());

    List<Candidate> candidates = new ArrayList<>();
    for (Message t : provenance.keySet()) {
      candidates.add(new Candidate(t, t.equals(m2), List.copyOf(provenance.get(t)),
          coverage.get(t), conc.partialTag(compiled.usable().get(t))));
    }

    // ---- Lines 22-31: decision ----
    Literal u2 = compiled.usable().get(m2);
    Decision decision;
    List<Candidate> replacements = new ArrayList<>();
    boolean partial = false;
    if (!unresolvedReacquisition.isEmpty()) {
      decision = Decision.UNKNOWN_COVERAGE;
    } else if (conc.provable(u2)) {
      decision = Decision.PRESERVE;
    } else if (!conc.refuted(u2)) {
      decision = Decision.UNDECIDED_LOGIC;
    } else if (coverage.get(m2) != Coverage.COMPLETE) {
      decision = Decision.UNKNOWN_COVERAGE;
    } else {
      boolean allConclusive = candidateCoverage == Coverage.COMPLETE;
      for (Candidate c : candidates) {
        if (c.intended()) {
          continue;
        }
        Literal u = compiled.usable().get(c.target());
        if (conc.provable(u)) {
          replacements.add(c);
        } else if (!conc.refuted(u) || c.derivationCoverage() != Coverage.COMPLETE) {
          allConclusive = false;
        }
      }
      if (!replacements.isEmpty()) {
        decision = Decision.REPLACE;
        partial = !allConclusive;
      } else {
        decision = allConclusive ? Decision.SKIP : Decision.UNKNOWN_COVERAGE;
      }
    }
    return new ForgetDlResult(Status.OK, decision, List.copyOf(notes), after, reacquired,
        Set.of(), List.copyOf(candidates), List.copyOf(replacements), candidateCoverage, partial,
        compiled, conc, ForgetDlResult.buildTrace(compiled, conc, m2));
  }

  /**
   * R<sub>m̄</sub> (Algorithm 2, line 17): known, not syntactically forgotten, same format, same
   * (known) label, and different from m̄ itself (A5).
   */
  private static List<Message> replacementSet(Message b, List<Message> kSorted,
      Set<Message> forget, Map<Message, String> labels) {
    String label = labels.get(b);
    List<Message> out = new ArrayList<>();
    if (label == null) {
      return out; // A4: without a label, no type-safe replacement exists
    }
    String format = Terms.format(b);
    for (Message x : kSorted) {
      if (!x.equals(b) && !forget.contains(x) && format.equals(Terms.format(x))
          && label.equals(labels.get(x))) {
        out.add(x);
      }
    }
    return out;
  }

  private static Iterable<Map<Message, Message>> combinations(List<Message> keys,
      List<List<Message>> sets) {
    // ponytail: enumerate lazily; the candidate/work caps must apply before Cartesian expansion.
    return () -> new Iterator<>() {
      private final int[] indices = new int[keys.size()];
      private boolean available = sets.stream().noneMatch(List::isEmpty);

      public boolean hasNext() {
        return available;
      }

      public Map<Message, Message> next() {
        if (!available) {
          throw new NoSuchElementException();
        }
        Map<Message, Message> next = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
          next.put(keys.get(i), sets.get(i).get(indices[i]));
        }
        available = false;
        for (int i = indices.length - 1; i >= 0; i--) {
          if (++indices[i] < sets.get(i).size()) {
            available = true;
            break;
          }
          indices[i] = 0;
        }
        return next;
      }
    };
  }

  private static Coverage decisionCoverage(NormalDerivations.Result r, ProceduralState p,
      List<String> notes) {
    if (r.cyclicDerivations() && !p.forgottenProcedures().isEmpty()) {
      String note = "whole-procedure lapse requires cyclic proofs outside the finite basis for "
          + Terms.canonical(r.target());
      if (!notes.contains(note)) {
        notes.add(note);
      }
      return Coverage.TRUNCATED;
    }
    return r.coverage();
  }

  /** A6: f is re-acquired if some derivation of f uses the received m1 and nothing blocked. */
  private boolean recoverableVia(Message f, Message m1, Set<Message> k1, ForgetBlocking blocking,
      ProceduralState procedural, List<String> notes, Set<Message> unresolved) {
    NormalDerivations.Result r = derivations.enumerate(f, k1);
    for (Derivation d : r.derivations()) {
      Set<Message> hyps = NormalDerivations.hypotheses(d);
      if (hyps.contains(m1) && blocking.blocked(hyps).isEmpty()
          && java.util.Collections.disjoint(NormalDerivations.rules(d), procedural.forgottenRules())
          && !procedural.forgottenProcedures().contains(NormalDerivations.procedureId(d))) {
        return true;
      }
    }
    // Re-acquisition is defined on the finite basis (A6). Without forgotten procedures, a witness
    // outside it shortens to a surviving derivation of f without m1, so the receipt adds nothing;
    // with forgotten procedures it may, and the search is incomplete (as decisionCoverage).
    boolean cyclic = r.cyclicDerivations() && !procedural.forgottenProcedures().isEmpty();
    if (r.coverage() != Coverage.COMPLETE || cyclic) {
      unresolved.add(f);
      notes.add("re-acquisition check for " + Terms.canonical(f) + " incomplete ("
          + (cyclic ? "cyclic receipt provenance under a procedure lapse" : r.coverage())
          + "); successor forgetting state is uncertain");
    }
    return false;
  }

  /** Messages associated with a cue present at this transition (cued<sub>σ</sub>, Def. 2). */
  private static Set<Message> cuedMessages(ForgetDlModel.CueModel cues) {
    Set<Message> out = new LinkedHashSet<>();
    for (Association a : cues.associations()) {
      if (cues.present().contains(a.cue())) {
        out.add(a.message());
      }
    }
    return out;
  }
}
