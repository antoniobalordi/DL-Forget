package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
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
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import com.xmen.service.forget.dl.ForgetDlResult.Status;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Algorithm 2 state update, re-acquisition, candidate generation and coverage handling. */
@DisplayName("Algorithm 2 engine")
class ForgetDlEngineTest {

  private final ForgetDlEngine engine = new ForgetDlEngine();
  private final Message h = a("H");
  private final Message pw1 = a("pw1");
  private final Message pw2 = a("pw2");

  private TransitionInput.Builder in(Set<Message> k, Set<Message> f) {
    return TransitionInput.builder(new HumanState(k, f, ProceduralState.EMPTY));
  }

  @Test
  @DisplayName("erratum E1: forgetting an old message keeps the just-received message (Alg. 1 l.6)")
  void erratumReceivedMessageIsKept() {
    Message m1 = a("m1");
    ForgetDlResult r = engine.run(in(Set.of(pw1, h), Set.of()).received(m1).forget(pw1).build());
    assertEquals(Decision.NO_SEND, r.decision());
    assertTrue(r.after().knowledge().containsAll(Set.of(pw1, h, m1)));
    assertEquals(Set.of(pw1), r.after().forget());
  }

  @Test
  @DisplayName("Algorithm 2: forgetting the received message keeps K and records the lapse")
  void forgetJustReceived() {
    Message m1 = a("m1");
    ForgetDlResult r = engine.run(in(Set.of(a("m2"), a("m4")), Set.of()).received(m1).forget(m1)
        .build());
    assertEquals(Set.of(a("m2"), a("m4")), r.after().knowledge());
    assertEquals(Set.of(m1), r.after().forget());
    ForgetDlResult again = engine.run(in(Set.of(pw1, h), Set.of(pw1)).received(pw1).forget(pw1)
        .build());
    assertEquals(Set.of(pw1), again.after().forget(), "not a re-acquisition (contrast host D7)");
  }

  @Test
  @DisplayName("lines 5-6: a lapsed message used in an internal action triggers Neglect and returns")
  void neglect() {
    Message nh = a("nH");
    ForgetDlResult r = engine.run(in(Set.of(nh, h), Set.of()).forget(nh)
        .internalAction(fn("Eq", nh, a("nH_recv"))).send(pair(h, nh)).build());
    assertEquals(Decision.NEGLECT, r.decision());
    assertEquals(Set.of(nh), r.neglected());
    assertEquals(Set.of(nh), r.after().forget());
    assertTrue(r.candidates().isEmpty());
  }

  @Test
  @DisplayName("Neglect retains explicit competence recovery and declarative re-acquisition")
  void neglectStillAppliesExplicitProceduralRecovery() {
    String procedure = "pi_0123456789abcdef";
    HumanState before = new HumanState(Set.of(pw1, pw2), Set.of(pw2),
        new ProceduralState(Set.of("h", "sdec"), Set.of(procedure)));
    ForgetDlResult r = engine.run(TransitionInput.builder(before)
        .received(pw2).forget(pw1).internalAction(pw1)
        .lapse(new ForgetDlModel.RuleLapse("h"))
        .relearnRule("h").relearnProcedure(procedure).build());
    assertEquals(Decision.NEGLECT, r.decision());
    assertEquals(Set.of("sdec"), r.after().procedural().forgottenRules());
    assertTrue(r.after().procedural().forgottenProcedures().isEmpty());
    assertEquals(Set.of(pw1), r.after().forget(), "the re-received pw2 leaves Forget (A8)");
    assertEquals(Set.of(pw2), r.reacquired());
  }

  @Test
  @DisplayName("Example 3: re-acquisition by receipt, by a key, or via a received ciphertext")
  void reacquisition() {
    Message m1 = a("m1"), m2 = a("m2"), m4 = a("m4"), m5 = a("m5");
    Set<Message> k = Set.of(senc(pair(m2, m4), m5), m2, m4, m1);
    assertEquals(Set.of(m2), engine.run(in(k, Set.of(m2)).received(m2).build()).reacquired());
    ForgetDlResult byKey = engine.run(in(k, Set.of(m2)).received(m5).build());
    assertEquals(Set.of(m2), byKey.reacquired());
    assertTrue(byKey.after().forget().isEmpty());
    assertEquals(Set.of(m2),
        engine.run(in(k, Set.of(m2)).received(senc(m5, m1)).build()).reacquired());
    assertEquals(Set.of(m2), engine.run(in(k, Set.of(m2)).received(a("x")).build()).after().forget());
    ForgetDlResult same = engine.run(in(k, Set.of()).received(m5).forget(m2).build());
    assertEquals(Set.of(m2), same.after().forget(), "A6: a lapse is not undone in its own transition");
  }

