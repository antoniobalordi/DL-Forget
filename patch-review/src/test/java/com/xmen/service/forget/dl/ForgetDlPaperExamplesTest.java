package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static com.xmen.service.forget.dl.DlFixtures.sign;
import static com.xmen.service.forget.dl.DlFixtures.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import com.xmen.service.forget.dl.ForgetDlResult.BlockerKind;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import com.xmen.service.forget.dl.ForgetDlResult.DerivationTrace;
import com.xmen.service.forget.dl.ForgetDlResult.TargetTrace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Paper examples 7–10, Lemma 1, §7.8 and the extended examples of §8 (Tables 3 and 4). */
@DisplayName("Paper examples (Sections 7-8)")
class ForgetDlPaperExamplesTest {

  private final ForgetDlEngine engine = new ForgetDlEngine();
  private final Message m1 = a("m1");
  private final Message m2 = a("m2");

  private static HumanState state(Set<Message> k, Set<Message> forget, ProceduralState p) {
    return new HumanState(k, forget, p);
  }

  private static CueModel cue(String c, Message m, boolean present) {
    return new CueModel(Set.of(c), Set.of(new Association(c, m)), present ? Set.of(c) : Set.of());
  }

  private static TargetTrace trace(ForgetDlResult r, Message t) {
    return r.trace().stream().filter(x -> x.target().equals(t)).findFirst().orElseThrow();
  }

  // ---------------- §7.4, Examples 7-9 ----------------

  @Test
  @DisplayName("Example 7: the decryption branch is blocked, the direct branch survives")
  void example7() {
    Message c = senc(m1, m2);
    for (BlockingMode mode : BlockingMode.values()) {
      ForgetDlResult r = engine.run(TransitionInput.builder(
          state(Set.of(m1, c, m2), Set.of(c), ProceduralState.EMPTY)).send(m1).closure(mode).build());
      assertEquals(Decision.PRESERVE, r.decision(), mode.name());
      assertEquals(Set.of(c), r.compiled().lapsedMessages(), "same Lapsed set in every case");
      TargetTrace t = trace(r, m1);
      assertEquals("+∂", t.usableTag());
      DerivationTrace dec = t.derivations().stream()
          .filter(d -> d.hypotheses().contains(c)).findFirst().orElseThrow();
      assertEquals("-∂", dec.viaTag());
      assertEquals(BlockerKind.DECLARATIVE, dec.standing().get(0).kind());
    }
  }

