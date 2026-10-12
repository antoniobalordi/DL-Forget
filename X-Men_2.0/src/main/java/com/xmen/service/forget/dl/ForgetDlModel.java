package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Typed inputs and state of the defeasible Forget mutation (paper Sections 4, 7.5, 7.6, 7.7). */
public final class ForgetDlModel {

  private ForgetDlModel() {}

  /** Manuscript pair-elimination label and the host's established projection label. */
  private static String ruleLabel(String rule) {
    Objects.requireNonNull(rule, "rule");
    if (rule.isBlank()) {
      throw new IllegalArgumentException("rule label must not be blank");
    }
    return rule.equals("unpair") ? "proj" : rule;
  }

  private static Set<String> ruleLabels(Set<String> rules) {
    Set<String> labels = new TreeSet<>();
    rules.forEach(r -> labels.add(ruleLabel(r)));
    return Collections.unmodifiableSet(labels);
  }

  /**
   * PForget<sub>H</sub> = (R<sub>H</sub>, &#928;<sub>H</sub>): forgotten inference-rule labels and
   * forgotten whole procedures (derivation identifiers, see {@link NormalDerivations#procedureId}).
   * A typed pair, so a rule label can never be confused with a procedure identifier.
   */
  public record ProceduralState(Set<String> forgottenRules, Set<String> forgottenProcedures) {
    public static final ProceduralState EMPTY = new ProceduralState(Set.of(), Set.of());

    public ProceduralState {
      forgottenRules = ruleLabels(forgottenRules);
      forgottenProcedures = Collections.unmodifiableSet(new TreeSet<>(forgottenProcedures));
    }
  }

  /** (c, m̄) ∈ Assoc: perceiving cue c can trigger retrieval of m̄. */
  public record Association(String cue, Message message) {
    public Association {
      Objects.requireNonNull(cue, "cue");
      Objects.requireNonNull(message, "message");
    }
  }

  /**
   * Definition 8: C<sub>H</sub> = (Cue, Assoc, Pres). Cue identifiers are not messages. Presence
   * and association are recorded separately: an ineffective cue is either absent from Pres or
   * absent from Assoc.
   */
  public record CueModel(Set<String> cues, Set<Association> associations, Set<String> present) {
    public static final CueModel NONE = new CueModel(Set.of(), Set.of(), Set.of());

    public CueModel {
      cues = Collections.unmodifiableSet(new TreeSet<>(cues));
      associations = Collections.unmodifiableSet(new LinkedHashSet<>(associations.stream()
          .sorted(java.util.Comparator.comparing(Association::cue)
              .thenComparing(Association::message, Terms.ORDER)).toList()));
      present = Collections.unmodifiableSet(new TreeSet<>(present));
    }
  }

  /** Persistent per-agent state carried between transitions. */
  public record HumanState(Set<Message> knowledge, Set<Message> forget,
      ProceduralState procedural) {
    public HumanState {
      knowledge = Collections.unmodifiableSet(new LinkedHashSet<>(Terms.sorted(knowledge)));
      forget = Collections.unmodifiableSet(new LinkedHashSet<>(Terms.sorted(forget)));
      Objects.requireNonNull(procedural, "procedural");
    }

    public static HumanState of(Set<Message> knowledge) {
      return new HumanState(knowledge, Set.of(), ProceduralState.EMPTY);
    }
  }

  /** The lapse event ℓ of Algorithm 2. */
  public sealed interface LapseEvent permits MessageLapse, RuleLapse, ProcedureLapse {}

  /** Declarative lapse of a message (just received, or already known). */
  public record MessageLapse(Message message) implements LapseEvent {
    public MessageLapse {
      Objects.requireNonNull(message, "message");
    }
  }

  /** Loss of an inference competence (rule label, e.g. {@code sdec}, {@code sign}). */
  public record RuleLapse(String rule) implements LapseEvent {
    public RuleLapse {
      rule = ruleLabel(rule);
    }
  }

