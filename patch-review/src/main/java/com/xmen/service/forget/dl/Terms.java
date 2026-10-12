package com.xmen.service.forget.dl;

import com.xmen.model.Atom;
import com.xmen.model.Encrypt;
import com.xmen.model.Message;
import com.xmen.model.Pair;
import com.xmen.model.PredictiveFunction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Message-algebra helpers (paper Section 3.1) over the existing X-Men {@link Message} model.
 *
 * <p>Supported fragment: atoms, pairs, symmetric encryption ({@code senc/2}, and the legacy
 * {@link Encrypt} term), asymmetric encryption {@code aenc(m, pk(sk))}, and any other public
 * free constructor (e.g. {@code sign/2}, {@code h/1}, {@code pk/1}, user-defined functions).
 * Function symbols with equational theories (XOR, Diffie-Hellman, bilinear pairing) and
 * destructor symbols occurring inside terms are reported as unsupported.
 */
public final class Terms {

  /** Format of atomic messages (see assumption A2 in the semantic contract). */
  public static final String ATOM_FORMAT = "atom";

  private static final Set<String> DESTRUCTOR_SYMBOLS =
      Set.of("fst", "snd", "sdec", "adec", "verify", "getmessage", "revealverify");

  private static final Set<String> EQUATIONAL_SYMBOLS =
      Set.of("xor", "^", "*", "inv", "pmult", "em", "exp", "mult", "++", "diff");

  private Terms() {}

  /** Deterministic structural rendering used for ordering, atom names and procedure ids. */
  public static String canonical(Message m) {
    if (m instanceof Atom a) {
      return a.getValue();
    }
    if (m instanceof Pair p) {
      return "<" + canonical(p.getLeft()) + "," + canonical(p.getRight()) + ">";
    }
    if (m instanceof Encrypt e) {
      return "{" + canonical(e.getMsg()) + "}_" + canonical(e.getKey());
    }
    if (m instanceof PredictiveFunction f) {
      StringBuilder sb = new StringBuilder(f.getName()).append('(');
      for (int i = 0; i < f.getArgs().size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(canonical(f.getArgs().get(i)));
      }
      return sb.append(')').toString();
    }
    throw new IllegalArgumentException("unsupported message class: " + m.getClass().getName());
  }

  /** Injective structural key; display text alone may identify an atom and a compound alike. */
  public static String identity(Message m) {
    if (m instanceof Atom a) {
      return "A" + field(a.getValue());
    }
    if (m instanceof Pair p) {
      return "P" + identity(p.getLeft()) + identity(p.getRight());
    }
    if (m instanceof Encrypt e) {
      return "E" + identity(e.getMsg()) + identity(e.getKey());
    }
    if (m instanceof PredictiveFunction f) {
      StringBuilder sb = new StringBuilder("F").append(field(f.getName()))
          .append(f.getArgs().size()).append(':');
      for (Message a : f.getArgs()) {
        sb.append(identity(a));
      }
      return sb.toString();
    }
    throw new IllegalArgumentException("unsupported message class: " + m.getClass().getName());
  }

  static String field(String value) {
    return value.length() + ":" + value;
  }

  public static final Comparator<Message> ORDER = Comparator.comparing(Terms::canonical)
      .thenComparing(Terms::identity);

  /** Immediate arguments (empty for atoms). */
  public static List<Message> args(Message m) {
    if (m instanceof Pair p) {
      return List.of(p.getLeft(), p.getRight());
    }
    if (m instanceof Encrypt e) {
      return List.of(e.getMsg(), e.getKey());
    }
    if (m instanceof PredictiveFunction f) {
      return f.getArgs();
    }
    return List.of();
  }

  /** All subterms of {@code m}, including {@code m} itself (Definition 2, extended to all args). */
  public static Set<Message> subterms(Message m) {
    Set<Message> out = new LinkedHashSet<>();
    collect(m, out);
    return out;
  }

  public static Set<Message> subterms(Iterable<Message> ms) {
    Set<Message> out = new LinkedHashSet<>();
    for (Message m : ms) {
      collect(m, out);
    }
    return out;
  }

  private static void collect(Message m, Set<Message> out) {
    if (out.add(m)) {
      for (Message a : args(m)) {
        collect(a, out);
      }
    }
  }

  public static boolean occursIn(Message needle, Message haystack) {
    return subterms(haystack).contains(needle);
  }

  /**
   * Definition 3: the top-level function symbol. Atoms share the format {@link #ATOM_FORMAT}
   * (assumption A2: read literally, Def. 3 would make every atom its own format and forbid the
   * paper's own PW1 → PW2 replacement).
   */
  public static String format(Message m) {
    if (m instanceof Atom) {
      return ATOM_FORMAT;
    }
    if (m instanceof Pair) {
      return "pair";
    }
    if (m instanceof Encrypt) {
      return "senc/2";
    }
    if (m instanceof PredictiveFunction f) {
      return f.getName() + "/" + f.getArgs().size();
    }
    throw new IllegalArgumentException("unsupported message class: " + m.getClass().getName());
  }