  @Test
  @DisplayName("A6: an unrelated receipt does not clear a lapse that stays derivable another way")
  void reacquisitionNeedsReceiptProvenance() {
    Message m1 = a("m1"), k = a("k");
    Set<Message> kn = Set.of(m1, senc(m1, k), k);
    ForgetDlResult r = engine.run(in(kn, Set.of(m1)).received(a("x")).send(m1).build());
    assertEquals(Set.of(m1), r.after().forget(), "retrieval lapse stays recorded");
    assertEquals(Decision.PRESERVE, r.decision(), "usable through the decryption branch");
  }

  @Test
  @DisplayName("§7.6: procedural lapses persist; explicit relearning removes them; cues do not")
  void proceduralStateUpdate() {
    HumanState s = new HumanState(Set.of(h), Set.of(), new ProceduralState(Set.of("sdec"), Set.of()));
    ForgetDlResult r = engine.run(TransitionInput.builder(s)
        .lapse(new ForgetDlModel.RuleLapse("pair")).build());
    assertEquals(Set.of("pair", "sdec"), r.after().procedural().forgottenRules());
    ForgetDlResult rel = engine.run(TransitionInput.builder(r.after()).relearnRule("sdec").build());
    assertEquals(Set.of("pair"), rel.after().procedural().forgottenRules());
    ForgetDlResult cued = engine.run(TransitionInput.builder(r.after()).send(pair(h, h))
        .cues(new CueModel(Set.of("c"), Set.of(new Association("c", h)), Set.of("c"))).build());
    assertEquals(Decision.SKIP, cued.decision(), "message cues do not restore competences");
  }

  @Test
  @DisplayName("invalid input and unsupported algebra are explicit failures")
  void failures() {
    assertEquals(Status.INVALID_INPUT,
        engine.run(in(Set.of(h), Set.of()).forget(pw1).send(h).build()).status());
    assertEquals(Status.INVALID_INPUT, engine.run(in(Set.of(h), Set.of()).send(h)
        .cues(new CueModel(Set.of("c"), Set.of(new Association("c", pw1)), Set.of())).build())
        .status());
    assertEquals(Status.INVALID_INPUT, engine.run(in(Set.of(h), Set.of()).send(h)
        .cues(new CueModel(Set.of(), Set.of(), Set.of("ghost"))).build()).status());
    ForgetDlResult x = engine.run(in(Set.of(h), Set.of()).send(fn("xor", h, h)).build());
    assertEquals(Status.UNSUPPORTED, x.status());
    assertEquals(Decision.NONE, x.decision());
    assertEquals(Status.UNSUPPORTED, engine.run(in(Set.of(h), Set.of())
        .internalAction(fn("verify", h, h)).build()).status());
    assertEquals(Status.UNSUPPORTED, engine.run(in(Set.of(h), Set.of())
        .cues(new CueModel(Set.of("c"), Set.of(new Association("c", fn("xor", h, h))), Set.of()))
        .build()).status());
  }

