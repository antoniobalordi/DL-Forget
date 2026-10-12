package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.aenc;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Derivation;
import com.xmen.model.Encrypt;
import com.xmen.model.Message;
import com.xmen.service.forget.dl.NormalDerivations.Coverage;
import com.xmen.service.forget.dl.NormalDerivations.Result;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Normal-derivation enumeration (paper §3.2, Definition 7). */
@DisplayName("Normal derivations")
class NormalDerivationsTest {

  private final NormalDerivations nd = new NormalDerivations();
  private final Message m1 = a("m1");
  private final Message m2 = a("m2");

  private static Set<Message> k(Message... ms) {
    return new LinkedHashSet<>(List.of(ms));
  }

  private static List<Set<Message>> hyps(Result r) {
    List<Set<Message>> out = new ArrayList<>();
    r.derivations().forEach(d -> out.add(NormalDerivations.hypotheses(d)));
    return out;
  }

  @Test
  @DisplayName("Example 4: direct use and decryption; decryption carries the ciphertext premise")
  void example4() {
    Message c = senc(m1, m2);
    Result r = nd.enumerate(m1, k(m1, c, m2));
    assertEquals(Coverage.COMPLETE, r.coverage());
    assertEquals(2, r.derivations().size());
    assertTrue(hyps(r).contains(Set.of(m1)));
    assertTrue(hyps(r).contains(Set.of(c, m2)), "fixes host defect D1");
  }

  @Test
  @DisplayName("legacy Encrypt terms are symmetric ciphertexts")
  void legacyEncrypt() {
    Message c = new Encrypt(m1, m2);
    Result r = nd.enumerate(m1, k(c, m2));
    assertEquals(List.of(Set.of(c, m2)), hyps(r));
    assertEquals(Set.of("sdec"), NormalDerivations.rules(r.derivations().get(0)));
  }

  @Test
  @DisplayName("Example 6/9: direct use and projection of the pair")
  void example6() {
    Message p = pair(m1, m2);
    Result r = nd.enumerate(m1, k(p, m1, m2));
    assertEquals(2, r.derivations().size());
    assertTrue(hyps(r).contains(Set.of(p)));
    assertTrue(hyps(r).contains(Set.of(m1)));
  }

  @Test
  @DisplayName("Example 1: a forgotten message may occur in a derivation without being a hypothesis")
  void example1() {
    Message m = a("m");
    Message mb = a("mbar");
    assertEquals(List.of(Set.of(senc(m, mb), mb)), hyps(nd.enumerate(m, k(senc(m, mb), mb))));
    Result r = nd.enumerate(m, k(senc(m, mb), senc(mb, m1), m1));
    assertEquals(List.of(Set.of(senc(m, mb), senc(mb, m1), m1)), hyps(r));
    assertFalse(hyps(r).get(0).contains(mb));
  }

  @Test
  @DisplayName("Example 10: unique derivation adec;pair;senc although N_H is known")
  void example10() {
    Message h = a("H"), pw = a("PW"), sk = a("skH"), nh = a("NH"), nb = a("NB");
    Message challenge = aenc(pair(nh, nb), sk);
    Message m2 = senc(pair(h, pw), pair(nh, nb));
    Result r = nd.enumerate(m2, k(h, pw, sk, nh, challenge));
    assertEquals(Coverage.COMPLETE, r.coverage());
    assertEquals(1, r.derivations().size());
    Derivation pi = r.derivations().get(0);
    assertEquals(Set.of("adec", "pair", "senc"), NormalDerivations.rules(pi));
    assertEquals(Set.of(h, pw, sk, challenge), NormalDerivations.hypotheses(pi));
  }

  @Test
  @DisplayName("§7.2: decrypt-and-project, plus the direct derivation when n1 is known")
  void decryptAndProject() {
    Message n1 = a("n1"), n2 = a("n2"), key = a("k");
    Message c = senc(pair(n1, n2), key);
    Result only = nd.enumerate(n1, k(c, key));
    assertEquals(1, only.derivations().size());
    assertEquals(Set.of("proj", "sdec"), NormalDerivations.rules(only.derivations().get(0)));
    assertEquals(2, nd.enumerate(n1, k(c, key, n1)).derivations().size());
  }

  @Test
  @DisplayName("asymmetric decryption needs the private key of pk(sk); other keys are opaque")
  void asymmetric() {
    Message sk = a("sk"), x = a("x");
    assertEquals(1, nd.enumerate(x, k(aenc(x, sk), sk)).derivations().size());
    assertEquals(0, nd.enumerate(x, k(aenc(x, sk), fn("pk", sk))).derivations().size());
    assertEquals(0, nd.enumerate(x, k(fn("aenc", x, a("KB")), a("KB"))).derivations().size());
  }

  @Test
  @DisplayName("public functions (incl. user-defined k/2) are composable; private ones are not")
  void userDefinedAndPrivateFunctions() {
    Message nh = a("nH"), nb = a("nB");
    Message key = fn("k", nh, nb);
    assertEquals(1, nd.enumerate(key, k(nh, nb)).derivations().size(), "contrast host defect D3");
    NormalDerivations priv = new NormalDerivations(Set.of("k"), NormalDerivations.Limits.DEFAULT);
    assertEquals(0, priv.enumerate(key, k(nh, nb)).derivations().size());
  }

