package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Encrypt;
import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsabilityDeciderTest {

  private final UsabilityDecider decider = new UsabilityDecider(Set.of());
  private final ForgetBlocking noBlocking =
      new ForgetBlocking(Set.of(), BlockingMode.CASE1_WEAK, Set.of());

  @Test
  void decryptionCanUseAConstructedKeyButNotAForgottenRule() {
    Message x = a("x"), k = a("k"), key = fn("h", k);
    for (Message ciphertext : Set.of(senc(x, key), new Encrypt(x, key))) {
      Set<Message> knowledge = Set.of(ciphertext, k);
      assertTrue(decider.usable(x, knowledge, noBlocking, Set.of(), CueModel.NONE));
      assertFalse(decider.usable(x, knowledge, noBlocking, Set.of("h"), CueModel.NONE));
      assertFalse(decider.usable(x, knowledge, noBlocking, Set.of("sdec"), CueModel.NONE));
    }
    assertTrue(decider.usable(x, Set.of(aenc(x, key), k), noBlocking, Set.of(), CueModel.NONE));
    assertFalse(decider.usable(x, Set.of(aenc(x, key), k), noBlocking,
        Set.of("adec"), CueModel.NONE));
    assertFalse(decider.usable(x, Set.of(aenc(x, key), fn("pk", key)), noBlocking,
        Set.of(), CueModel.NONE));
  }

  @Test
  void cuesRestoreKnowledgeButDoNotRestoreRules() {
    Message x = a("x"), k = a("k"), ciphertext = senc(x, k);
    Set<Message> knowledge = Set.of(ciphertext, k);
    ForgetBlocking blocking = new ForgetBlocking(Set.of(k), BlockingMode.CASE1_WEAK, Set.of());
    CueModel cues = new CueModel(Set.of("c"), Set.of(new Association("c", k)), Set.of("c"));
    assertFalse(decider.usable(x, knowledge, blocking, Set.of(), CueModel.NONE));
    assertTrue(decider.usable(x, knowledge, blocking, Set.of(), cues));
    assertFalse(decider.usable(x, knowledge, blocking, Set.of("sdec"), cues));
  }

  @Test
  void projectionAndPrivateConstructorsRespectTheirRestrictions() {
    Message x = a("x"), y = a("y"), secret = fn("secret", x);
    assertTrue(decider.usable(x, Set.of(pair(x, y)), noBlocking, Set.of(), CueModel.NONE));
    assertFalse(decider.usable(x, Set.of(pair(x, y)), noBlocking, Set.of("proj"), CueModel.NONE));
    assertFalse(decider.usable(pair(x, y), Set.of(x, y), noBlocking,
        Set.of("pair"), CueModel.NONE));
    UsabilityDecider privateDecider = new UsabilityDecider(Set.of("secret"));
    assertFalse(privateDecider.usable(secret, Set.of(x), noBlocking, Set.of(), CueModel.NONE));
    assertTrue(privateDecider.usable(secret, Set.of(secret), noBlocking,
        Set.of("secret"), CueModel.NONE));
  }

  @Test
  void cyclicKnowledgeNeedsAnAccessibleStartingPoint() {
    Message x = a("x"), y = a("y");
    assertFalse(decider.usable(y, Set.of(senc(x, y), senc(y, x)), noBlocking,
        Set.of(), CueModel.NONE));
    assertTrue(decider.usable(y, Set.of(x, senc(x, y), senc(y, x)), noBlocking,
        Set.of(), CueModel.NONE));
  }

  @Test
  void agreesWithCompleteEnumerationAndCompiledReasoning() {
    Random random = new Random(160016);
    NormalDerivations enumerator = new NormalDerivations();
    int checked = 0;
    for (int i = 0; i < 400; i++) {
      ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(random);
      ProceduralState proc = new ProceduralState(s.proc().forgottenRules(), Set.of());
      DlFixtures.Compiled compiled = DlFixtures.compile(s.k(), s.forget(), s.mode(), proc,
          s.cues(), s.targets().toArray(Message[]::new));
      ForgetBlocking blocking = new ForgetBlocking(s.forget(), s.mode(), Set.of());
      for (Message target : s.targets()) {
        if (enumerator.enumerate(target, s.k()).coverage() == NormalDerivations.Coverage.COMPLETE) {
          assertEquals(compiled.usable(target).equals("+∂"),
              decider.usable(target, s.k(), blocking, proc.forgottenRules(), s.cues()),
              "scenario " + i + ", target " + Terms.canonical(target));
          checked++;
        }
      }
    }
    assertTrue(checked >= 1000, "enough complete targets must be checked");
  }
}
