package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.DefeasibleTheory.Rule;
import com.xmen.service.forget.dl.ForgetDlCompiler.Compiled;
import com.xmen.service.forget.dl.ForgetDlCompiler.DerivationEntry;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.NormalDerivations.Coverage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Outcome of one application of Algorithm 2, with its cognitive trace (Section 7.4). */
public record ForgetDlResult(
    Status status,
    Decision decision,
    List<String> notes,
    HumanState after,
    Set<Message> reacquired,
    Set<Message> neglected,
    List<Candidate> candidates,
    List<Candidate> replacements,
    Coverage candidateCoverage,
    boolean partial,
    Compiled compiled,
    Conclusions conclusions,
    List<TargetTrace> trace) {

  /** Processing status; anything but OK carries no semantic decision. */
  public enum Status {
    OK,
    INVALID_INPUT,
    UNSUPPORTED
  }

  /** Algorithm 2 outcome, plus explicit non-decisions. */
  public enum Decision {
    /** +∂Usable(m2): the intended send is kept (line 23). */
    PRESERVE,
    /** −∂Usable(m2) and V ≠ ∅: one Replace per usable variant (lines 25-28). */
    REPLACE,
    /** −∂Usable(m2), V = ∅, complete coverage (line 30). */
    SKIP,
    /** A lapsed message is used in an internal action (lines 5-6). */
    NEGLECT,
    /** The transition has no send; only the state update applies. */
    NO_SEND,
    /** Negative evidence rests on a truncated enumeration: no Replace/Skip claim. */
    UNKNOWN_COVERAGE,
    /** The theory leaves Usable(m2) without a defeasible tag. */
    UNDECIDED_LOGIC,
    /** No decision (invalid input or unsupported algebra). */
    NONE
  }

  /** A target in T with the syntactic substitutions C that produced it. */
  public record Candidate(
      Message target,
      boolean intended,
      List<Map<Message, Message>> substitutions,
      Coverage derivationCoverage,
      String usableTag) {}

  public enum BlockerKind {
    DECLARATIVE,
    RULE,
    PROCEDURE
  }

  /** An applicable opposing defeater and the applicable supporting rules superior to it. */
  public record Blocker(BlockerKind kind, String label, String subject, List<String> overruledBy) {
    public boolean standing() {
      return overruledBy.isEmpty();
    }
  }

  /** Trace of one derivation π: its Via tag, applicable support and blockers. */
  public record DerivationTrace(
      String procedureId,
      String viaTag,
      Set<Message> hypotheses,
      Set<Message> blockedHypotheses,
      Set<String> rules,
      List<String> applicableSupport,
      List<Blocker> blockers) {

    public List<Blocker> standing() {
      return blockers.stream().filter(Blocker::standing).toList();
    }
  }

  /** Trace of one target: Usable tag and per-derivation explanation. */
  public record TargetTrace(Message target, boolean intended, String usableTag,
      List<DerivationTrace> derivations) {

    public boolean noDerivation() {
      return derivations.isEmpty();
    }
  }

  static ForgetDlResult failure(Status s, List<String> notes, HumanState after) {
    return new ForgetDlResult(s, Decision.NONE, List.copyOf(notes), after, Set.of(), Set.of(),
        List.of(), List.of(), Coverage.COMPLETE, false, null, null, List.of());
  }

  /** Builds the cognitive trace from the compiled theory and its tagged conclusions. */
  static List<TargetTrace> buildTrace(Compiled compiled, Conclusions c, Message intended) {
    List<TargetTrace> out = new ArrayList<>();
    for (Map.Entry<Message, List<DerivationEntry>> e : compiled.entries().entrySet()) {
      Message t = e.getKey();
      List<DerivationTrace> ds = new ArrayList<>();
      for (DerivationEntry d : e.getValue()) {
        List<String> support = new ArrayList<>();
        List<Rule> supportRules = new ArrayList<>();
        Rule base = compiled.theory().rule(d.baseLabel());
        if (applicable(base, c)) {
          support.add(base.label());
          supportRules.add(base);
        }
        for (String rho : d.reinstaters().keySet()) {
          Rule r = compiled.theory().rule(rho);
          if (applicable(r, c)) {
            support.add(rho);
            supportRules.add(r);
          }
        }
        List<Blocker> blockers = new ArrayList<>();
        d.declarativeDefeaters().forEach((h, label) ->
            addBlocker(blockers, compiled, c, supportRules, BlockerKind.DECLARATIVE, label,
                Terms.canonical(h)));
        d.ruleDefeaters().forEach((r, label) ->
            addBlocker(blockers, compiled, c, supportRules, BlockerKind.RULE, label, r));
        addBlocker(blockers, compiled, c, supportRules, BlockerKind.PROCEDURE,
            d.procedureDefeater(), d.procedureId());
        ds.add(new DerivationTrace(d.procedureId(), c.partialTag(d.via()), d.hypotheses(),
            d.blockedHypotheses(), d.rules(), List.copyOf(support), List.copyOf(blockers)));
      }
      out.add(new TargetTrace(t, t.equals(intended),
          c.partialTag(compiled.usable().get(t)), List.copyOf(ds)));
    }
    return List.copyOf(out);
  }

  private static void addBlocker(List<Blocker> out, Compiled compiled, Conclusions c,
      List<Rule> support, BlockerKind kind, String label, String subject) {
    Rule s = compiled.theory().rule(label);
    if (!applicable(s, c)) {
      return;
    }
    List<String> by = support.stream()
        .filter(t -> compiled.theory().isSuperior(t.label(), s.label()))
        .map(Rule::label)
        .toList();
    out.add(new Blocker(kind, label, subject, by));
  }

  private static boolean applicable(Rule r, Conclusions c) {
    for (Literal l : r.body()) {
      if (!c.provable(l)) {
        return false;
      }
    }
    return true;
  }

  /** Readable explanation of the decision and of every target's tag. */
  public String explain() {
    StringBuilder sb = new StringBuilder();
    sb.append("status=").append(status).append(" decision=").append(decision);
    if (partial) {
      sb.append(" (partial: candidate set not exhaustive)");
    }
    sb.append('\n');
    for (String n : notes) {
      sb.append("note: ").append(n).append('\n');
    }
    for (TargetTrace t : trace) {
      sb.append(t.intended() ? "intended " : "variant  ").append(Terms.canonical(t.target()))
          .append(": ").append(t.usableTag()).append("Usable");
      if (t.noDerivation()) {
        sb.append(" — no normal derivation (Π = ∅)");
      }
      sb.append('\n');
      for (DerivationTrace d : t.derivations()) {
        sb.append("  ").append(d.procedureId()).append(' ').append(d.viaTag()).append("Via")
            .append(" hypotheses=").append(Terms.sorted(d.hypotheses()).stream().map(Terms::canonical).toList())
            .append(" blocked=").append(Terms.sorted(d.blockedHypotheses()).stream().map(Terms::canonical).toList())
            .append(" rules=").append(d.rules()).append(" support=").append(d.applicableSupport())
            .append('\n');
        for (Blocker b : d.blockers()) {
          sb.append("    ").append(b.standing() ? "standing " : "overruled ")
              .append(b.kind()).append(' ').append(b.subject()).append(" [").append(b.label())
              .append(']');
          if (!b.standing()) {
            sb.append(" by ").append(b.overruledBy());
          }
          sb.append('\n');
        }
      }
    }
    return sb.toString();
  }
}
