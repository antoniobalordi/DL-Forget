package com.xmen.service.forget;

import com.xmen.model.*;
import com.xmen.service.impl.ForgetMutationStrategy;
import com.xmen.utilities.ForgetMutationParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForgetDlDirectCompatibilityTest {
  @Test void alreadyExtractedForgetActionsStillLapseForLegacyDirectCaller() {
    Rule rule = new Rule("H1");
    rule.addPrecondition(fact("State", "'H'", "'1'", "<pw1,pw2>"));
    rule.addAction(new Fact("H"));
    rule.addAction(fact("Forget", "pw1"));
    rule.addPostcondition(fact("Out", "pw1"));
    ArrayList<Rule> rules = new ArrayList<>(List.of(rule));
    ParametersBundle bundle = new ParametersBundle();
    bundle.setExistingSetupKnowledge(Map.of("pw1", "password", "pw2", "password"));
    ForgetMutationParser.parseForgetMutations(rules, bundle);
    ForgetMutationStrategy strategy = new ForgetMutationStrategy();
    strategy.setDefeasibleLogicMode(true);
    strategy.applyMutation(rule, rules, bundle);
    assertThat(bundle.getCollections()).hasSize(1);
    assertThat(bundle.getCollections().get(0).get(0).toString()).contains("Out(pw2)");
    assertThat(bundle.getForgetContext().getForgetSet()).contains(new Atom("pw1"));
  }

  private Fact fact(String name, String... values) {
    Fact fact = new Fact(name);
    for (String value : values) fact.getParameters().add(new Value(value));
    return fact;
  }
}
