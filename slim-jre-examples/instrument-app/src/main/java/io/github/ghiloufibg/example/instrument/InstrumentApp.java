package io.github.ghiloufibg.example.instrument;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

/**
 * Tests java.instrument module detection with Instrumentation API.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.instrument - Instrumentation, ClassFileTransformer, ClassDefinition,
 *       UnmodifiableClassException
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect java.lang.instrument.* usage
 *   <li>jdeps: MUST detect java.lang.instrument.* imports
 * </ul>
 *
 * <p>This class can be used as a Java agent (premain/agentmain).
 */
public class InstrumentApp implements ClassFileTransformer {

  private static Instrumentation instrumentation;

  /** Premain entry point for Java agent (-javaagent:...). */
  public static void premain(String args, Instrumentation inst) {
    System.out.println("[AGENT] premain called with args: " + args);
    instrumentation = inst;
    inst.addTransformer(new InstrumentApp(), true);
  }

  /** Agentmain entry point for attach API. */
  public static void agentmain(String args, Instrumentation inst) {
    System.out.println("[AGENT] agentmain called with args: " + args);
    instrumentation = inst;
    inst.addTransformer(new InstrumentApp(), true);
  }

  public static void main(String[] args) {
    System.out.println("=== Instrumentation API Pattern Test ===\n");

    // Test 1: Type references
    testTypeReferences();

    // Test 2: ClassFileTransformer
    testClassFileTransformer();

    // Test 3: Instrumentation operations
    testInstrumentationOperations();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testTypeReferences() {
    System.out.println("--- Instrumentation Type References ---");

    // Type references that require java.instrument
    System.out.println("[INFO] Instrumentation type: " + Instrumentation.class.getName());
    System.out.println("[INFO] ClassFileTransformer type: " + ClassFileTransformer.class.getName());
    System.out.println("[INFO] ClassDefinition type: " + ClassDefinition.class.getName());
    System.out.println(
        "[INFO] IllegalClassFormatException type: " + IllegalClassFormatException.class.getName());
    System.out.println(
        "[INFO] UnmodifiableClassException type: " + UnmodifiableClassException.class.getName());
  }

  private static void testClassFileTransformer() {
    System.out.println("\n--- ClassFileTransformer Pattern ---");

    ClassFileTransformer transformer = new InstrumentApp();
    System.out.println("[OK] Created ClassFileTransformer: " + transformer.getClass().getName());
  }

  private static void testInstrumentationOperations() {
    System.out.println("\n--- Instrumentation Operations ---");

    if (instrumentation == null) {
      System.out.println("[INFO] Instrumentation not available (not running as agent)");
      System.out.println(
          "[INFO] Run with: java -javaagent:instrument-app.jar -jar instrument-app.jar");
      return;
    }

    // Instrumentation operations
    System.out.println("[OK] All classes loaded: " + instrumentation.getAllLoadedClasses().length);
    System.out.println(
        "[OK] Initiated classes: " + instrumentation.getInitiatedClasses(null).length);
    System.out.println(
        "[OK] Native method prefix supported: " + instrumentation.isNativeMethodPrefixSupported());
    System.out.println(
        "[OK] Retransform supported: " + instrumentation.isRetransformClassesSupported());
    System.out.println("[OK] Redefine supported: " + instrumentation.isRedefineClassesSupported());
  }

  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer)
      throws IllegalClassFormatException {

    // Simple passthrough transformer for testing
    if (className != null && className.startsWith("com/ghiloufi/")) {
      System.out.println("[TRANSFORM] Visiting: " + className);
    }
    return null; // Return null to use original bytecode
  }
}
