package com.xmen.service.forget.dl;

import static com.xmen.service.forget.dl.DlFixtures.a;
import static com.xmen.service.forget.dl.DlFixtures.fn;
import static com.xmen.service.forget.dl.DlFixtures.pair;
import static com.xmen.service.forget.dl.DlFixtures.senc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xmen.model.Message;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import com.xmen.service.forget.dl.ForgetDlResult.Decision;
import com.xmen.service.forget.dl.NormalDerivations.Coverage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exact usability must not erase independent candidate or successor-state uncertainty. */
class ForgetDlUsabilityFallbackTest {

  private static ForgetDlEngine tiny(Set<String> privateFunctions) {
    return new ForgetDlEngine(new AmbiguityBlockingReasoner(), privateFunctions,
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(512, 1), 256));
  }

  private static TransitionInput.Builder in(Set<Message> k, Set<Message> forgotten) {
    return TransitionInput.builder(new HumanState(k, forgotten, ProceduralState.EMPTY));
  }

  private static TransitionInput exponential(boolean surviving) {
    Set<Message> k = new LinkedHashSet<>();
    List<Message> components = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Message x = a("x" + i), ka = a("ka" + i), kb = a("kb" + i);
      k.addAll(List.of(ka, kb, senc(x, ka), senc(x, kb)));
      components.add(x);
    }
    Message target = components.get(9);
    for (int i = 8; i >= 0; i--) {
      target = pair(components.get(i), target);
    }
    return in(k, surviving ? Set.of(a("ka0")) : Set.of(a("ka0"), a("kb0")))
        .send(target).build();
  }

  @Test
  void exponentialSurvivorPreservesWithoutInventingADlProof() {
    TransitionInput input = exponential(true);
    ForgetDlResult r = new ForgetDlEngine().run(input);
    assertEquals(Decision.PRESERVE, r.decision());
    assertEquals(Boolean.TRUE, r.usabilityFallback().get(input.intendedSend()));
    assertEquals(Coverage.TRUNCATED, r.candidates().get(0).derivationCoverage());
    assertEquals("-∂", r.trace().get(0).usableTag());
    assertTrue(r.conclusions().refuted(r.compiled().usable().get(input.intendedSend())));
    assertTrue(r.explain().toLowerCase().contains("saturation"));
  }

  @Test
  void exponentialNegativeSkipsWhenNoSubstitutionCanChangeTheTarget() {
    TransitionInput input = exponential(false);
    ForgetDlResult r = new ForgetDlEngine().run(input);
    assertEquals(Decision.SKIP, r.decision());
    assertEquals(Boolean.FALSE, r.usabilityFallback().get(input.intendedSend()));
    assertEquals(Coverage.COMPLETE, r.candidateCoverage());
    assertEquals(Coverage.TRUNCATED, r.candidates().get(0).derivationCoverage());
    assertEquals(1, r.candidates().size());
  }

  @Test
  void negativeFallbackDoesNotDiscardHiddenReplacementCandidates() {
    Message old = a("old"), fresh = a("fresh"), target = fn("h", old);
    ForgetDlResult r = tiny(Set.of()).run(in(Set.of(old, fresh), Set.of(old)).send(target)
        .label(old, "value").label(fresh, "value").build());
    assertEquals(Boolean.FALSE, r.usabilityFallback().get(target));
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertEquals(Coverage.TRUNCATED, r.candidateCoverage());
  }

  @Test
  void exactProcedureLapsesDisableTheFallback() {
    Message x = a("x"), target = fn("h", x);
    HumanState state = new HumanState(Set.of(x), Set.of(),
        new ProceduralState(Set.of(), Set.of("pi_unrelated")));
    ForgetDlResult r = tiny(Set.of()).run(TransitionInput.builder(state).send(target).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertTrue(r.usabilityFallback().isEmpty());
  }

  @Test
  void uncertainReacquisitionStillOverridesAnOtherwiseUsableSend() {
    Message old = a("old"), key = a("key"), known = a("known");
    ForgetDlResult r = tiny(Set.of()).run(in(Set.of(old, senc(old, key), known), Set.of(old))
        .received(key).send(fn("h", known)).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertTrue(r.usabilityFallback().isEmpty());
  }

  @Test
  void fallbackUsesCurrentRulesAndCues() {
    Message x = a("x"), target = fn("h", x);
    CueModel cue = new CueModel(Set.of("c"), Set.of(new Association("c", x)), Set.of("c"));
    ForgetDlResult restored = tiny(Set.of()).run(in(Set.of(x), Set.of(x)).send(target)
        .cues(cue).build());
    assertEquals(Decision.PRESERVE, restored.decision());
    assertEquals(Boolean.TRUE, restored.usabilityFallback().get(target));
    ForgetDlResult lostRule = tiny(Set.of()).run(in(Set.of(x), Set.of(x)).send(target)
        .cues(cue).lapse(new ForgetDlModel.RuleLapse("h")).build());
    assertEquals(Decision.SKIP, lostRule.decision());
    assertEquals(Boolean.FALSE, lostRule.usabilityFallback().get(target));
  }

  @Test
  void fallbackDoesNotComposePrivateFunctions() {
    Message x = a("x"), y = a("y"), target = pair(fn("secret", x), y);
    TransitionInput input = in(Set.of(x, y), Set.of()).send(target).build();
    ForgetDlResult privateResult = tiny(Set.of("secret")).run(input);
    assertEquals(Decision.SKIP, privateResult.decision());
    assertEquals(Boolean.FALSE, privateResult.usabilityFallback().get(target));
    assertEquals(Decision.PRESERVE, tiny(Set.of()).run(input).decision());
  }

  @Test
  void injectedReasonersAreNotBypassed() {
    Message x = a("x");
    DefeasibleReasoner delegated = theory -> new AmbiguityBlockingReasoner().reason(theory);
    ForgetDlEngine custom = new ForgetDlEngine(delegated, Set.of(),
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(512, 1), 256));
    ForgetDlResult r = custom.run(in(Set.of(x), Set.of()).send(fn("h", x)).build());
    assertEquals(Decision.UNKNOWN_COVERAGE, r.decision());
    assertTrue(r.usabilityFallback().isEmpty());
  }

  @Test
  void fallbackCanRecoverAReplacementWithoutClaimingExhaustiveCandidates() {
    Message old = a("old"), fresh = a("fresh"), x = a("x");
    Message blockedPair = pair(x, a("a")), accessiblePair = pair(x, a("b"));
    ForgetDlEngine capped = new ForgetDlEngine(new AmbiguityBlockingReasoner(), Set.of(),
        new ForgetDlEngine.Limits(new NormalDerivations.Limits(1, 250_000), 256));
    Message target = pair(old, x), replacement = pair(fresh, x);
    ForgetDlResult r = capped.run(in(Set.of(old, fresh, blockedPair, accessiblePair),
        Set.of(old, blockedPair)).send(target).label(old, "value").label(fresh, "value")
        .label(blockedPair, "container").label(accessiblePair, "container").build());
    assertEquals(Decision.REPLACE, r.decision());
    assertEquals(List.of(replacement), r.replacements().stream()
        .map(ForgetDlResult.Candidate::target).toList());
    assertEquals(Boolean.FALSE, r.usabilityFallback().get(target));
    assertEquals(Boolean.TRUE, r.usabilityFallback().get(replacement));
    assertEquals(Coverage.TRUNCATED, r.candidateCoverage());
    assertTrue(r.partial());
    assertFalse(r.replacements().isEmpty());
  }
}
