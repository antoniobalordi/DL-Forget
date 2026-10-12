package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import com.xmen.service.forget.dl.ForgetDlResult.Status;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Host adapter on the shapes produced by X-Men for src/test/resources/Forget_Bank_Input.spthy. */
@DisplayName("Host bridge")
class ForgetDlBridgeTest {

  private final ForgetDlBridge bridge = new ForgetDlBridge();
  private final Message pw1 = a("~pw1");
  private final Message pw2 = a("~pw2");
  private final Message nh = a("nH");
  private final Message nb = a("nB");
  private final Message ltkH = a("ltkH");
  private final Set<Message> stateK = Set.of(a("$B1"), a("$B2"), nh, pw1, pw2, ltkH);
  private final Message challenge = aenc(pair(nh, nb), ltkH);
  private final Message send = senc(pair(a("$Human"), a("pw1")), fn("k", nh, nb));
  private final Function<Message, String> typeOf = m -> switch (Terms.canonical(m)) {
    case "pw1", "~pw1", "pw2", "~pw2" -> "password";
    default -> null;
  };

  @Test
  @DisplayName("H_2 of Forget_Bank_Input: Forget(pw1) → Replace pw1 by pw2 (k/2 composable, $Human public)")
  void bankRuleH2() {
    ForgetDlBridge.Outcome o = bridge.decide(stateK, Set.of(), challenge, List.of(a("pw1")),
        Set.of(), send, typeOf, BlockingMode.CASE1_WEAK);
    assertEquals(Status.OK, o.result().status());
    assertEquals(Decision.REPLACE, o.result().decision());
    assertEquals(List.of(Map.of(a("pw1"), a("pw2"))), o.replacementSubstitutions());
    assertEquals(senc(pair(a("$Human"), a("pw2")), fn("k", nh, nb)), o.replacementTargets().get(0));
    assertEquals(Set.of(pw1), o.forgetAfter(), "host representation (tilde) preserved");
    assertTrue(o.knowledgeAfter().contains(challenge));
    assertFalse(o.knowledgeAfter().contains(a("$Human")), "A10: injected public constant not written back");
  }

  @Test
  @DisplayName("unknown forgotten message is an explicit INVALID_INPUT, not a fabricated atom")
  void fabricatedForgetIsRejected() {
    ForgetDlBridge.Outcome o = bridge.decide(stateK, Set.of(), challenge, List.of(a("pw9")),
        Set.of(), send, typeOf, BlockingMode.CASE1_WEAK);
    assertEquals(Status.INVALID_INPUT, o.result().status());
    assertTrue(o.replacementSubstitutions().isEmpty());
  }

  @Test
  @DisplayName("forgotten value used by an internal check → Neglect and return")
  void neglect() {
    ForgetDlBridge.Outcome o = bridge.decide(stateK, Set.of(), challenge, List.of(nh), Set.of(nh),
        send, typeOf, BlockingMode.CASE1_WEAK);
    assertEquals(Decision.NEGLECT, o.result().decision());
  }

  @Test
  @DisplayName("only password left is forgotten → Skip")
  void skip() {
    ForgetDlBridge.Outcome o = bridge.decide(Set.of(a("$B1"), nh, pw1, ltkH), Set.of(), challenge,
        List.of(pw1), Set.of(), send, typeOf, BlockingMode.CASE1_WEAK);
    assertEquals(Decision.SKIP, o.result().decision());
  }
}
