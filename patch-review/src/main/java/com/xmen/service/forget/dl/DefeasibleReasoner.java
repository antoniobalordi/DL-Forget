package com.xmen.service.forget.dl;

import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes the tagged conclusions of a finite propositional defeasible theory.
 *
 * <p>The single production implementation is {@link AmbiguityBlockingReasoner}. The interface is
 * the seam for qualifying an external backend (e.g. SPINdle) against the same conformance suite.
 */
public interface DefeasibleReasoner {

  /** The four proof tags of DL(&#8706;). */
  enum Tag {
    /** +&#916;: definitely provable (facts and strict rules only). */
    PLUS_DELTA,
    /** -&#916;: provably not definitely provable. */
    MINUS_DELTA,
    /** +&#8706;: defeasibly provable. */
    PLUS_PARTIAL,
    /** -&#8706;: provably not defeasibly provable (a conclusion, not a failed search). */
    MINUS_PARTIAL
  }

  Conclusions reason(DefeasibleTheory theory);

  /**
   * The four disjoint-by-tag conclusion sets. A literal may have neither +&#8706; nor -&#8706;
   * (e.g. in a positive loop); callers must treat that as undecided, never as negative.
   */
  record Conclusions(
      Set<Literal> plusDelta,
      Set<Literal> minusDelta,
      Set<Literal> plusPartial,
      Set<Literal> minusPartial) {

    public Conclusions {
      plusDelta = Collections.unmodifiableSet(new TreeSet<>(plusDelta));
      minusDelta = Collections.unmodifiableSet(new TreeSet<>(minusDelta));
      plusPartial = Collections.unmodifiableSet(new TreeSet<>(plusPartial));
      minusPartial = Collections.unmodifiableSet(new TreeSet<>(minusPartial));
    }

    public boolean has(Tag tag, Literal l) {
      return switch (tag) {
        case PLUS_DELTA -> plusDelta.contains(l);
        case MINUS_DELTA -> minusDelta.contains(l);
        case PLUS_PARTIAL -> plusPartial.contains(l);
        case MINUS_PARTIAL -> minusPartial.contains(l);
      };
    }

    public boolean provable(Literal l) {
      return plusPartial.contains(l);
    }

    public boolean refuted(Literal l) {
      return minusPartial.contains(l);
    }

    /** True iff exactly one of +&#8706;l and -&#8706;l holds. */
    public boolean decided(Literal l) {
      return plusPartial.contains(l) ^ minusPartial.contains(l);
    }

    /** Short rendering of the defeasible tag of {@code l}: "+∂", "-∂", "both" or "none". */
    public String partialTag(Literal l) {
      boolean p = plusPartial.contains(l);
      boolean m = minusPartial.contains(l);
      return p && m ? "both" : p ? "+∂" : m ? "-∂" : "none";
    }
  }
}