  /** Loss of one whole derivation procedure (identifier from {@link NormalDerivations}). */
  public record ProcedureLapse(String procedureId) implements LapseEvent {
    public ProcedureLapse {
      Objects.requireNonNull(procedureId, "procedureId");
    }
  }

  /**
   * Input of one application of Algorithm 2 at transition i of agent H.
   *
   * @param before state at i (K<sup>i</sup>, Forget<sup>i</sup>, PForget<sup>i</sup>)
   * @param received m<sub>1</sub> (may be null if the transition receives nothing)
   * @param lapses lapse events at this transition (usually one; empty = decision only)
   * @param intendedSend m<sub>2</sub> (may be null if the transition sends nothing)
   * @param internalActionTerms terms used by the transition's explicit internal actions a
   * @param cues cue model at i+1
   * @param closure Case 1, 2 or 3 of Definition 5
   * @param labels label of each message ("constant-value" labels of Section 3.3)
   * @param relearnedRules explicit procedural recovery of rule competences at this transition
   * @param relearnedProcedures explicit procedural recovery of whole procedures
   */
  public record TransitionInput(
      HumanState before,
      Message received,
      List<LapseEvent> lapses,
      Message intendedSend,
      Set<Message> internalActionTerms,
      CueModel cues,
      BlockingMode closure,
      Map<Message, String> labels,
      Set<String> relearnedRules,
      Set<String> relearnedProcedures) {

    public TransitionInput {
      Objects.requireNonNull(before, "before");
      lapses = List.copyOf(lapses);
      internalActionTerms = Set.copyOf(internalActionTerms);
      Objects.requireNonNull(cues, "cues");
      Objects.requireNonNull(closure, "closure");
      labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
      relearnedRules = ruleLabels(relearnedRules);
      relearnedProcedures = Set.copyOf(relearnedProcedures);
    }

    public static Builder builder(HumanState before) {
      return new Builder(before);
    }

    /** Fluent builder with paper defaults (Case 1, no cues, no procedural recovery). */
    public static final class Builder {
      private final HumanState before;
      private Message received;
      private final List<LapseEvent> lapses = new ArrayList<>();
      private Message intendedSend;
      private final Set<Message> internalActionTerms = new LinkedHashSet<>();
      private CueModel cues = CueModel.NONE;
      private BlockingMode closure = BlockingMode.CASE1_WEAK;
      private final Map<Message, String> labels = new LinkedHashMap<>();
      private final Set<String> relearnedRules = new LinkedHashSet<>();
      private final Set<String> relearnedProcedures = new LinkedHashSet<>();

      private Builder(HumanState before) {
        this.before = before;
      }

      public Builder received(Message m1) {
        this.received = m1;
        return this;
      }

      public Builder lapse(LapseEvent e) {
        lapses.add(e);
        return this;
      }

      public Builder forget(Message m) {
        return lapse(new MessageLapse(m));
      }

      public Builder send(Message m2) {
        this.intendedSend = m2;
        return this;
      }

      public Builder internalAction(Message term) {
        internalActionTerms.add(term);
        return this;
      }

      public Builder cues(CueModel c) {
        this.cues = c;
        return this;
      }

      public Builder closure(BlockingMode mode) {
        this.closure = mode;
        return this;
      }

      public Builder label(Message m, String label) {
        labels.put(m, label);
        return this;
      }

      public Builder labels(Map<Message, String> ls) {
        labels.putAll(ls);
        return this;
      }

      public Builder relearnRule(String r) {
        relearnedRules.add(r);
        return this;
      }

      public Builder relearnProcedure(String p) {
        relearnedProcedures.add(p);
        return this;
      }

      public TransitionInput build() {
        return new TransitionInput(before, received, lapses, intendedSend, internalActionTerms,
            cues, closure, labels, relearnedRules, relearnedProcedures);
      }
    }
  }
}