  @Test
  @DisplayName("truncated enumeration yields UNKNOWN_COVERAGE, never Skip")
  void coverage() {
    ForgetDlEngine tiny = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(1, 1_000), 256));
    Message t = pair(pair(h, pw1), pair(h, pw1));
    Set<Message> k = Set.of(h, pw1, pair(h, pw1));
    ForgetDlResult r = tiny.run(in(k, Set.of(pw1, pair(h, pw1))).send(t).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertEquals(Decision.SKIP, engine.run(in(k, Set.of(pw1, pair(h, pw1))).send(t).build())
        .decision());
  }

  @Test
  @DisplayName("candidate cap: justified replacements are emitted but marked partial")
  void candidateCap() {
    Set<Message> k = new LinkedHashSet<>(List.of(h, pw1, pw2, a("pw3"), a("pw4")));
    TransitionInput.Builder b = in(k, Set.of()).forget(pw1).send(pair(h, pw1));
    k.stream().filter(m -> !m.equals(h)).forEach(m -> b.label(m, "password"));
    ForgetDlEngine capped = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(NormalDerivations.Limits.DEFAULT, 2));
    ForgetDlResult r = capped.run(b.build());
    assertEquals(Decision.REPLACE, r.decision());
    assertTrue(r.partial());
    assertEquals(1, r.replacements().size());
    ForgetDlResult full = engine.run(b.build());
    assertEquals(3, full.replacements().size());
    assertFalse(full.partial());
  }

  @Test
  @DisplayName("A4: a blocked hypothesis without a label has no replacement")
  void labelRequired() {
    ForgetDlResult r = engine.run(in(Set.of(h, pw1, pw2), Set.of()).forget(pw1)
        .send(pair(h, pw1)).label(pw2, "password").build());
    assertEquals(Decision.SKIP, r.decision());
  }

  @Test
  @DisplayName("format: replacements must share the top-level symbol")
  void formatRequired() {
    Message hashed = fn("h", pw2);
    ForgetDlResult r = engine.run(in(Set.of(h, pw1, hashed), Set.of()).forget(pw1)
        .send(pair(h, pw1)).label(pw1, "password").label(hashed, "password").build());
    assertEquals(Decision.SKIP, r.decision());
  }

  @Test
  @DisplayName("§7.7 Case 2: a closure-blocked replacement is generated but not emitted")
  void closureBlockedVariant() {
    Message salt = a("salt");
    Set<Message> k = Set.of(h, pw1, pw2, pair(pw2, salt));
    ForgetDlResult r = engine.run(in(k, Set.of(pair(pw2, salt))).forget(pw1).send(pair(h, pw1))
        .closure(BlockingMode.CASE2_PAIRING).label(pw1, "password").label(pw2, "password").build());
    assertEquals(2, r.candidates().size(), "pw2 passes the syntactic test pw2 ∉ Forget");
    assertEquals(Decision.SKIP, r.decision(), "Algorithm 1 would Replace here");
    ForgetDlResult case1 = engine.run(in(k, Set.of(pair(pw2, salt))).forget(pw1)
        .send(pair(h, pw1)).label(pw1, "password").label(pw2, "password").build());
    assertEquals(Decision.REPLACE, case1.decision());
  }

  @Test
  @DisplayName("A5: a closure-blocked hypothesis is never replaced by itself")
  void noSelfReplacement() {
    Message salt = a("salt");
    Set<Message> k = Set.of(h, pw1, pw2, pair(pw1, salt));
    ForgetDlResult r = engine.run(in(k, Set.of(pair(pw1, salt))).send(pair(h, pw1))
        .closure(BlockingMode.CASE2_PAIRING).label(pw1, "password").label(pw2, "password").build());
    assertEquals(Decision.REPLACE, r.decision());
    assertTrue(r.candidates().get(0).substitutions().isEmpty());
    assertEquals(pair(h, pw2), r.replacements().get(0).target());
  }

  @Test
  @DisplayName("several lapses in one transition compose; Replace covers every blocked hypothesis")
  void multipleLapses() {
    Message u1 = a("u1"), u2 = a("u2");
    ForgetDlResult r = engine.run(in(Set.of(u1, u2, pw1, pw2), Set.of()).forget(u1).forget(pw1)
        .send(pair(u1, pw1)).label(u1, "user").label(u2, "user").label(pw1, "password")
        .label(pw2, "password").build());
    assertEquals(Decision.REPLACE, r.decision());
    assertEquals(List.of(pair(u2, pw2)),
        r.replacements().stream().map(ForgetDlResult.Candidate::target).toList());
  }

  @Test
  @DisplayName("determinism: insertion order does not change theory, trace or decision")
  void determinism() {
    Set<Message> k1 = new LinkedHashSet<>(List.of(h, pw1, pw2, senc(pw1, h)));
    Set<Message> k2 = new LinkedHashSet<>(List.of(senc(pw1, h), pw2, pw1, h));
    ForgetDlResult r1 = engine.run(in(k1, Set.of()).forget(pw1).send(pair(h, pw1))
        .label(pw1, "password").label(pw2, "password").build());
    ForgetDlResult r2 = engine.run(in(k2, Set.of()).forget(pw1).send(pair(h, pw1))
        .label(pw2, "password").label(pw1, "password").build());
    assertEquals(r1.compiled().theory().toText(), r2.compiled().theory().toText());
    assertEquals(r1.explain(), r2.explain());
  }
  @Test
  void cyclicProofAfterProcedureLapseIsNotASkip() {
    Message x = a("x"), y = a("y");
    Set<Message> k = Set.of(x, senc(x, y), senc(y, x));
    String direct = NormalDerivations.procedureId(
        new com.xmen.model.Derivation(x, NormalDerivations.INITIAL, List.of()));
    HumanState state = new HumanState(k, Set.of(), new ProceduralState(Set.of(), Set.of(direct)));
    ForgetDlResult r = engine.run(TransitionInput.builder(state).send(x).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertTrue(r.notes().stream().anyMatch(n -> n.contains("cyclic proofs")));
    assertEquals(Decision.PRESERVE, engine.run(in(k, Set.of()).send(x).build()).decision());
  }

  @Test
  void incompleteReacquisitionCannotJustifyNegativeDecision() {
    ForgetDlEngine tiny = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(512, 1), 256));
    ForgetDlResult r = tiny.run(in(Set.of(pw1, senc(pw1, h)), Set.of(pw1))
        .received(h).send(a("unavailable")).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
  }

  @Test
  void manuscriptUnpairAliasAndRelearningUseTheSameCompetence() {
    ForgetDlResult r = engine.run(in(Set.of(pair(h, pw1)), Set.of())
        .lapse(new ForgetDlModel.RuleLapse("unpair")).send(h).build());
    assertEquals(Decision.SKIP, r.decision());
    assertEquals(Set.of("proj"), r.after().procedural().forgottenRules());
    assertEquals(Decision.PRESERVE, engine.run(TransitionInput.builder(r.after())
        .relearnRule("unpair").send(h).build()).decision());
  }

  @Test
  void cueAssociationIsValidatedEvenWithoutASend() {
    assertEquals(Status.INVALID_INPUT, engine.run(in(Set.of(h), Set.of())
        .cues(new CueModel(Set.of("cue"), Set.of(new Association("cue", pw1)), Set.of()))
        .build()).status());
  }

  @Test
  void candidateCapBoundsCartesianExpansion() {
    Set<Message> k = new LinkedHashSet<>();
    TransitionInput.Builder builder;
    Message send = h;
    k.add(h);
    for (int i = 0; i < 12; i++) {
      Message forgotten = a("old" + i);
      k.add(forgotten);
      send = pair(forgotten, send);
      for (int j = 0; j < 4; j++) {
        k.add(a("new" + i + "_" + j));
      }
    }
    builder = in(k, Set.of()).send(send);
    for (int i = 0; i < 12; i++) {
      builder.forget(a("old" + i)).label(a("old" + i), "label" + i);
      for (int j = 0; j < 4; j++) {
        builder.label(a("new" + i + "_" + j), "label" + i);
      }
    }
    ForgetDlEngine capped = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(NormalDerivations.Limits.DEFAULT, 2));
    ForgetDlResult r = capped.run(builder.build());
    assertEquals(Decision.REPLACE, r.decision());
    assertTrue(r.partial());
    assertEquals(2, r.candidates().size());
  }

  @Test
  void cueInsertionOrderDoesNotChangeTheoryOrTrace() {
    Set<Association> first = new LinkedHashSet<>(List.of(new Association("c1", pw1),
        new Association("c2", h)));
    Set<Association> second = new LinkedHashSet<>(List.of(new Association("c2", h),
        new Association("c1", pw1)));
    var b = in(Set.of(h, pw1), Set.of(h, pw1)).send(pair(h, pw1));
    var a = engine.run(b.cues(new CueModel(Set.of("c1", "c2"), first, Set.of("c1", "c2"))).build());
    var c = engine.run(b.cues(new CueModel(Set.of("c1", "c2"), second, Set.of("c1", "c2"))).build());
    assertEquals(a.compiled().theory().toText(), c.compiled().theory().toText());
    assertEquals(a.explain(), c.explain());
  }

  @Test
  void cyclicReceiptProofIsNoReacquisitionWitnessWithoutProcedureLapses() {
    Message x = a("x"), hashed = fn("h", x), receipt = senc(x, x);
    HumanState state = new HumanState(Set.of(x, hashed), Set.of(hashed),
        new ProceduralState(Set.of("h"), Set.of()));
    // A6 is defined on the finite basis: the cyclic proof h(sdec(senc(x,x),x)) is no witness,
    // and with no forgotten procedure the search is complete, so the update is certain.
    ForgetDlResult hashedSend = engine.run(TransitionInput.builder(state)
        .received(receipt).send(hashed).build());
    assertEquals(Decision.SKIP, hashedSend.decision(), "h(x) needs the lost rule h");
    assertEquals(Set.of(hashed), hashedSend.after().forget());
    assertEquals(Decision.PRESERVE, engine.run(TransitionInput.builder(state)
        .received(receipt).send(x).build()).decision());
    assertEquals(Decision.NO_SEND, engine.run(TransitionInput.builder(state)
        .received(receipt).build()).decision());
  }

  @Test
  void reacquisitionCannotUseAForgottenCompetence() {
    Message x = a("x"), hashed = fn("h", x);
    HumanState state = new HumanState(Set.of(x, hashed), Set.of(hashed),
        new ProceduralState(Set.of("h"), Set.of()));
    ForgetDlResult blocked = engine.run(TransitionInput.builder(state)
        .received(x).send(hashed).build());
    assertEquals(Decision.SKIP, blocked.decision());
    assertEquals(Set.of(hashed), blocked.after().forget());
    assertTrue(blocked.reacquired().isEmpty());
    assertEquals(Decision.PRESERVE, engine.run(TransitionInput.builder(state)
        .received(x).relearnRule("h").send(hashed).build()).decision());
    assertEquals(Decision.PRESERVE, engine.run(TransitionInput.builder(state)
        .received(hashed).send(hashed).build()).decision());
  }

}
