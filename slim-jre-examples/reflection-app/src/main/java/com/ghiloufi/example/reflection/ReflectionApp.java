package com.ghiloufi.example.reflection;

/**
 * Tests ReflectionBytecodeScanner with various Class.forName and ClassLoader.loadClass patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.sql - Class.forName("java.sql.Driver")
 *   <li>java.naming - Class.forName("javax.naming.InitialContext")
 *   <li>java.logging - Class.forName("java.util.logging.Logger")
 *   <li>java.xml - Class.forName("javax.xml.parsers.DocumentBuilderFactory")
 *   <li>java.management - Class.forName("javax.management.MBeanServer")
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ReflectionBytecodeScanner: MUST detect all patterns
 *   <li>ApiUsageScanner: Should NOT detect (no static type references)
 *   <li>jdeps: Will NOT detect (no static references in bytecode)
 * </ul>
 */
public class ReflectionApp {

  public static void main(String[] args) {
    System.out.println("=== Reflection Pattern Test ===\n");

    // Pattern 1: Class.forName(String)
    testClassForName("java.sql.Driver");
    testClassForName("javax.naming.InitialContext");

    // Pattern 2: Class.forName with initialization
    testClassForNameWithInit("java.util.logging.Logger");
    testClassForNameWithInit("javax.xml.parsers.DocumentBuilderFactory");

    // Pattern 3: ClassLoader.loadClass
    testClassLoaderLoadClass("javax.management.MBeanServer");

    // Pattern 4: Variable-based (harder to detect)
    String className = "java.sql.Connection";
    testClassForName(className);

    // Pattern 5: Concatenated class names (very hard to detect)
    String prefix = "java.sql.";
    testClassForName(prefix + "Statement");

    System.out.println("\n=== Test Complete ===");
  }

  private static void testClassForName(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      System.out.println("[OK] Class.forName: " + clazz.getName());
    } catch (ClassNotFoundException e) {
      System.out.println("[MISSING] Class.forName: " + className + " - " + e.getMessage());
    }
  }

  private static void testClassForNameWithInit(String className) {
    try {
      Class<?> clazz = Class.forName(className, true, ReflectionApp.class.getClassLoader());
      System.out.println("[OK] Class.forName(init): " + clazz.getName());
    } catch (ClassNotFoundException e) {
      System.out.println("[MISSING] Class.forName(init): " + className + " - " + e.getMessage());
    }
  }

  private static void testClassLoaderLoadClass(String className) {
    try {
      Class<?> clazz = ReflectionApp.class.getClassLoader().loadClass(className);
      System.out.println("[OK] ClassLoader.loadClass: " + clazz.getName());
    } catch (ClassNotFoundException e) {
      System.out.println("[MISSING] ClassLoader.loadClass: " + className + " - " + e.getMessage());
    }
  }
}
