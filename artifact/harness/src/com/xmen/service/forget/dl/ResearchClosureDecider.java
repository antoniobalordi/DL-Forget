package com.xmen.service.forget.dl;

import com.xmen.model.Message;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import java.util.Set;

/** Research harness adapter for the production Theorem 16 usability decider. */
public final class ResearchClosureDecider {

  private final UsabilityDecider decider;

  public ResearchClosureDecider(Set<String> privateFunctions) {
    decider = new UsabilityDecider(privateFunctions);
  }

  public boolean usable(Message t, Set<Message> k, ForgetBlocking blocking,
      Set<String> forgottenRules, CueModel cues) {
    return decider.usable(t, k, blocking, forgottenRules, cues);
  }
}
