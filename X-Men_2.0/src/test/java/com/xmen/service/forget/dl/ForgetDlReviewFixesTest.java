package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Re-acquisition, Neglect and candidate generation as revised after review (R1–R3). */
@DisplayName("Review fixes")
class ForgetDlReviewFixesTest {

  private final ForgetDlEngine engine = new ForgetDlEngine();

  // Running example of the CAV paper: c = aenc(<nH,nB>, pk(skH)), m3 = senc(<H,pw1>, k(nH,nB)).
  private final Message h = a("H");
  private final Message b1 = a("B1");
  private final Message b2 = a("B2");
  private final Message nh = a("nH");
  private final Message nb = a("nB");
  private final Message pw1 = a("pw1");
  private final Message pw2 = a("pw2");
  private final Message skH = a("skH");
  private final Message c = aenc(pair(nh, nb), skH);
  private final Set<Message> bankK = Set.of(h, b1, b2, nh, pw1, pw2, skH);

  private Message credential(Message pw) {
    return senc(pair(h, pw), fn("k", nh, nb));
  }

  private Map<Message, String> labels() {
    Map<Message, String> l = new LinkedHashMap<>();
    l.put(h, "agentname");
    l.put(nh, "nonce");
    l.put(skH, "key");
    l.put(pw1, "password");
    l.put(pw2, "password");
    return l;
  }

  private static CueModel present(String cue, Message m) {
    return new CueModel(Set.of(cue), Set.of(new Association(cue, m)), Set.of(cue));
  }

  private static TransitionInput.Builder from(Set<Message> k, Set<Message> forget) {
    return TransitionInput.builder(new HumanState(k, forget, ProceduralState.EMPTY));
  }

  // ---------------- R1.1: lapse timing (Table 1, S8) ----------------

  @Test
  @DisplayName("R1.1/S8: a lapse at the transition that receives c is not undone by c")
  void s8LapseAtReceivingTransitionStays() {
    ForgetDlResult r = engine.run(from(bankK, Set.of()).received(c).forget(pw1).forget(nh)
        .send(credential(pw1)).labels(labels()).cues(present("hint", pw1)).build());
    assertEquals(Decision.PRESERVE, r.decision(), "pi_b survives with the hint");
    assertEquals(Set.of(nh, pw1), r.after().forget());
    assertTrue(r.reacquired().isEmpty());
  }

  @Test
  @DisplayName("R1.1/S8: the same lapse made at an earlier transition is re-acquired from c")
  void s8EarlierLapseIsReacquired() {
    ForgetDlResult r = engine.run(from(bankK, Set.of(nh)).received(c).forget(pw1)
        .send(credential(pw1)).labels(labels()).cues(present("hint", pw1)).build());
    assertEquals(Decision.PRESERVE, r.decision());
    assertEquals(Set.of(nh), r.reacquired(), "so pi_a also survives: Table 1 must fix the timing");
    assertEquals(Set.of(pw1), r.after().forget());
  }

  // ---------------- R2.1-R2.3: the Neglect test ----------------

  @Test
  @DisplayName("R2.1: a value forgotten at an earlier transition neglects a check that reads it")
  void earlierLapseTriggersNeglect() {
    Message check = fn("Eq", nh, a("nH_recv"));
    ForgetDlResult r = engine.run(from(Set.of(nh, h), Set.of(nh)).received(a("nH_recv"))
        .internalAction(check).send(h).build());
    assertEquals(Decision.NEGLECT, r.decision());
    assertEquals(Set.of(nh), r.neglected());
  }

  @Test
  @DisplayName("R2.2: re-acquisition precedes the test, so a check on a re-received message stays")
  void reReceivedMessageIsCheckable() {
    Message token = a("tok");
    ForgetDlResult r = engine.run(from(Set.of(h, token), Set.of(token)).received(token)
        .internalAction(fn("Check", token)).send(h).build());
    assertNotEquals(Decision.NEGLECT, r.decision());
    assertEquals(Set.of(token), r.reacquired());
    assertTrue(r.after().forget().isEmpty());
  }

