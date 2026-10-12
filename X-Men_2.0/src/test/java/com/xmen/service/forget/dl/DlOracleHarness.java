package com.xmen.service.forget.dl;

import com.xmen.model.Message;
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
import java.util.Random;

/**
 * Test-side harness for the differential check against an external DL encoding
 * (scripts/dl-oracle/asp_crosscheck.py). Line format, one theory per block:
 * {@code theory ID / declare A / fact [-]A / rule LABEL S|D|F [-]HEAD [-]BODY... / sup T S / end}.
 *
 * <pre>
 * export N SEED   print N theories compiled from random Forget scenarios
 * tags FILE       print "ID LITERAL TAGS" for every literal of every theory in FILE
 * </pre>
 */
public final class DlOracleHarness {

  private DlOracleHarness() {}

  public static void main(String[] args) throws IOException {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    if (args[0].equals("export")) {
      Random r = new Random(Long.parseLong(args[2]));
      for (int i = 0; i < Integer.parseInt(args[1]); i++) {
        ForgetDlPropertiesTest.Scenario s = ForgetDlPropertiesTest.random(r);
        DlFixtures.Compiled c = DlFixtures.compile(s.k(), s.forget(), s.mode(), s.proc(),
            s.cues(), s.targets().toArray(Message[]::new));
        out.print(write("compiled" + i, c.compiled().theory()));
      }
      return;
    }
    List<String> lines = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
    DefeasibleTheory.Builder b = null;
    String id = null;
    AmbiguityBlockingReasoner reasoner = new AmbiguityBlockingReasoner();
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
          Conclusions c = reasoner.reason(th);
          for (String a : th.atoms()) {
            for (Literal l : List.of(Literal.pos(a), Literal.neg(a))) {
              out.println(id + " " + (l.negated() ? "-" : "") + a + " "
                  + (c.plusDelta().contains(l) ? "+D" : "") + (c.minusDelta().contains(l) ? "-D" : "")
                  + (c.plusPartial().contains(l) ? "+d" : "") + (c.minusPartial().contains(l) ? "-d" : ""));
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

  static String write(String id, DefeasibleTheory th) {
    StringBuilder sb = new StringBuilder("theory ").append(id).append('\n');
    th.atoms().forEach(a -> sb.append("declare ").append(a).append('\n'));
    th.facts().forEach(f -> sb.append("fact ").append(f.negated() ? "-" : "").append(f.atom()).append('\n'));
    for (Rule r : th.rules()) {
      sb.append("rule ").append(r.label()).append(' ')
          .append(r.kind() == RuleKind.STRICT ? "S" : r.kind() == RuleKind.DEFEASIBLE ? "D" : "F")
          .append(' ').append(r.head().negated() ? "-" : "").append(r.head().atom());
      r.body().forEach(l -> sb.append(' ').append(l.negated() ? "-" : "").append(l.atom()));
      sb.append('\n');
    }
    th.superiorityPairs().forEach(p -> sb.append("sup ").append(p[0]).append(' ').append(p[1]).append('\n'));
    return sb.append("end\n").toString();
  }
}