  /** Returns a description of the first unsupported construct in {@code m}, if any. */
  public static Optional<String> unsupportedConstruct(Message m) {
    for (Message s : subterms(m)) {
      if (s instanceof PredictiveFunction f) {
        String n = f.getName().toLowerCase();
        if (EQUATIONAL_SYMBOLS.contains(n)) {
          return Optional.of("equational function symbol '" + f.getName() + "' in " + canonical(m));
        }
        if (DESTRUCTOR_SYMBOLS.contains(n)) {
          return Optional.of("destructor symbol '" + f.getName() + "' inside term " + canonical(m));
        }
      } else if (!(s instanceof Atom || s instanceof Pair || s instanceof Encrypt)) {
        return Optional.of("unsupported message class " + s.getClass().getSimpleName());
      }
    }
    return Optional.empty();
  }

  /**
   * Constructor (composition rule) label of {@code m}, or empty if {@code m} cannot be composed
   * from its arguments. Labels are the paper's rule names: {@code pair}, {@code senc},
   * {@code aenc}, and the function symbol for other public constructors (e.g. {@code sign}).
   */
  public static Optional<String> constructorLabel(Message m, Set<String> privateFunctions) {
    if (m instanceof Pair) {
      return Optional.of("pair");
    }
    if (m instanceof Encrypt) {
      return Optional.of("senc");
    }
    if (m instanceof PredictiveFunction f) {
      String n = f.getName();
      if (privateFunctions.contains(n) || DESTRUCTOR_SYMBOLS.contains(n.toLowerCase())
          || EQUATIONAL_SYMBOLS.contains(n.toLowerCase())) {
        return Optional.empty();
      }
      return Optional.of(n);
    }
    return Optional.empty();
  }

  /** A ciphertext view: plaintext and the key needed to decrypt it. */
  public record Cipher(Message ciphertext, Message plaintext, Message decryptionKey,
      String decryptLabel, String encryptLabel) {}

  /** Symmetric ciphertexts: {@code senc(m, k)} or legacy {@code {m}_k}; decryption key is k. */
  public static Optional<Cipher> symmetric(Message c) {
    if (c instanceof Encrypt e) {
      return Optional.of(new Cipher(c, e.getMsg(), e.getKey(), "sdec", "senc"));
    }
    if (c instanceof PredictiveFunction f && f.getName().equals("senc") && f.getArgs().size() == 2) {
      return Optional.of(new Cipher(c, f.getArgs().get(0), f.getArgs().get(1), "sdec", "senc"));
    }
    return Optional.empty();
  }

  /** Asymmetric ciphertexts {@code aenc(m, pk(sk))}; decryption key is sk (k^-1). */
  public static Optional<Cipher> asymmetric(Message c) {
    if (c instanceof PredictiveFunction f && f.getName().equals("aenc") && f.getArgs().size() == 2
        && f.getArgs().get(1) instanceof PredictiveFunction pk
        && pk.getName().equals("pk") && pk.getArgs().size() == 1) {
      return Optional.of(new Cipher(c, f.getArgs().get(0), pk.getArgs().get(0), "adec", "aenc"));
    }
    return Optional.empty();
  }

  /**
   * Simultaneous, outermost-first syntactic substitution {@code m[C]} (Algorithm 2, line 18):
   * every occurrence of a key of {@code sub} in {@code m} is replaced by its image.
   */
  public static Message substitute(Message m, Map<Message, Message> sub) {
    Message direct = sub.get(m);
    if (direct != null) {
      return direct;
    }
    if (m instanceof Pair p) {
      Message l = substitute(p.getLeft(), sub);
      Message r = substitute(p.getRight(), sub);
      return (l == p.getLeft() && r == p.getRight()) ? m : new Pair(l, r);
    }
    if (m instanceof Encrypt e) {
      Message pl = substitute(e.getMsg(), sub);
      Message k = substitute(e.getKey(), sub);
      return (pl == e.getMsg() && k == e.getKey()) ? m : new Encrypt(pl, k);
    }
    if (m instanceof PredictiveFunction f) {
      List<Message> as = new ArrayList<>(f.getArgs().size());
      boolean changed = false;
      for (Message a : f.getArgs()) {
        Message b = substitute(a, sub);
        changed |= b != a;
        as.add(b);
      }
      return changed ? new PredictiveFunction(f.getName(), as) : m;
    }
    return m;
  }

  /** Sorted copy (deterministic iteration independent of the caller's set implementation). */
  public static List<Message> sorted(java.util.Collection<Message> ms) {
    List<Message> out = new ArrayList<>(ms);
    out.sort(ORDER);
    return out;
  }
}
