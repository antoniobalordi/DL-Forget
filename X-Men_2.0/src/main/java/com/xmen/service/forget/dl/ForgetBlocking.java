package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Definition 5: {@code Blocked(Hyp_π, Forget)} for the three closure cases.
 *
 * <ul>
 *   <li>Case 1: h ∈ Forget.
 *   <li>Case 2: Forget ⊢ h using only pair composition and decomposition.
 *   <li>Case 3: Forget ⊢ h using all supported Dolev-Yao rules.
 * </ul>
 *
 * Derivability is decided by the standard analysis/synthesis saturation, which terminates because
 * the analysed set stays within the subterms of Forget. Blockedness depends only on h and the
 * Forget set, not on the derivation or target in which h occurs.
 */
public final class ForgetBlocking {

  private final BlockingMode mode;
  private final Set<String> privateFunctions;
  private final Set<Message> forget;
  private final Set<Message> analysed;

  public ForgetBlocking(Set<Message> forget, BlockingMode mode, Set<String> privateFunctions) {
    this.mode = mode;
    this.privateFunctions = Set.copyOf(privateFunctions);
    this.forget = Set.copyOf(forget);
    this.analysed = mode == BlockingMode.CASE1_WEAK ? this.forget : analyse();
  }

  public BlockingMode mode() {
    return mode;
  }

  public boolean isBlocked(Message h) {
    return switch (mode) {
      case CASE1_WEAK -> forget.contains(h);
      case CASE2_PAIRING, CASE3_FULL_DY -> synthesise(h);
    };
  }

  /** Blocked subset of {@code hyps}, in canonical order. */
  public Set<Message> blocked(Collection<Message> hyps) {
    Set<Message> out = new LinkedHashSet<>();
    for (Message h : Terms.sorted(hyps)) {
      if (isBlocked(h)) {
        out.add(h);
      }
    }
    return out;
  }

  private Set<Message> analyse() {
    Set<Message> a = new LinkedHashSet<>(forget);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Message x : List.copyOf(a)) {
        if (x instanceof Pair p) {
          changed |= a.add(p.getLeft());
          changed |= a.add(p.getRight());
        }
        if (mode == BlockingMode.CASE3_FULL_DY) {
          Optional<Terms.Cipher> c = Terms.symmetric(x).or(() -> Terms.asymmetric(x));
          if (c.isPresent() && synthesise(c.get().decryptionKey(), a)) {
            changed |= a.add(c.get().plaintext());
          }
        }
      }
    }
    return a;
  }

  private boolean synthesise(Message x) {
    return synthesise(x, analysed);
  }

  private boolean synthesise(Message x, Set<Message> base) {
    if (base.contains(x)) {
      return true;
    }
    boolean composable = mode == BlockingMode.CASE2_PAIRING
        ? x instanceof Pair
        : mode == BlockingMode.CASE3_FULL_DY
            && Terms.constructorLabel(x, privateFunctions).isPresent();
    if (!composable) {
      return false;
    }
    for (Message arg : Terms.args(x)) {
      if (!synthesise(arg, base)) {
        return false;
      }
    }
    return true;
  }
}
