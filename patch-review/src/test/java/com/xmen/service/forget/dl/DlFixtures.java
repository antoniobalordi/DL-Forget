package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Derivation;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.forget.dl.DefeasibleReasoner.Conclusions;
import com.xmen.service.forget.dl.ForgetDlModel.CueModel;
import com.xmen.service.forget.dl.ForgetDlModel.ProceduralState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Term builders and a direct compile-and-reason helper shared by the DL tests. */
final class DlFixtures {
  private DlFixtures() {}

  static Message a(String v) {
    return new Atom(v);
  }

  static Message pair(Message l, Message r) {
    return new Pair(l, r);
  }

  /** Right-nested tuple ⟨m1, ⟨m2, ...⟩⟩ (paper notation). */
  static Message tuple(Message... ms) {
    Message t = ms[ms.length - 1];
    for (int i = ms.length - 2; i >= 0; i--) {
      t = new Pair(ms[i], t);
    }
    return t;
  }

  static Message fn(String f, Message... args) {
    return new PredictiveFunction(f, List.of(args));
  }

  static Message senc(Message m, Message k) {
    return fn("senc", m, k);
  }

  static Message pk(Message sk) {
    return fn("pk", sk);
  }

  static Message aenc(Message m, Message sk) {
    return fn("aenc", m, pk(sk));
  }

  static Message sign(Message m, Message sk) {
    return fn("sign", m, sk);
  }

  /** Result of compiling a set of targets in one theory. */
  record Compiled(ForgetDlCompiler.Compiled compiled, Conclusions conclusions) {
    String usable(Message t) {
      return conclusions.partialTag(compiled.usable().get(t));
    }

    String via(Message t, int i) {
      return conclusions.partialTag(compiled.entries().get(t).get(i).via());
    }
  }

  static Compiled compile(Set<Message> k, Set<Message> forget, BlockingMode mode,
      ProceduralState proc, CueModel cues, Message... targets) {
    NormalDerivations nd = new NormalDerivations();
    Map<Message, List<Derivation>> byTarget = new LinkedHashMap<>();
    for (Message t : targets) {
      byTarget.put(t, nd.enumerate(t, k).derivations());
    }
    ForgetDlCompiler.Compiled c = new ForgetDlCompiler().compile(k,
        new ForgetBlocking(forget, mode, Set.of()), proc, cues, byTarget);
    return new Compiled(c, new AmbiguityBlockingReasoner().reason(c.theory()));
  }
}
