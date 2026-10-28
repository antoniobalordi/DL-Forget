package com.xmen.service.forget.dl;

import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.DefeasibleTheory.Literal;
import com.xmen.service.forget.dl.DefeasibleTheory.Rule;
import com.xmen.service.forget.dl.DefeasibleTheory.RuleKind;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prints the tags of the ambiguity-propagating prototype for the differential check against the
 * DL(delta) ASP encoding (scripts/dl-oracle/ap_crosscheck.py). Input: theories in the
 * DlOracleHarness format. Output, one line per literal: "ID LITERAL TAGS", where TAGS is a
 * concatenation of +d / -d (delta) and +S / -S (support).
 *
 * <pre>java ... com.xmen.service.forget.dl.ResearchDeltaTags THEORIES.dl &gt; tags.txt</pre>
 */
public final class ResearchDeltaTags {

  private ResearchDeltaTags() {}

  public static void main(String[] args) throws IOException {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
    ResearchVariantReasoners.AmbiguityPropagating ap = new ResearchVariantReasoners.AmbiguityPropagating();
    DefeasibleTheory.Builder b = null;
    String id = null;
    for (String line : lines) {
      String[] t = line.trim().split("\\s+");
      switch (t[0]) {
        case "theory" -> {
          b = DefeasibleTheory.builder();
          id = t[1];
        }
        case "declare" -> b.declare(t[1]);
        case "fact" -> b.fact(lit(t[1]));
        case "rule" -> {
          List<Literal> body = new ArrayList<>();
          for (int i = 4; i < t.length; i++) {
            body.add(lit(t[i]));
          }
          RuleKind k = switch (t[2]) {
            case "S" -> RuleKind.STRICT;
            case "D" -> RuleKind.DEFEASIBLE;
            default -> RuleKind.DEFEATER;
          };
          b.rule(new Rule(t[1], k, body, lit(t[3])));
        }
        case "sup" -> b.superior(t[1], t[2]);
        case "end" -> {
          DefeasibleTheory th = b.build();
          Conclusions c = ap.reason(th);              // delta tags are in the partial slots
          Set<Literal>[] support = ap.support();      // {+support, -support} of this theory
          for (String a : th.atoms()) {
            for (Literal l : List.of(Literal.pos(a), Literal.neg(a))) {
              out.println(id + " " + (l.negated() ? "-" : "") + a + " "
                  + (c.plusPartial().contains(l) ? "+d" : "")
                  + (c.minusPartial().contains(l) ? "-d" : "")
                  + (support[0].contains(l) ? "+S" : "")
                  + (support[1].contains(l) ? "-S" : ""));
            }
          }
        }
        default -> { }
      }
    }
  }

  private static Literal lit(String s) {
    return s.startsWith("-") ? Literal.neg(s.substring(1)) : Literal.pos(s);
  }
}