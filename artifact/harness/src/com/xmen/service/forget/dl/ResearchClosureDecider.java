package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Research prototype (not part of X-Men 2.0): decides U_σ(t) without enumerating derivations,
 * for states without exact-procedure lapses (Π̄ = ∅).
 *
 * <p>Accessible knowledge: K_acc = {h ∈ K | not β(h) or h is cued}. Then t is usable iff t is
 * Dolev-Yao derivable from K_acc using only the inference rules not in R̄. Derivability is decided
 * by the usual analysis/synthesis saturation restricted to the available rules: the analysed set
 * A is the closure of K_acc under the available destructors (proj, sdec, adec) whose key premise
 * is synthesisable from A, and t is usable iff t can be synthesised from A with the available
 * constructors. Every step stays within the subterms of K ∪ {t}, so the procedure is polynomial.
 */
public final class ResearchClosureDecider {

  private final Set<String> privateFunctions;

  public ResearchClosureDecider(Set<String> privateFunctions) {
    this.privateFunctions = Set.copyOf(privateFunctions);
  }

  public boolean usable(Message t, Set<Message> k, ForgetBlocking blocking,
      Set<String> forgottenRules, CueModel cues) {
    Set<Message> acc = new LinkedHashSet<>();
    for (Message h : k) {
      if (!blocking.isBlocked(h) || cued(h, cues)) {
        acc.add(h);
      }
    }
    Set<Message> a = analyse(acc, forgottenRules);
    return synth(t, a, forgottenRules);
  }

  static boolean cued(Message h, CueModel cues) {
    for (Association x : cues.associations()) {
      if (x.message().equals(h) && cues.present().contains(x.cue())) {
        return true;
      }
    }
    return false;
  }

  private Set<Message> analyse(Set<Message> base, Set<String> forgotten) {
    Set<Message> a = new LinkedHashSet<>(base);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Message x : List.copyOf(a)) {
        if (x instanceof Pair p && !forgotten.contains("proj")) {
          changed |= a.add(p.getLeft());
          changed |= a.add(p.getRight());
        }
        Optional<Terms.Cipher> c = Terms.symmetric(x).or(() -> Terms.asymmetric(x));
        if (c.isPresent() && !forgotten.contains(c.get().decryptLabel())
            && !a.contains(c.get().plaintext()) && synth(c.get().decryptionKey(), a, forgotten)) {
          a.add(c.get().plaintext());
          changed = true;
        }
      }
    }
    return a;
  }

  private boolean synth(Message x, Set<Message> a, Set<String> forgotten) {
    if (a.contains(x)) {
      return true;
    }
    Optional<String> ctor = Terms.constructorLabel(x, privateFunctions);
    if (ctor.isEmpty() || forgotten.contains(ctor.get())) {
      return false;
    }
    for (Message arg : Terms.args(x)) {
      if (!synth(arg, a, forgotten)) {
        return false;
      }
    }
    return true;
  }
}
