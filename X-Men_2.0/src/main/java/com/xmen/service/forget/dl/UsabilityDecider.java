package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Theorem 16: restricted Dolev-Yao usability without enumerating derivations.
 * Inputs must use the supported free algebra and have no exact-procedure lapses.
 * Each successful pass adds a subterm of K ∪ {target}; no proof trees are expanded.
 */
public final class UsabilityDecider {

  private final Set<String> privateFunctions;

  public UsabilityDecider(Set<String> privateFunctions) {
    this.privateFunctions = Set.copyOf(privateFunctions);
  }

  public boolean usable(Message target, Set<Message> knowledge, ForgetBlocking blocking,
      Set<String> forgottenRules, CueModel cues) {
    Set<Message> cued = new LinkedHashSet<>();
    for (Association a : cues.associations()) {
      if (cues.present().contains(a.cue())) {
        cued.add(a.message());
      }
    }
    Set<Message> available = new LinkedHashSet<>();
    for (Message h : knowledge) {
      if (!blocking.isBlocked(h) || cued.contains(h)) {
        available.add(h);
      }
    }

    Set<Message> subterms = Terms.subterms(knowledge);
    subterms.addAll(Terms.subterms(target));
    List<Message> terms = new ArrayList<>(subterms).reversed();
    boolean changed = true;
    while (changed && !available.contains(target)) {
      changed = false;
      for (Message x : terms) {
        if (!available.contains(x)) {
          Optional<String> constructor = Terms.constructorLabel(x, privateFunctions);
          if (constructor.isPresent() && !forgottenRules.contains(constructor.get())
              && available.containsAll(Terms.args(x))) {
            changed |= available.add(x);
          }
        }
        if (!available.contains(x)) {
          continue;
        }
        if (x instanceof Pair p && !forgottenRules.contains("proj")) {
          changed |= available.add(p.getLeft());
          changed |= available.add(p.getRight());
        }
        Optional<Terms.Cipher> cipher = Terms.symmetric(x).or(() -> Terms.asymmetric(x));
        if (cipher.isPresent() && !forgottenRules.contains(cipher.get().decryptLabel())
            && available.contains(cipher.get().decryptionKey())) {
          changed |= available.add(cipher.get().plaintext());
        }
      }
    }
    return available.contains(target);
  }
}