  @Test
  @DisplayName("R2.3: a present cue makes a forgotten operand accessible, so the check stays")
  void cuedOperandIsNotNeglected() {
    ForgetDlResult r = engine.run(from(Set.of(nh, h), Set.of(nh)).received(a("nH_recv"))
        .internalAction(fn("Eq", nh, a("nH_recv"))).cues(present("note", nh)).send(h).build());
    assertNotEquals(Decision.NEGLECT, r.decision());
  }

  @Test
  @DisplayName("R2.3: under Case 2 a component of a forgotten pair is blocked for checks as for sends")
  void closureBlocksCheckOperand() {
    Message stored = pair(nh, h);
    ForgetDlResult r = engine.run(from(Set.of(stored, nh, h), Set.of(stored))
        .closure(BlockingMode.CASE2_PAIRING).received(a("nH_recv"))
        .internalAction(fn("Eq", nh, a("nH_recv"))).build());
    assertEquals(Decision.NEGLECT, r.decision());
    assertTrue(r.neglected().contains(nh));
  }

  @Test
  @DisplayName("R2.2: a Neglect transition still re-acquires, so its successor state is right")
  void neglectTransitionStillReacquires() {
    ForgetDlResult r = engine.run(from(Set.of(pw1, pw2), Set.of(pw2)).received(pw2)
        .forget(pw1).internalAction(pw1).build());
    assertEquals(Decision.NEGLECT, r.decision());
    assertEquals(Set.of(pw2), r.reacquired());
    assertEquals(Set.of(pw1), r.after().forget());
  }

  // ---------------- R3.1: cued hypotheses are kept, not replaced ----------------

  @Test
  @DisplayName("R3.1: autofill restores H, no hint for pw1 → Replace with <H,pw2>, not Skip")
  void cuedHypothesisIsKept() {
    ForgetDlResult r = engine.run(from(bankK, Set.of()).received(c).forget(h).forget(pw1)
        .send(credential(pw1)).labels(labels()).cues(present("autofill", h)).build());
    assertEquals(Decision.REPLACE, r.decision());
    assertEquals(List.of(credential(pw2)),
        r.replacements().stream().map(ForgetDlResult.Candidate::target).toList());
    assertEquals(Map.of(pw1, pw2), r.replacements().get(0).substitutions().get(0));
  }

  @Test
  @DisplayName("R3.1: without cues every blocked hypothesis is replaced, as in the published algorithm")
  void uncuedHypothesesAreAllReplaced() {
    ForgetDlResult r = engine.run(from(bankK, Set.of()).received(c).forget(h).forget(pw1)
        .send(credential(pw1)).labels(labels()).build());
    assertEquals(Decision.SKIP, r.decision(), "R_H is empty: H has a unique label");
  }

  // ---------------- R1.2: re-acquisition over the finite basis ----------------

  @Test
  @DisplayName("R1.2: a receipt used only inside a cycle does not re-acquire, and is not Unknown")
  void cyclicReceiptDoesNotReacquire() {
    Message ha = fn("h", a("a"));
    ForgetDlResult r = engine.run(from(Set.of(a("a"), ha), Set.of(ha))
        .received(senc(a("a"), a("a"))).send(ha).build());
    assertEquals(Decision.PRESERVE, r.decision(), "h(a) is rebuilt from a without the receipt");
    assertEquals(Set.of(ha), r.after().forget());
  }

  @Test
  @DisplayName("R1.2: with a forgotten procedure a cyclic receipt witness may matter, so Unknown")
  void cyclicReceiptUnderProcedureLapseIsUnknown() {
    Message x = a("a"), ha = fn("h", x);
    String direct = new NormalDerivations(Set.of(), NormalDerivations.Limits.DEFAULT)
        .enumerate(ha, Set.of(x)).derivations().stream().map(NormalDerivations::procedureId)
        .findFirst().orElseThrow();
    HumanState state = new HumanState(Set.of(x, ha), Set.of(ha),
        new ProceduralState(Set.of(), Set.of(direct)));
    ForgetDlResult r = engine.run(TransitionInput.builder(state).received(senc(x, x)).send(x)
        .build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision(),
        "h(sdec(senc(a,a),a)) avoids the forgotten h(a) and lies outside the finite basis");
  }
}
