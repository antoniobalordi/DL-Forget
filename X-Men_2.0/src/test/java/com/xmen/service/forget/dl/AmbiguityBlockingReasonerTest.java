package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DefeasibleTheory.Literal.neg;
import static com.xmen.service.forget.dl.DefeasibleTheory.Literal.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Conformance suite for ambiguity-blocking DL(∂) with team defeat (Antoniou et al. 2001). */
@DisplayName("DL(∂) conformance")
class AmbiguityBlockingReasonerTest {

  private final DefeasibleReasoner reasoner = new AmbiguityBlockingReasoner();

  private Conclusions run(DefeasibleTheory.Builder b) {
    return reasoner.reason(b.build());
  }

  private static String tags(Conclusions c, Literal l) {
    return (c.plusDelta().contains(l) ? "+D" : "") + (c.minusDelta().contains(l) ? "-D" : "")
        + (c.plusPartial().contains(l) ? "+d" : "") + (c.minusPartial().contains(l) ? "-d" : "");
  }

  @Test
  @DisplayName("facts are definite; atoms without rules are refuted at both levels")
  void factsAndUnsupportedAtoms() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("a")).declare("b"));
    assertEquals("+D+d", tags(c, pos("a")));
    assertEquals("-D-d", tags(c, pos("b")));
    assertEquals("-D-d", tags(c, neg("a")));
  }

  @Test
  @DisplayName("strict chains give +Δ; defeasible support gives +∂ but -Δ")
  void strictVersusDefeasible() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("a"))
        .strict("s", pos("b"), pos("a")).defeasible("r", pos("c"), pos("a")));
    assertEquals("+D+d", tags(c, pos("b")));
    assertEquals("-D+d", tags(c, pos("c")));
  }

  @Test
  @DisplayName("strict rule with a defeasible premise yields +∂ only (paper §7.1 on Usable)")
  void strictOverDefeasiblePremise() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("k"))
        .defeasible("d", pos("via"), pos("k")).strict("use", pos("usable"), pos("via")));
    assertEquals("-D+d", tags(c, pos("usable")));
  }

  @Test
  @DisplayName("an unresolved defeater blocks the conclusion without proving its complement")
  void defeaterBlocksWithoutProvingComplement() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("a"))
        .defeasible("r", pos("p"), pos("a")).defeater("w", neg("p"), pos("a")));
    assertEquals("-D-d", tags(c, pos("p")));
    assertEquals("-D-d", tags(c, neg("p")));
  }

  @Test
  @DisplayName("superior defeater: -∂p and -∂¬p (forgetting-defeater template)")
  void superiorDefeater() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("known")).fact(pos("lapsed"))
        .defeasible("d", pos("via"), pos("known")).defeater("w", neg("via"), pos("lapsed"))
        .superior("w", "d"));
    assertEquals("-D-d", tags(c, pos("via")));
    assertEquals("-D-d", tags(c, neg("via")));
  }

  @Test
  @DisplayName("inferior or inapplicable defeater does not block")
  void inapplicableDefeater() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("known")).declare("lapsed")
        .defeasible("d", pos("via"), pos("known")).defeater("w", neg("via"), pos("lapsed"))
        .superior("w", "d"));
    assertEquals("-D+d", tags(c, pos("via")));
  }

  @Test
  @DisplayName("reinstater superior to the defeater restores the conclusion")
  void reinstater() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("known")).fact(pos("lapsed"))
        .fact(pos("cue"))
        .defeasible("d", pos("via"), pos("known")).defeater("w", neg("via"), pos("lapsed"))
        .defeasible("rho", pos("via"), pos("cue")).superior("w", "d").superior("rho", "w"));
    assertEquals("-D+d", tags(c, pos("via")));
  }

  @Test
  @DisplayName("team defeat: two supports jointly beat two attackers; one alone does not")
  void teamDefeat() {
    DefeasibleTheory.Builder team = DefeasibleTheory.builder()
        .fact(pos("a")).fact(pos("b")).fact(pos("c")).fact(pos("e"))
        .defeasible("r1", pos("p"), pos("a")).defeasible("r2", pos("p"), pos("b"))
        .defeasible("s1", neg("p"), pos("c")).defeasible("s2", neg("p"), pos("e"))
        .superior("r1", "s1").superior("r2", "s2");
    assertEquals("-D+d", tags(run(team), pos("p")));
    assertEquals("-D-d", tags(run(team), neg("p")));

    DefeasibleTheory.Builder alone = DefeasibleTheory.builder()
        .fact(pos("a")).fact(pos("c")).fact(pos("e"))
        .defeasible("r1", pos("p"), pos("a"))
        .defeasible("s1", neg("p"), pos("c")).defeasible("s2", neg("p"), pos("e"))
        .superior("r1", "s1");
    assertEquals("-D-d", tags(run(alone), pos("p")));
  }

  @Test
  @DisplayName("ambiguity blocking: an ambiguous premise does not propagate")
  void ambiguityBlocking() {
    Conclusions c = run(DefeasibleTheory.builder()
        .defeasible("r1", pos("p")).defeasible("r2", neg("p"))
        .defeasible("r3", pos("q")).defeasible("r4", neg("q"), pos("p")));
    assertEquals("-D-d", tags(c, pos("p")));
    assertEquals("-D-d", tags(c, neg("p")));
    assertEquals("-D+d", tags(c, pos("q")), "blocking: r4 is discarded");
  }

  @Test
  @DisplayName("positive loops get neither tag (constructive negation)")
  void loopsAreUndecided() {
    Conclusions c = run(DefeasibleTheory.builder()
        .defeasible("r", pos("p"), pos("p")).strict("s", pos("q"), pos("q")));
    assertEquals("-D", tags(c, pos("p")));
    assertFalse(c.decided(pos("p")));
    assertEquals("", tags(c, pos("q")));
  }

  @Test
  @DisplayName("empty bodies, and +Δ of the complement refutes defeasibly")
  void emptyBodiesAndStrictOpposition() {
    Conclusions c = run(DefeasibleTheory.builder().fact(pos("a"))
        .strict("s", pos("p"), pos("a")).defeasible("r", neg("p"))
        .defeasible("t", pos("q")));
    assertEquals("+D+d", tags(c, pos("p")));
    assertEquals("-D-d", tags(c, neg("p")));
    assertEquals("-D+d", tags(c, pos("q")));
  }

  @Test
  @DisplayName("defeaters are not team members in (2.3.2)")
  void defeaterIsNotATeamMember() {
    Conclusions c = run(DefeasibleTheory.builder()
        .defeasible("r", pos("p")).defeater("dp", pos("p")).defeasible("s", neg("p"))
        .superior("dp", "s"));
    assertEquals("-D-d", tags(c, pos("p")));
  }

  @Test
  @DisplayName("theory validation: duplicate labels, cyclic or ill-typed superiority")
  void validation() {
    assertThrows(IllegalArgumentException.class, () -> DefeasibleTheory.builder()
        .defeasible("r", pos("p")).defeasible("r", pos("q")));
    assertThrows(IllegalArgumentException.class, () -> DefeasibleTheory.builder()
        .defeasible("r", pos("p")).defeasible("s", neg("p"))
        .superior("r", "s").superior("s", "r").build());
    assertThrows(IllegalArgumentException.class, () -> DefeasibleTheory.builder()
        .defeasible("r", pos("p")).defeasible("s", pos("q")).superior("r", "s").build());
    assertThrows(IllegalArgumentException.class, () -> DefeasibleTheory.builder()
        .defeasible("r", pos("p")).superior("r", "missing").build());
  }

  @Test
  @DisplayName("toText is deterministic and insertion-order independent")
  void deterministicText() {
    String t1 = DefeasibleTheory.builder().fact(pos("b")).fact(pos("a"))
        .defeasible("r2", pos("q")).defeasible("r1", neg("q")).superior("r2", "r1").build().toText();
    String t2 = DefeasibleTheory.builder().fact(pos("a")).fact(pos("b"))
        .defeasible("r1", neg("q")).defeasible("r2", pos("q")).superior("r2", "r1").build().toText();
    assertEquals(t1, t2);
    assertTrue(t1.contains("sup r2 > r1"));
  }
}
