package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.pk;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Message;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Definition 5: Blocked(Hyp, Forget) for Cases 1–3. */
@DisplayName("Blocked, Cases 1-3")
class ForgetBlockingTest {

  private final Message m1 = a("m1");
  private final Message m2 = a("m2");

  private static ForgetBlocking b(BlockingMode mode, Message... forget) {
    return new ForgetBlocking(Set.of(forget), mode, Set.of());
  }

  @Test
  @DisplayName("Example 4/7: no key in Forget, all cases agree")
  void example4() {
    for (BlockingMode mode : BlockingMode.values()) {
      ForgetBlocking f = b(mode, senc(m1, m2));
      assertTrue(f.isBlocked(senc(m1, m2)));
      assertFalse(f.isBlocked(m1), mode.name());
    }
  }

  @Test
  @DisplayName("Example 5/8: only Case 3 decrypts the forgotten ciphertext")
  void example5() {
    assertFalse(b(BlockingMode.CASE1_WEAK, senc(m1, m2), m2).isBlocked(m1));
    assertFalse(b(BlockingMode.CASE2_PAIRING, senc(m1, m2), m2).isBlocked(m1));
    assertTrue(b(BlockingMode.CASE3_FULL_DY, senc(m1, m2), m2).isBlocked(m1));
  }

  @Test
  @DisplayName("Example 6/9: pair decomposition from Case 2 on")
  void example6() {
    Message p = pair(m1, m2);
    assertFalse(b(BlockingMode.CASE1_WEAK, p).isBlocked(m1));
    assertTrue(b(BlockingMode.CASE2_PAIRING, p).isBlocked(m1));
    assertTrue(b(BlockingMode.CASE2_PAIRING, p).isBlocked(m2));
    assertTrue(b(BlockingMode.CASE3_FULL_DY, p).isBlocked(m2));
  }

  @Test
  @DisplayName("Case 2 composition: forgetting both components forgets the pair")
  void composition() {
    assertFalse(b(BlockingMode.CASE1_WEAK, m1, m2).isBlocked(pair(m1, m2)));
    assertTrue(b(BlockingMode.CASE2_PAIRING, m1, m2).isBlocked(pair(m1, m2)));
    assertFalse(b(BlockingMode.CASE2_PAIRING, m1).isBlocked(pair(m1, m2)));
    assertFalse(b(BlockingMode.CASE2_PAIRING, m1, m2).isBlocked(senc(m1, m2)));
    assertTrue(b(BlockingMode.CASE3_FULL_DY, m1, m2).isBlocked(senc(m1, m2)));
  }

  @Test
  @DisplayName("Case 3: asymmetric decryption with a forgotten private key, and nested analysis")
  void case3Nested() {
    Message sk = a("sk"), x = a("x"), k2 = a("k2");
    ForgetBlocking f = b(BlockingMode.CASE3_FULL_DY, aenc(senc(x, k2), sk), pair(sk, k2));
    assertTrue(f.isBlocked(x));
    assertTrue(f.isBlocked(pk(sk)));
    assertFalse(f.isBlocked(a("y")));
  }

  @Test
  @DisplayName("blocked() returns the blocked subset in canonical order")
  void blockedSubset() {
    Set<Message> out = b(BlockingMode.CASE1_WEAK, m2, m1).blocked(Set.of(m2, a("z"), m1));
    assertEquals("[m1, m2]", out.stream().map(Terms::canonical).toList().toString());
  }
}
