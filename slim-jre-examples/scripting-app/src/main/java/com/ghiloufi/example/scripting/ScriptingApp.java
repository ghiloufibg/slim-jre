package com.ghiloufi.example.scripting;

import java.util.List;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Tests java.scripting module detection with ScriptEngine patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.scripting - ScriptEngineManager, ScriptEngine, Bindings, etc.
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect javax.script.* usage
 *   <li>jdeps: MUST detect javax.script.* imports
 *   <li>ServiceLoaderScanner: May detect ScriptEngineFactory service loading
 * </ul>
 *
 * <p>NOTE: Without a JavaScript engine (Nashorn removed in JDK 15), script execution will fail, but
 * the bytecode references to javax.script.* are what we're testing for detection.
 */
public class ScriptingApp {

  public static void main(String[] args) {
    System.out.println("=== Scripting API Pattern Test ===\n");

    // Test 1: ScriptEngineManager
    testScriptEngineManager();

    // Test 2: ScriptEngine types
    testScriptEngineTypes();

    // Test 3: Bindings and Context
    testBindingsAndContext();

    // Test 4: Compilable interface
    testCompilable();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testScriptEngineManager() {
    System.out.println("--- ScriptEngineManager Pattern ---");

    ScriptEngineManager manager = new ScriptEngineManager();
    List<ScriptEngineFactory> factories = manager.getEngineFactories();

    System.out.println("[INFO] Available ScriptEngines: " + factories.size());
    for (ScriptEngineFactory factory : factories) {
      System.out.println("  - " + factory.getEngineName() + " (" + factory.getLanguageName() + ")");
    }

    // Try to get various engines (may not exist)
    String[] engineNames = {"javascript", "js", "groovy", "python", "ruby"};
    for (String name : engineNames) {
      ScriptEngine engine = manager.getEngineByName(name);
      if (engine != null) {
        System.out.println("[OK] Found engine: " + name);
      } else {
        System.out.println("[INFO] Engine not available: " + name);
      }
    }
  }

  private static void testScriptEngineTypes() {
    System.out.println("\n--- ScriptEngine Type References ---");

    // Type references that require java.scripting
    System.out.println("[INFO] ScriptEngine type: " + ScriptEngine.class.getName());
    System.out.println("[INFO] ScriptEngineManager type: " + ScriptEngineManager.class.getName());
    System.out.println("[INFO] ScriptEngineFactory type: " + ScriptEngineFactory.class.getName());
    System.out.println("[INFO] ScriptException type: " + ScriptException.class.getName());
  }

  private static void testBindingsAndContext() {
    System.out.println("\n--- Bindings and Context Patterns ---");

    ScriptEngineManager manager = new ScriptEngineManager();
    Bindings globalBindings = manager.getBindings();

    System.out.println("[INFO] Bindings type: " + Bindings.class.getName());
    System.out.println("[INFO] ScriptContext type: " + ScriptContext.class.getName());

    // Set some global bindings
    globalBindings.put("appName", "ScriptingApp");
    globalBindings.put("version", "1.0");
    System.out.println("[OK] Global bindings set: " + globalBindings.keySet());
  }

  private static void testCompilable() {
    System.out.println("\n--- Compilable Interface Pattern ---");

    // Type references for compilation support
    System.out.println("[INFO] Compilable type: " + Compilable.class.getName());
    System.out.println("[INFO] CompiledScript type: " + CompiledScript.class.getName());
    System.out.println("[INFO] Invocable type: " + Invocable.class.getName());

    // Try to find a compilable engine
    ScriptEngineManager manager = new ScriptEngineManager();
    for (ScriptEngineFactory factory : manager.getEngineFactories()) {
      ScriptEngine engine = factory.getScriptEngine();
      if (engine instanceof Compilable) {
        System.out.println("[OK] Found compilable engine: " + factory.getEngineName());
      }
      if (engine instanceof Invocable) {
        System.out.println("[OK] Found invocable engine: " + factory.getEngineName());
      }
    }
  }
}
