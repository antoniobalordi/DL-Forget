import java.lang.reflect.*;
import java.util.*;
/** Reflective runner: instantiates each class, invokes every @Test method, reports totals. */
public final class RunTests {
  public static void main(String[] args) throws Exception {
    int run = 0, failed = 0; List<String> failures = new ArrayList<>();
    for (String cn : args) {
      Class<?> c = Class.forName(cn);
      Constructor<?> k = c.getDeclaredConstructor(); k.setAccessible(true);
      List<Method> ms = new ArrayList<>(Arrays.asList(c.getDeclaredMethods()));
      ms.sort(Comparator.comparing(Method::getName));
      int cr = 0, cf = 0;
      for (Method m : ms) {
        if (!m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) continue;
        m.setAccessible(true); run++; cr++;
        long t0 = System.nanoTime();
        try { m.invoke(k.newInstance()); }
        catch (InvocationTargetException e) { failed++; cf++; failures.add(cn + "#" + m.getName() + ": " + e.getCause()); }
      }
      System.out.printf("%-60s tests=%d failures=%d%n", c.getSimpleName(), cr, cf);
    }
    System.out.println("TOTAL tests=" + run + " failures=" + failed);
    failures.forEach(f -> System.out.println("FAIL " + f));
    System.exit(failed == 0 ? 0 : 1);
  }
}