  @Test
  @DisplayName("Example 8: Cases 1-2 keep m1 usable; Case 3 closure blocks the direct branch")
  void example8() {
    Message c = senc(m1, m2);
    Set<Message> k = Set.of(m1, c, m2);
    Set<Message> f = Set.of(c, m2);
    assertEquals("+∂", DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK, ProceduralState.EMPTY,
        CueModel.NONE, m1).usable(m1));
    assertEquals("+∂", DlFixtures.compile(k, f, BlockingMode.CASE2_PAIRING, ProceduralState.EMPTY,
        CueModel.NONE, m1).usable(m1));
    DlFixtures.Compiled c3 = DlFixtures.compile(k, f, BlockingMode.CASE3_FULL_DY,
        ProceduralState.EMPTY, CueModel.NONE, m1);
    assertEquals("-∂", c3.usable(m1));
    assertEquals(Set.of(m1, c, m2), c3.compiled().lapsedMessages());
  }

  @Test
  @DisplayName("Example 9: a forgotten pair is harmless in Case 1, blocks both components in Cases 2-3")
  void example9() {
    Message p = pair(m1, m2);
    Set<Message> k = Set.of(p, m1, m2);
    DlFixtures.Compiled c1 = DlFixtures.compile(k, Set.of(p), BlockingMode.CASE1_WEAK,
        ProceduralState.EMPTY, CueModel.NONE, m1, m2);
    assertEquals("+∂", c1.usable(m1));
    assertEquals("+∂", c1.usable(m2));
    for (BlockingMode mode : List.of(BlockingMode.CASE2_PAIRING, BlockingMode.CASE3_FULL_DY)) {
      DlFixtures.Compiled c = DlFixtures.compile(k, Set.of(p), mode, ProceduralState.EMPTY,
          CueModel.NONE, m1, m2);
      assertEquals("-∂", c.usable(m1), mode.name());
      assertEquals("-∂", c.usable(m2), mode.name());
    }
  }

  // ---------------- §7.6, Example 10 ----------------

  private final Message h = a("H");
  private final Message pw = a("PW");
  private final Message skH = a("skH");
  private final Message nh = a("NH");
  private final Message nb = a("NB");
  private final Message challenge = aenc(pair(nh, nb), skH);
  private final Message credential = senc(pair(h, pw), pair(nh, nb));
  private final Set<Message> bankK = Set.of(h, pw, skH, nh, challenge);

  @Test
  @DisplayName("Example 10a: forgetting r_senc blocks the unique derivation → Skip")
  void example10RuleLapse() {
    ForgetDlResult r = engine.run(TransitionInput.builder(
        state(bankK, Set.of(), new ProceduralState(Set.of("senc"), Set.of()))).send(credential).build());
    assertEquals(Decision.SKIP, r.decision());
    assertEquals(1, r.candidates().size(), "T = {m2}");
    DerivationTrace d = trace(r, credential).derivations().get(0);
    assertEquals(BlockerKind.RULE, d.standing().get(0).kind());
    assertEquals("senc", d.standing().get(0).subject());
  }

  @Test
  @DisplayName("Example 10b: forgetting π as a whole → Skip, by a procedure-level defeater")
  void example10ProcedureLapse() {
    String pid = NormalDerivations.procedureId(
        new NormalDerivations().enumerate(credential, bankK).derivations().get(0));
    ForgetDlResult r = engine.run(TransitionInput.builder(
        state(bankK, Set.of(), new ProceduralState(Set.of(), Set.of(pid)))).send(credential).build());
    assertEquals(Decision.SKIP, r.decision());
    assertEquals(BlockerKind.PROCEDURE,
        trace(r, credential).derivations().get(0).standing().get(0).kind());
  }

  @Test
  @DisplayName("§7.2 scope: a rule lapse is global, a procedure lapse blocks only the named tree")
  void ruleVersusProcedureScope() {
    Message n1 = a("n1"), n2 = a("n2"), key = a("k");
    Set<Message> k = Set.of(senc(pair(n1, n2), key), key);
    String piN1 = NormalDerivations.procedureId(
        new NormalDerivations().enumerate(n1, k).derivations().get(0));
    DlFixtures.Compiled rule = DlFixtures.compile(k, Set.of(), BlockingMode.CASE1_WEAK,
        new ProceduralState(Set.of("sdec"), Set.of()), CueModel.NONE, n1, n2);
    assertEquals("-∂", rule.usable(n1));
    assertEquals("-∂", rule.usable(n2));
    DlFixtures.Compiled proc = DlFixtures.compile(k, Set.of(), BlockingMode.CASE1_WEAK,
        new ProceduralState(Set.of(), Set.of(piN1)), CueModel.NONE, n1, n2);
    assertEquals("-∂", proc.usable(n1));
    assertEquals("+∂", proc.usable(n2));
    Set<Message> withN1 = Set.of(senc(pair(n1, n2), key), key, n1);
    assertEquals("+∂", DlFixtures.compile(withN1, Set.of(), BlockingMode.CASE1_WEAK,
        new ProceduralState(Set.of("sdec", "proj"), Set.of()), CueModel.NONE, n1).usable(n1),
        "the direct derivation survives either lapse");
  }

  // ---------------- §7.5, Lemma 1 ----------------

  @Test
  @DisplayName("Lemma 1: one matching present cue per blocked hypothesis is necessary and sufficient")
  void lemma1() {
    Message x = a("x"), y = a("y");
    Message t = pair(x, y);
    Set<Message> k = Set.of(x, y);
    Set<Message> f = Set.of(x, y);
    Set<Association> assoc = Set.of(new Association("cx", x), new Association("cy", y));
    Set<String> cues = Set.of("cx", "cy");
    String none = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK, ProceduralState.EMPTY,
        new CueModel(cues, assoc, Set.of()), t).usable(t);
    String onlyX = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK, ProceduralState.EMPTY,
        new CueModel(cues, assoc, Set.of("cx")), t).usable(t);
    String both = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK, ProceduralState.EMPTY,
        new CueModel(cues, assoc, cues), t).usable(t);
    String crossed = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK, ProceduralState.EMPTY,
        new CueModel(cues, Set.of(new Association("cx", x), new Association("cy", x)), cues), t)
        .usable(t);
    String procedural = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK,
        new ProceduralState(Set.of("pair"), Set.of()), new CueModel(cues, assoc, cues), t).usable(t);
    assertEquals(List.of("-∂", "-∂", "+∂", "-∂", "-∂"),
        List.of(none, onlyX, both, crossed, procedural));
  }

  // ---------------- §7.8 expressiveness ----------------

  @Test
  @DisplayName("§7.8: a forgotten procedure is a state no Case-1 Forget set can represent")
  void proceduralSeparation() {
    Message c = senc(m1, m2);
    Set<Message> k = Set.of(c, m2);
    String pid = NormalDerivations.procedureId(
        new NormalDerivations().enumerate(m1, k).derivations().get(0));
    DlFixtures.Compiled dl = DlFixtures.compile(k, Set.of(), BlockingMode.CASE1_WEAK,
        new ProceduralState(Set.of(), Set.of(pid)), CueModel.NONE, m1, c, m2);
    List<String> target = List.of(dl.usable(m1), dl.usable(c), dl.usable(m2));
    assertEquals(List.of("-∂", "+∂", "+∂"), target);
    for (Set<Message> f : List.of(Set.<Message>of(), Set.of(c), Set.of(m2), Set.of(c, m2))) {
      DlFixtures.Compiled fs = DlFixtures.compile(k, f, BlockingMode.CASE1_WEAK,
          ProceduralState.EMPTY, CueModel.NONE, m1, c, m2);
      assertFalse(target.equals(List.of(fs.usable(m1), fs.usable(c), fs.usable(m2))),
          "Forget = " + f);
    }
  }

  @Test
  @DisplayName("§7.8: a cue restores accessibility while the lapse stays recorded")
  void cueSeparation() {
    Message mb = a("mbar");
    ForgetDlResult r = engine.run(TransitionInput.builder(state(Set.of(mb), Set.of(mb),
        ProceduralState.EMPTY)).send(mb).cues(cue("c", mb, true)).build());
    assertEquals(Decision.PRESERVE, r.decision());
    assertEquals(Set.of(mb), r.after().forget());
    assertTrue(r.compiled().theory().facts().contains(ForgetDlCompiler.lapsedAtom(mb)));
  }

  // ---------------- §8.1 Bank Login extension, Table 3 ----------------

  private Map<Message, String> bankLabels(Message... passwords) {
    Map<Message, String> l = new LinkedHashMap<>();
    l.put(h, "agentname");
    l.put(nh, "nonce");
    l.put(skH, "key");
    for (Message p : passwords) {
      l.put(p, "password");
    }
    return l;
  }

  @Test
  @DisplayName("Table 3, no recovery: the credential is blocked and no replacement exists → Skip")
  void table3NoRecovery() {
    ForgetDlResult r = engine.run(TransitionInput.builder(state(bankK, Set.of(pw),
        ProceduralState.EMPTY)).send(credential).labels(bankLabels(pw)).build());
    assertEquals(Decision.SKIP, r.decision());
    assertEquals("-∂", trace(r, credential).usableTag());
  }

  @Test
  @DisplayName("Table 3, hint: cue reinstates PW; Forget and K unchanged; lapse returns without cue")
  void table3Hint() {
    Message hint = a("hintPW");
    ForgetDlResult r = engine.run(TransitionInput.builder(state(bankK, Set.of(pw),
            ProceduralState.EMPTY)).received(hint).send(credential).labels(bankLabels(pw))
        .cues(cue("c_hint", pw, true)).build());
    assertEquals(Decision.PRESERVE, r.decision());
    assertEquals(Set.of(pw), r.after().forget(), "hint is not re-acquisition");
    assertTrue(r.reacquired().isEmpty());
    DerivationTrace d = trace(r, credential).derivations().get(0);
    assertTrue(d.standing().isEmpty());
    assertEquals(1, d.blockers().size());
    assertTrue(d.blockers().get(0).overruledBy().get(0).startsWith("rho:"));

    ForgetDlResult later = engine.run(TransitionInput.builder(r.after()).send(credential)
        .labels(bankLabels(pw)).cues(cue("c_hint", pw, false)).build());
    assertEquals(Decision.SKIP, later.decision(), "within-state recovery only");
  }

  @Test
  @DisplayName("Table 3, reset: old credential stays blocked, fresh credential usable → Replace")
  void table3Reset() {
    Message pwNew = a("PWnew");
    Message fresh = senc(pair(h, pwNew), pair(nh, nb));
    ForgetDlResult r = engine.run(TransitionInput.builder(state(bankK, Set.of(pw),
            ProceduralState.EMPTY)).received(pwNew).send(credential)
        .labels(bankLabels(pw, pwNew)).build());
    assertEquals(Decision.REPLACE, r.decision());
    assertEquals(fresh, r.replacements().get(0).target());
    assertEquals("-∂", trace(r, credential).usableTag());
    assertEquals("+∂", trace(r, fresh).usableTag());
    assertEquals(Set.of(pw), r.after().forget());
  }

  @Test
  @DisplayName("§2.1 attack: forgetting PW1 with PW2 known triggers Replace with PW2")
  void bankLoginAttack() {
    Message pw1 = a("PW1"), pw2 = a("PW2");
    Message intended = senc(pair(h, pw1), pair(nh, nb));
    ForgetDlResult r = engine.run(TransitionInput.builder(HumanState.of(
            Set.of(h, pw1, pw2, skH, nh))).received(challenge).forget(pw1).send(intended)
        .labels(bankLabels(pw1, pw2)).build());
    assertEquals(Decision.REPLACE, r.decision());
    assertEquals(List.of(senc(pair(h, pw2), pair(nh, nb))),
        r.replacements().stream().map(ForgetDlResult.Candidate::target).toList());
    assertEquals(List.of(Map.of(pw1, pw2)), r.replacements().get(0).substitutions());
  }

  // ---------------- §8.2 Invoice-Payment, Table 4 ----------------

  private final Message hh = a("H");
  private final Message bank = a("B");
  private final Message sup = a("S");
  private final Message srv = a("srv");
  private final Message amt = a("amt");
  private final Message accS = a("AccS");
  private final Message accAtt = a("AccAtt");
  private final Message skHi = a("skH");
  private final Message skB = a("skB");
  private final Set<Message> invoiceK =
      Set.of(hh, bank, sup, srv, amt, accS, accAtt, skHi, DlFixtures.pk(skB));

  private Message order(Message acc) {
    return aenc(sign(tuple(hh, bank, sup, srv, amt, acc), skHi), skB);
  }

  private Map<Message, String> invoiceLabels() {
    Map<Message, String> l = new LinkedHashMap<>();
    l.put(hh, "agentname");
    l.put(bank, "agentname");
    l.put(sup, "agentname");
    l.put(srv, "service");
    l.put(amt, "amount");
    l.put(accS, "account");
    l.put(accAtt, "account");
    return l;
  }

  private ForgetDlResult invoice(ProceduralState p, boolean lapse, CueModel cues) {
    TransitionInput.Builder b = TransitionInput.builder(new HumanState(invoiceK, Set.of(), p))
        .send(order(accS)).labels(invoiceLabels()).cues(cues);
    if (lapse) {
      b.forget(accS);
    }
    return engine.run(b.build());
  }

  @Test
  @DisplayName("Table 4: all four rows")
  void table4() {
    ForgetDlResult noLapse = invoice(ProceduralState.EMPTY, false, CueModel.NONE);
    assertEquals(Decision.PRESERVE, noLapse.decision());
    assertEquals(1, noLapse.candidates().size());

    ForgetDlResult lapse = invoice(ProceduralState.EMPTY, true, CueModel.NONE);
    assertEquals(Decision.REPLACE, lapse.decision());
    assertEquals(order(accAtt), lapse.replacements().get(0).target());
    assertEquals(2, lapse.candidates().size());

    ForgetDlResult cued = invoice(ProceduralState.EMPTY, true, cue("cS", accS, true));
    assertEquals(Decision.PRESERVE, cued.decision());
    assertEquals("+∂", trace(cued, order(accAtt)).usableTag(), "usable but not emitted");
    assertTrue(cued.replacements().isEmpty());

    ForgetDlResult signLost = invoice(new ProceduralState(Set.of("sign"), Set.of()), true,
        cue("cS", accS, true));
    assertEquals(Decision.SKIP, signLost.decision());
    assertEquals(2, signLost.candidates().size(), "same T: variant space depends on Forget only");
  }

  @Test
  @DisplayName("§8.2 with the received reminder: several derivations, same decisions")
  void invoiceWithReceivedReminder() {
    Message reminder = tuple(sup, hh, srv, amt);
    Set<Message> k = Set.of(hh, bank, sup, accS, accAtt, skHi, DlFixtures.pk(skB));
    ForgetDlResult r = engine.run(TransitionInput.builder(HumanState.of(k)).received(reminder)
        .forget(accS).send(order(accS)).labels(invoiceLabels()).build());
    assertEquals(Decision.REPLACE, r.decision());
    assertTrue(trace(r, order(accS)).derivations().size() > 1);
    assertEquals(order(accAtt), r.replacements().get(0).target());
  }
}
