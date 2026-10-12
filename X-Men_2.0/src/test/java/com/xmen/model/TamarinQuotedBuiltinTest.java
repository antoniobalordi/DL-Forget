package com.xmen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

class TamarinQuotedBuiltinTest {
  @Test
  void proceduralAnnotationsAcceptQuotedBuiltinRuleNames() {
    for (String name : new String[] {"senc", "aenc", "sign", "pk"}) {
      String source = "rule H: [] --[ForgetRule('" + name + "'),RelearnRule('" + name
          + "')]-> []\nend";
      var tokens = new CommonTokenStream(new TamarinLexer(CharStreams.fromString(source)));
      var parser = new TamarinParser(tokens);
      parser.removeErrorListeners();
      parser.theory();
      assertEquals(0, parser.getNumberOfSyntaxErrors(), name);
      assertEquals(Token.EOF, tokens.LA(1));
    }
  }
}