  @Test
  @DisplayName("finiteness: mutually encrypted keys terminate (the immediate-inverse rule alone does not)")
  void finitenessCounterexample() {
    Message x = a("a"), y = a("b");
    Set<Message> kn = k(senc(x, y), senc(y, x), x);
    Result rx = nd.enumerate(x, kn);
    assertEquals(Coverage.COMPLETE, rx.coverage());
    assertEquals(1, rx.derivations().size());
    assertTrue(rx.cyclicDerivations(), "whole-procedure lapses need coverage beyond this basis");
    Result ry = nd.enumerate(y, kn);
    assertEquals(1, ry.derivations().size());
  }

  @Test
  @DisplayName("cyclic key dependencies are reported; ordinary inverse pruning is not cyclic")
  void cyclicCoverage() {
    Message x = a("x"), key = fn("f", x);
    assertTrue(nd.enumerate(x, k(x, senc(x, key))).cyclicDerivations());
    assertFalse(nd.enumerate(m1, k(pair(m1, m2), senc(m1, m2), m2)).cyclicDerivations());
  }

  @Test
  @DisplayName("normality: no decomposition of a just-composed term")
  void normality() {
    Message p = pair(m1, m2);
    Result r = nd.enumerate(m1, k(m2, senc(m1, m2)));
    for (Derivation d : r.derivations()) {
      assertFalse(NormalDerivations.canonicalTree(d).startsWith("sdec[m1](senc"), "senc then sdec");
    }
    assertEquals(0, nd.enumerate(m1, k(m2)).derivations().size());
    assertEquals(1, nd.enumerate(p, k(m1, m2)).derivations().size());
  }

  @Test
  @DisplayName("unsupported operators are reported, not silently ignored")
  void unsupported() {
    Result r = nd.enumerate(fn("xor", m1, m2), k(m1, m2));
    assertEquals(Coverage.UNSUPPORTED, r.coverage());
    assertEquals(Coverage.UNSUPPORTED, nd.enumerate(m1, k(fn("fst", pair(m1, m2)))).coverage());
    assertEquals(Coverage.UNSUPPORTED, nd.enumerate(fn("senc", m1), k(m1)).coverage());
    assertEquals(Coverage.UNSUPPORTED, nd.enumerate(fn("aenc", m1), k(m1)).coverage());
    assertEquals(Coverage.UNSUPPORTED, nd.enumerate(fn("pk", m1, m2), k(m1, m2)).coverage());
    Message opaque = () -> "opaque";
    assertEquals(Coverage.UNSUPPORTED, nd.enumerate(m1, k(m1, opaque)).coverage());
  }

  @Test
  @DisplayName("limits produce TRUNCATED with only valid derivations")
  void truncation() {
    Message t = pair(pair(m1, m2), pair(m1, m2));
    Set<Message> kn = k(m1, m2, pair(m1, m2), pair(m2, m1));
    NormalDerivations tiny = new NormalDerivations(Set.of(), new NormalDerivations.Limits(1, 1000));
    Result r = tiny.enumerate(t, kn);
    assertEquals(Coverage.TRUNCATED, r.coverage());
    for (Derivation d : r.derivations()) {
      assertTrue(kn.containsAll(NormalDerivations.hypotheses(d)));
      assertEquals(t, d.getGoal());
    }
    assertEquals(Coverage.COMPLETE, nd.enumerate(t, kn).coverage());
  }

  @Test
  @DisplayName("deterministic order and stable procedure ids across states")
  void determinismAndStableIds() {
    Message c = senc(m1, m2);
    Result r1 = nd.enumerate(m1, k(m1, c, m2));
    Result r2 = nd.enumerate(m1, k(m2, c, m1, a("unrelated")));
    assertEquals(
        r1.derivations().stream().map(NormalDerivations::procedureId).toList(),
        r2.derivations().stream().map(NormalDerivations::procedureId).toList());
  }

  @Test
  @DisplayName("display collisions keep distinct terms, procedures and DL conclusions")
  void structuralIdentity() {
    Message atom = a("f(x)"), compound = fn("f", a("x"));
    assertEquals(Terms.canonical(atom), Terms.canonical(compound));
    assertNotEquals(Terms.identity(atom), Terms.identity(compound));
    assertNotEquals(0, Terms.ORDER.compare(atom, compound));
    assertNotEquals(NormalDerivations.procedureId(nd.enumerate(atom, k(atom)).derivations().get(0)),
        NormalDerivations.procedureId(nd.enumerate(compound, k(compound)).derivations().get(0)));
    DlFixtures.Compiled c = DlFixtures.compile(k(atom, compound), k(atom), BlockingMode.CASE1_WEAK,
        ProceduralState.EMPTY, CueModel.NONE, atom, compound);
    assertEquals(2, c.compiled().usable().size());
    assertEquals("-∂", c.usable(atom));
    assertEquals("+∂", c.usable(compound));
  }

  @Test
  @DisplayName("enumeration limits reject nonpositive values")
  void positiveLimits() {
    assertThrows(IllegalArgumentException.class, () -> new NormalDerivations.Limits(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new NormalDerivations.Limits(1, 0));
  }
}
