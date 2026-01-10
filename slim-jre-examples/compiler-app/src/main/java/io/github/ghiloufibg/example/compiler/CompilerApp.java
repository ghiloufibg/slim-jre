package io.github.ghiloufibg.example.compiler;

import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Tests java.compiler module detection with ToolProvider/JavaCompiler.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.compiler - JavaCompiler, ToolProvider, DiagnosticCollector, JavaFileObject,
 *       StandardJavaFileManager, SourceVersion, Element, TypeElement, TypeMirror
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect javax.tools.*, javax.lang.model.*
 *   <li>jdeps: MUST detect all static imports
 * </ul>
 *
 * <p>NOTE: This requires JDK (not just JRE) at runtime for actual compilation.
 */
public class CompilerApp {

  public static void main(String[] args) {
    System.out.println("=== Compiler API Pattern Test ===\n");

    // Test 1: ToolProvider
    testToolProvider();

    // Test 2: JavaCompiler
    testJavaCompiler();

    // Test 3: Compile source code
    testCompilation();

    // Test 4: Language model types
    testLanguageModel();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testToolProvider() {
    System.out.println("--- ToolProvider Pattern ---");

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler != null) {
      System.out.println("[OK] System Java compiler available");
    } else {
      System.out.println("[INFO] No compiler available (JRE only)");
    }

    // Type references
    System.out.println("[INFO] ToolProvider type: " + ToolProvider.class.getName());
  }

  private static void testJavaCompiler() {
    System.out.println("\n--- JavaCompiler Pattern ---");

    // Type references
    System.out.println("[INFO] JavaCompiler type: " + JavaCompiler.class.getName());
    System.out.println("[INFO] JavaFileObject type: " + JavaFileObject.class.getName());
    System.out.println(
        "[INFO] StandardJavaFileManager type: " + StandardJavaFileManager.class.getName());
    System.out.println("[INFO] DiagnosticCollector type: " + DiagnosticCollector.class.getName());
    System.out.println("[INFO] Diagnostic type: " + Diagnostic.class.getName());
  }

  private static void testCompilation() {
    System.out.println("\n--- Compilation Pattern ---");

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.out.println("[INFO] Skipping compilation test (no compiler)");
      return;
    }

    // Simple source code
    String source =
        """
        public class HelloWorld {
            public static void main(String[] args) {
                System.out.println("Hello, World!");
            }
        }
        """;

    // Create in-memory file object
    JavaFileObject sourceFile = new StringJavaFileObject("HelloWorld", source);

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StringWriter output = new StringWriter();

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, null)) {

      List<String> options = Arrays.asList("-d", System.getProperty("java.io.tmpdir"));
      JavaCompiler.CompilationTask task =
          compiler.getTask(output, fileManager, diagnostics, options, null, List.of(sourceFile));

      boolean success = task.call();
      System.out.println("[OK] Compilation " + (success ? "succeeded" : "failed"));

      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
        System.out.println("  " + diagnostic.getKind() + ": " + diagnostic.getMessage(null));
      }

    } catch (Exception e) {
      System.out.println("[INFO] Compilation test: " + e.getMessage());
    }
  }

  private static void testLanguageModel() {
    System.out.println("\n--- Language Model Pattern ---");

    // javax.lang.model types
    System.out.println("[INFO] SourceVersion type: " + SourceVersion.class.getName());
    System.out.println("[INFO] Element type: " + Element.class.getName());
    System.out.println("[INFO] ElementKind type: " + ElementKind.class.getName());
    System.out.println("[INFO] TypeElement type: " + TypeElement.class.getName());
    System.out.println("[INFO] TypeMirror type: " + TypeMirror.class.getName());

    // Check current source version
    SourceVersion latest = SourceVersion.latest();
    System.out.println("[OK] Latest SourceVersion: " + latest);
  }

  /** In-memory Java source file for compilation. */
  static class StringJavaFileObject extends SimpleJavaFileObject {
    private final String code;

    StringJavaFileObject(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }
}
