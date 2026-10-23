package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.ForgetDlModel.Association;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.HumanState;
import com.xmen.service.forget.dl.ForgetDlModel.LapseEvent;
import com.xmen.service.forget.dl.ForgetDlModel.MessageLapse;
import com.xmen.service.forget.dl.ForgetDlModel.ProcedureLapse;
import com.xmen.service.forget.dl.ForgetDlModel.RuleLapse;
import com.xmen.service.forget.dl.ForgetDlModel.TransitionInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Running example of the CAV manuscript: the Bank Login challenge-response transition of Sandu
 * et al. H has sent {H,B,nH}_pk(B), receives c = aenc(<nH,nB>, pk(ltkH)) and intends to send
 * m3 = senc(<H,pw1>, k(nH,nB)). Every scenario is decided by the unmodified X-Men 2.0 engine
 * (ForgetDlEngine with the production reasoner); the published Algorithm 1 (same derivation
 * basis) and the two research variants of the proof theory are run on the same input.
 */
public final class ResearchRunningExample {

  static Message a(String v) {
    return new Atom(v);
  }

  static Message fn(String f, Message... args) {
    return new PredictiveFunction(f, List.of(args));
  }

  static final Message H = a("H"), B1 = a("B1"), B2 = a("B2"), NH = a("nH"), NB = a("nB");
  static final Message PW1 = a("pw1"), PW2 = a("pw2"), LTKH = a("ltkH"), PKB1 = a("pkB1");
  static final Message C = fn("aenc", new Pair(NH, NB), fn("pk", LTKH));
  static final Message M3 = fn("senc", new Pair(H, PW1), fn("k", NH, NB));

  static Set<Message> knowledge() {
    return new LinkedHashSet<>(List.of(H, B1, B2, NH, PW1, PW2, LTKH, PKB1));
  }

  static Map<Message, String> labels() {
    Map<Message, String> l = new LinkedHashMap<>();
    l.put(PW1, "password");
    l.put(PW2, "password");
    l.put(H, "username");
    l.put(NH, "nonce");
    return l;
  }

  record Scenario(String id, String description, List<LapseEvent> lapses, CueModel cues) {}

