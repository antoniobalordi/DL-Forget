package org.junit.jupiter.api;
import java.util.Objects;
import java.util.function.Supplier;
/** Minimal subset of the JUnit 5 Assertions API with the standard semantics. */
public final class Assertions {
  private Assertions() {}
  public interface Executable { void execute() throws Throwable; }
  private static String m(String msg, String d) { return (msg == null ? "" : msg + " ==> ") + d; }
  public static void assertEquals(Object e, Object a) { assertEquals(e, a, (String) null); }
  public static void assertEquals(Object e, Object a, String msg) { if (!Objects.equals(e, a)) throw new AssertionError(m(msg, "expected: <" + e + "> but was: <" + a + ">")); }
  public static void assertEquals(Object e, Object a, Supplier<String> msg) { if (!Objects.equals(e, a)) throw new AssertionError(m(msg.get(), "expected: <" + e + "> but was: <" + a + ">")); }
  public static void assertEquals(long e, long a) { if (e != a) throw new AssertionError("expected: <" + e + "> but was: <" + a + ">"); }
  public static void assertEquals(long e, long a, String msg) { if (e != a) throw new AssertionError(m(msg, "expected: <" + e + "> but was: <" + a + ">")); }
  public static void assertEquals(int e, int a) { if (e != a) throw new AssertionError("expected: <" + e + "> but was: <" + a + ">"); }
  public static void assertEquals(int e, int a, String msg) { if (e != a) throw new AssertionError(m(msg, "expected: <" + e + "> but was: <" + a + ">")); }
  public static void assertEquals(int e, int a, Supplier<String> msg) { if (e != a) throw new AssertionError(m(msg.get(), "expected: <" + e + "> but was: <" + a + ">")); }
  public static void assertNotEquals(Object e, Object a) { if (Objects.equals(e, a)) throw new AssertionError("expected: not equal but was: <" + a + ">"); }
  public static void assertNotEquals(Object e, Object a, String msg) { if (Objects.equals(e, a)) throw new AssertionError(m(msg, "expected: not equal but was: <" + a + ">")); }
  public static void assertTrue(boolean c) { if (!c) throw new AssertionError("expected: <true> but was: <false>"); }
  public static void assertTrue(boolean c, String msg) { if (!c) throw new AssertionError(m(msg, "expected: <true> but was: <false>")); }
  public static void assertTrue(boolean c, Supplier<String> msg) { if (!c) throw new AssertionError(m(msg.get(), "expected: <true> but was: <false>")); }
  public static void assertFalse(boolean c) { if (c) throw new AssertionError("expected: <false> but was: <true>"); }
  public static void assertFalse(boolean c, String msg) { if (c) throw new AssertionError(m(msg, "expected: <false> but was: <true>")); }
  public static void assertFalse(boolean c, Supplier<String> msg) { if (c) throw new AssertionError(m(msg.get(), "expected: <false> but was: <true>")); }
  public static <T extends Throwable> T assertThrows(Class<T> type, Executable ex) { return assertThrows(type, ex, (String) null); }
  public static <T extends Throwable> T assertThrows(Class<T> type, Executable ex, String msg) {
    try { ex.execute(); } catch (Throwable t) { if (type.isInstance(t)) return type.cast(t); throw new AssertionError(m(msg, "unexpected exception type " + t), t); }
    throw new AssertionError(m(msg, "expected " + type.getName() + " to be thrown"));
  }
}
