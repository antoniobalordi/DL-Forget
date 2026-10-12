package com.xmen.utilities;

import static org.junit.jupiter.api.Assertions.*;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileHandlerSingleRuleTest {
  @Test
  void aSingleRuleIsRetainedAndHasNoNeighbors() {
    Rule rule = new Rule("Only");
    var bundle = new ParametersBundle();
    bundle.setTheory(new ArrayList<>(List.of(rule)));
    new FileHandler().arrangeTheory(bundle);
    assertEquals(List.of(rule), bundle.getTheory());
    assertNull(rule.getPrevious());
    assertNull(rule.getNext());
  }
}