  public static void main(String[] args) {
    Set<Message> k1 = knowledge();
    k1.add(C);
    NormalDerivations nd = new NormalDerivations();
    List<Derivation> pis = nd.enumerate(M3, k1).derivations();
    String piA = null, piB = null;
    System.out.println("# Running example: Bank Login, transition receiving c and sending m3");
    System.out.println("m3 = " + Terms.canonical(M3) + "\nc  = " + Terms.canonical(C));
    System.out.println("\n## Normal derivations of m3 from K^{i+1} (" + pis.size() + ")");
    for (Derivation d : pis) {
      String id = NormalDerivations.procedureId(d);
      Set<Message> hyp = NormalDerivations.hypotheses(d);
      if (hyp.contains(NH)) {
        piA = id;
      } else {
        piB = id;
      }
      System.out.println("- " + id + (hyp.contains(NH) ? " (pi_a, nH from memory)" : " (pi_b, nH from c)")
          + " Hyp=" + Terms.sorted(hyp).stream().map(Terms::canonical).toList()
          + " Rules=" + NormalDerivations.rules(d));
      System.out.print(NormalDerivations.render(d).indent(4));
    }

    Set<String> cueIds = Set.of("hint", "autofill");
    Set<Association> assoc = Set.of(new Association("hint", PW1), new Association("autofill", H));
    CueModel none = new CueModel(cueIds, assoc, Set.of());
    CueModel hint = new CueModel(cueIds, assoc, Set.of("hint"));
    CueModel both = new CueModel(cueIds, assoc, Set.of("hint", "autofill"));

    List<Scenario> sc = new ArrayList<>();
    sc.add(new Scenario("S0", "no lapse", List.of(), none));
    sc.add(new Scenario("S1", "Forget(pw1), no cue", List.of(new MessageLapse(PW1)), none));
    sc.add(new Scenario("S2", "Forget(pw1), hint present", List.of(new MessageLapse(PW1)), hint));
    sc.add(new Scenario("S3", "Forget(pw1) + ForgetRule(adec)",
        List.of(new MessageLapse(PW1), new RuleLapse("adec")), none));
    sc.add(new Scenario("S4", "ForgetRule(adec) only", List.of(new RuleLapse("adec")), none));
    sc.add(new Scenario("S5", "ForgetProcedure(pi_b) only", List.of(new ProcedureLapse(piB)), none));
    sc.add(new Scenario("S6", "ForgetProcedure(pi_a)+ForgetProcedure(pi_b)",
        List.of(new ProcedureLapse(piA), new ProcedureLapse(piB)), none));
    sc.add(new Scenario("S7", "Forget(pw1)+Forget(nH), no cue",
        List.of(new MessageLapse(PW1), new MessageLapse(NH)), none));
    sc.add(new Scenario("S8", "Forget(pw1)+Forget(nH), hint present",
        List.of(new MessageLapse(PW1), new MessageLapse(NH)), hint));
    sc.add(new Scenario("S9", "Forget(pw1)+Forget(H), hint only",
        List.of(new MessageLapse(PW1), new MessageLapse(H)), hint));
    sc.add(new Scenario("S10", "Forget(pw1)+Forget(H), hint and autofill",
        List.of(new MessageLapse(PW1), new MessageLapse(H)), both));

    ForgetDlEngine prod = new ForgetDlEngine();
    ForgetDlEngine ntd = new ForgetDlEngine(new ResearchVariantReasoners.NoTeamDefeat(), Set.of(),
        ForgetDlEngine.Limits.DEFAULT);
    ForgetDlEngine ap = new ForgetDlEngine(new ResearchVariantReasoners.AmbiguityPropagating(),
        Set.of(), ForgetDlEngine.Limits.DEFAULT);

    System.out.println("\n## Decisions");
    System.out.println("| id | scenario | Forget-DL (X-Men 2.0) | variants | |T| | |D| facts/rules/sup | "
        + "Alg. 1 (SCP26 semantics) | no team defeat | ambiguity propagation |");
    System.out.println("|---|---|---|---|---|---|---|---|---|");
    Map<String, ForgetDlResult> keep = new LinkedHashMap<>();
    for (Scenario s : sc) {
      TransitionInput.Builder b = TransitionInput.builder(HumanState.of(knowledge()))
          .received(C).send(M3).cues(s.cues()).closure(BlockingMode.CASE1_WEAK).labels(labels());
      s.lapses().forEach(b::lapse);
      TransitionInput in = b.build();
      ForgetDlResult r = prod.run(in);
      keep.put(s.id(), r);
      ForgetDlResult rn = ntd.run(in);
      ForgetDlResult ra = ap.run(in);
      Set<Message> forgetAfter = r.after().forget();
      ResearchAlg1.Outcome a1 = ResearchAlg1.decide(r.after().knowledge(), forgetAfter,
          BlockingMode.CASE1_WEAK, labels(), M3);
      String vars = r.replacements().stream().map(c -> Terms.canonical(c.target())).toList().toString();
      String size = r.compiled() == null ? "-" : r.compiled().theory().facts().size() + "/"
          + r.compiled().theory().rules().size() + "/" + r.compiled().theory().superiorityPairs().size();
      System.out.println("| " + s.id() + " | " + s.description() + " | " + r.decision() + " | " + vars
          + " | " + r.candidates().size() + " | " + size + " | " + a1.decision()
          + (a1.variants().isEmpty() ? "" : " " + a1.variants().stream().map(Terms::canonical).toList())
          + " | " + rn.decision() + " | " + ra.decision() + " |");
    }
    for (String id : List.of("S1", "S2", "S3", "S8", "S10")) {
      System.out.println("\n## Cognitive trace " + id + "\n```");
      System.out.print(keep.get(id).explain());
      System.out.println("```");
    }
    System.out.println("\n## Compiled theory S2 (canonical text)\n```");
    System.out.print(keep.get("S2").compiled().theory().toText());
    System.out.println("```");
  }
}
