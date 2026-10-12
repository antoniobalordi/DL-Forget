package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Encrypt;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Adapter between the X-Men Forget pipeline (ForgetContext, parsed rule payloads, Forget(x)
 * annotations) and {@link ForgetDlEngine}. Pure Java so it can be tested without Spring.
 *
 * <p>Host-representation choices (semantic contract A9, A10):
 *
 * <ul>
 *   <li>Tamarin fresh-variable tildes are ignored ({@code ~pw1} ≡ {@code pw1}), as the host's
 *       {@code canonicalize} already does.
 *   <li>Public constants ({@code $X} and quoted {@code 'c'}) occurring in the intended send or in
 *       a candidate are available to the agent; they are added to K for derivation only and are
 *       not written back into the host state.
 * </ul>
 */
public final class ForgetDlBridge {

  /**
   * Decision plus what the host needs to rewrite the rule.
   *
   * @param replacementSubstitutions one map {blocked → replacement} per Replace variant, in the
   *     engine's deterministic order; keys and values are in the bridge's normalised form
   * @param knowledgeAfter successor knowledge in host representation
   * @param forgetAfter successor Forget set in host representation
   */
  public record Outcome(ForgetDlResult result, List<Map<Message, Message>> replacementSubstitutions,
      List<Message> replacementTargets, Set<Message> knowledgeAfter, Set<Message> forgetAfter) {}

  private final ForgetDlEngine engine;

  public ForgetDlBridge() {
    this(new ForgetDlEngine());
  }

  public ForgetDlBridge(ForgetDlEngine engine) {
    this.engine = engine;
  }

  public Outcome decide(Set<Message> knowledge, Set<Message> forget, Message received,
      List<Message> forgotten, Set<Message> forgottenUsedInInternalActions, Message target,
      Function<Message, String> labelOf, BlockingMode mode) {
    return decide(knowledge, forget, ProceduralState.EMPTY, received, forgotten,
        forgottenUsedInInternalActions, target, labelOf, mode, CueModel.NONE);
  }

  public Outcome decide(Set<Message> knowledge, Set<Message> forget, ProceduralState procedural,
      Message received, List<Message> forgotten, Set<Message> forgottenUsedInInternalActions,
      Message target, Function<Message, String> labelOf, BlockingMode mode, CueModel cues) {
    Map<Message, Message> original = new LinkedHashMap<>();
    Set<Message> k = normaliseAll(knowledge, original);
    Set<Message> f = normaliseAll(forget, original);
    Message m1 = received == null ? null : remember(normalise(received), received, original);
    Message m2 = target == null ? null : normalise(target);

    Set<Message> derivationK = new LinkedHashSet<>(k);
    if (m2 != null) {
      for (Message s : Terms.subterms(m2)) {
        if (isPublicConstant(s)) {
          derivationK.add(s);
        }
      }
    }
    TransitionInput.Builder b = TransitionInput.builder(new HumanState(derivationK, f, procedural))
        .received(m1).send(m2).closure(mode).cues(cues);
    for (Message x : forgotten) {
      b.forget(normalise(x));
    }
    for (Message x : forgottenUsedInInternalActions) {
      b.internalAction(normalise(x));
    }
    Set<Message> labelled = new LinkedHashSet<>(derivationK);
    if (m1 != null) {
      labelled.add(m1);
    }
    for (Message x : labelled) {
      String l = labelOf.apply(original.getOrDefault(x, x));
      if (l == null) {
        l = labelOf.apply(x);
      }
      if (l != null) {
        b.label(x, l);
      }
    }
    ForgetDlResult r = engine.run(b.build());

    List<Map<Message, Message>> subs = new ArrayList<>();
    List<Message> targets = new ArrayList<>();
    for (ForgetDlResult.Candidate c : r.replacements()) {
      subs.add(c.substitutions().get(0));
      targets.add(c.target());
    }
    Set<Message> kAfter = new LinkedHashSet<>();
    Set<Message> hostK = new LinkedHashSet<>(k);
    if (m1 != null) {
      hostK.add(m1);
    }
    for (Message x : r.after().knowledge()) {
      if (hostK.contains(x)) {
        kAfter.add(original.getOrDefault(x, x)); // A10: injected public constants are dropped
      }
    }
    Set<Message> fAfter = new LinkedHashSet<>();
    for (Message x : r.after().forget()) {
      fAfter.add(original.getOrDefault(x, x));
    }
    return new Outcome(r, List.copyOf(subs), List.copyOf(targets), kAfter, fAfter);
  }

  private static Set<Message> normaliseAll(Set<Message> ms, Map<Message, Message> original) {
    Set<Message> out = new LinkedHashSet<>();
    for (Message m : ms) {
      out.add(remember(normalise(m), m, original));
    }
    return out;
  }

  private static Message remember(Message norm, Message orig, Map<Message, Message> original) {
    original.putIfAbsent(norm, orig);
    return norm;
  }

  static boolean isPublicConstant(Message m) {
    return m instanceof Atom a
        && (a.getValue().startsWith("$") || (a.getValue().startsWith("'") && a.getValue().endsWith("'")));
  }

  /** Removes Tamarin fresh-variable tildes from atoms, recursively. */
  static Message normalise(Message m) {
    if (m instanceof Atom a) {
      String v = a.getValue();
      return v.startsWith("~") && v.length() > 1 ? new Atom(v.substring(1)) : m;
    }
    if (m instanceof Pair p) {
      return new Pair(normalise(p.getLeft()), normalise(p.getRight()));
    }
    if (m instanceof Encrypt e) {
      return new Encrypt(normalise(e.getMsg()), normalise(e.getKey()));
    }
    if (m instanceof PredictiveFunction fn) {
      return new PredictiveFunction(fn.getName(), fn.getArgs().stream().map(ForgetDlBridge::normalise).toList());
    }
    return m;
  }
}
