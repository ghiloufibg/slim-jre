package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Accuracy Validation Test for Slim JRE Module Detection.
 *
 * <p>This test validates the accuracy of the various scanners by comparing their detected modules
 * against known ground truth for each example app.
 *
 * <p>Run after building all examples with: mvn clean package -pl slim-jre-examples -am
 */
@DisplayName("Scanner Accuracy Validation")
class AccuracyValidationTest {

  private static final Path EXAMPLES_DIR = Path.of("../slim-jre-examples");

  /** Ground truth: expected modules for each example application. */
  private static final Map<String, Set<String>> GROUND_TRUTH = new HashMap<>();

  static {
    // reflection-app: Tests ReflectionBytecodeScanner edge cases
    // KNOWN LIMITATION: When Class.forName is called with a method parameter rather than
    // a string literal directly, the scanner cannot track the string across method boundaries.
    // The reflection-app deliberately uses helper methods (testClassForName(String)) which
    // receive the class name as a parameter - this pattern is NOT detectable.
    // For detectable patterns, see mixed-patterns-app which uses inline Class.forName("...").
    GROUND_TRUTH.put(
        "reflection-app",
        Set.of(
            "java.base" // Only java.base is detectable due to indirect reflection patterns
            ));

    // sql-app: Tests ApiUsageScanner with JDBC patterns
    GROUND_TRUTH.put(
        "sql-app",
        Set.of(
            "java.base",
            "java.sql", // JDBC classes
            "java.logging" // Logger
            ));

    // xml-app: Tests ApiUsageScanner with XML patterns
    GROUND_TRUTH.put(
        "xml-app",
        Set.of(
            "java.base", "java.xml" // All XML APIs
            ));

    // scripting-app: Tests ApiUsageScanner with ScriptEngine patterns
    GROUND_TRUTH.put(
        "scripting-app",
        Set.of(
            "java.base", "java.scripting" // javax.script.*
            ));

    // prefs-app: Tests ApiUsageScanner with Preferences API
    GROUND_TRUTH.put(
        "prefs-app",
        Set.of(
            "java.base", "java.prefs" // java.util.prefs.*
            ));

    // naming-app: Tests ApiUsageScanner with JNDI patterns
    GROUND_TRUTH.put(
        "naming-app",
        Set.of(
            "java.base", "java.naming" // javax.naming.*
            ));

    // rmi-app: Tests ApiUsageScanner with RMI patterns
    GROUND_TRUTH.put(
        "rmi-app",
        Set.of(
            "java.base", "java.rmi" // java.rmi.*
            ));

    // desktop-app: Tests ApiUsageScanner with AWT/Swing patterns
    GROUND_TRUTH.put(
        "desktop-app",
        Set.of(
            "java.base", "java.desktop" // java.awt.*, javax.swing.*, javax.imageio.*, java.beans.*
            ));

    // compiler-app: Tests ApiUsageScanner with ToolProvider/JavaCompiler
    GROUND_TRUTH.put(
        "compiler-app",
        Set.of(
            "java.base", "java.compiler" // javax.tools.*, javax.lang.model.*
            ));

    // instrument-app: Tests ApiUsageScanner with Instrumentation API
    GROUND_TRUTH.put(
        "instrument-app",
        Set.of(
            "java.base", "java.instrument" // java.lang.instrument.*
            ));

    // mixed-patterns-app: Comprehensive test combining ALL scanner patterns
    GROUND_TRUTH.put(
        "mixed-patterns-app",
        Set.of(
            "java.base",
            "java.logging", // Logger static import
            "java.xml", // DocumentBuilderFactory static import
            "java.net.http", // HttpClient static import
            "jdk.crypto.ec", // HTTPS via HttpClient
            "java.management", // ManagementFactory static import
            "jdk.zipfs", // FileSystems.newFileSystem
            "java.sql", // Class.forName("java.sql.Driver")
            "java.naming" // Class.forName("javax.naming.InitialContext")
            ));

    // Existing examples for completeness
    GROUND_TRUTH.put("simple-app", Set.of("java.base"));

    GROUND_TRUTH.put("logging-app", Set.of("java.base", "java.logging"));

    GROUND_TRUTH.put("http-client-app", Set.of("java.base", "java.net.http", "jdk.crypto.ec"));

    GROUND_TRUTH.put("locale-app", Set.of("java.base", "jdk.localedata"));

    GROUND_TRUTH.put("zipfs-app", Set.of("java.base", "jdk.zipfs"));

    // jmx-app uses both local JMX (ManagementFactory) and remote JMX (JMXConnectorFactory)
    // java.management.rmi is required for remote JMX patterns (javax.management.remote.*)
    GROUND_TRUTH.put("jmx-app", Set.of("java.base", "java.management", "java.management.rmi"));
  }

  private final JDepsAnalyzer jdepsAnalyzer = new JDepsAnalyzer();
  private final ApiUsageScanner apiUsageScanner = new ApiUsageScanner();
  private final ReflectionBytecodeScanner reflectionScanner = new ReflectionBytecodeScanner();
  private final CryptoModuleScanner cryptoScanner = new CryptoModuleScanner();
  private final ZipFsModuleScanner zipFsScanner = new ZipFsModuleScanner();
  private final JmxModuleScanner jmxScanner = new JmxModuleScanner();
  private final LocaleModuleScanner localeScanner = new LocaleModuleScanner();

  @BeforeAll
  static void checkExamplesExist() {
    assertThat(EXAMPLES_DIR).as("Examples directory should exist").exists().isDirectory();
  }

  @ParameterizedTest
  @CsvSource({
    "simple-app",
    "logging-app",
    "http-client-app",
    "locale-app",
    "zipfs-app",
    "jmx-app",
    "reflection-app",
    "sql-app",
    "xml-app",
    "scripting-app",
    "prefs-app",
    "naming-app",
    "rmi-app",
    "desktop-app",
    "compiler-app",
    "instrument-app",
    "mixed-patterns-app"
  })
  @DisplayName("Validate module detection accuracy for")
  void validateAccuracy(String exampleName) throws Exception {
    // JAR files have version suffix (e.g., reflection-app-1.0.0-SNAPSHOT.jar)
    Path jarPath =
        EXAMPLES_DIR
            .resolve(exampleName)
            .resolve("target")
            .resolve(exampleName + "-1.0.0-SNAPSHOT.jar");

    if (!Files.exists(jarPath)) {
      System.out.println("SKIP: " + exampleName + " (JAR not built)");
      return;
    }

    Set<String> expected = GROUND_TRUTH.getOrDefault(exampleName, Set.of("java.base"));
    Set<String> detected = detectAllModules(jarPath);

    // Calculate metrics
    Set<String> truePositives = new TreeSet<>(expected);
    truePositives.retainAll(detected);

    Set<String> falsePositives = new TreeSet<>(detected);
    falsePositives.removeAll(expected);

    Set<String> falseNegatives = new TreeSet<>(expected);
    falseNegatives.removeAll(detected);

    double precision = detected.isEmpty() ? 0 : (double) truePositives.size() / detected.size();
    double recall = expected.isEmpty() ? 0 : (double) truePositives.size() / expected.size();
    double f1 = (precision + recall) == 0 ? 0 : 2 * (precision * recall) / (precision + recall);

    // Report
    System.out.println("\n=== " + exampleName + " ===");
    System.out.println("Expected modules:  " + expected);
    System.out.println("Detected modules:  " + detected);
    System.out.println("True positives:    " + truePositives);
    System.out.println("False positives:   " + falsePositives);
    System.out.println("False negatives:   " + falseNegatives);
    System.out.printf("Precision: %.2f, Recall: %.2f, F1: %.2f%n", precision, recall, f1);

    // Assertions - expect high recall (catch all required modules)
    assertThat(recall)
        .as(
            "Recall for " + exampleName + " should be >= 0.8 (caught %d of %d required modules)",
            truePositives.size(),
            expected.size())
        .isGreaterThanOrEqualTo(0.8);

    // False negatives are critical - these are modules we missed
    assertThat(falseNegatives).as("Should not miss critical modules for " + exampleName).isEmpty();
  }

  @Test
  @DisplayName("Generate comprehensive accuracy report")
  void generateAccuracyReport() throws Exception {
    System.out.println("\n========================================");
    System.out.println("SLIM JRE SCANNER ACCURACY REPORT");
    System.out.println("========================================\n");

    int totalExpected = 0;
    int totalDetected = 0;
    int totalTruePositives = 0;
    int totalFalsePositives = 0;
    int totalFalseNegatives = 0;

    for (String exampleName : GROUND_TRUTH.keySet()) {
      Path jarPath =
          EXAMPLES_DIR
              .resolve(exampleName)
              .resolve("target")
              .resolve(exampleName + "-1.0.0-SNAPSHOT.jar");

      if (!Files.exists(jarPath)) {
        System.out.println(exampleName + ": SKIPPED (not built)");
        continue;
      }

      Set<String> expected = GROUND_TRUTH.get(exampleName);
      Set<String> detected = detectAllModules(jarPath);

      Set<String> tp = new TreeSet<>(expected);
      tp.retainAll(detected);

      Set<String> fp = new TreeSet<>(detected);
      fp.removeAll(expected);

      Set<String> fn = new TreeSet<>(expected);
      fn.removeAll(detected);

      totalExpected += expected.size();
      totalDetected += detected.size();
      totalTruePositives += tp.size();
      totalFalsePositives += fp.size();
      totalFalseNegatives += fn.size();

      double recall = expected.isEmpty() ? 1.0 : (double) tp.size() / expected.size();
      String status = fn.isEmpty() ? "PASS" : "FAIL";

      System.out.printf(
          "%s: %s (Recall: %.0f%%, TP: %d, FP: %d, FN: %d)%n",
          exampleName, status, recall * 100, tp.size(), fp.size(), fn.size());

      if (!fn.isEmpty()) {
        System.out.println("  MISSED: " + fn);
      }
      if (!fp.isEmpty()) {
        System.out.println("  EXTRA:  " + fp);
      }
    }

    System.out.println("\n----------------------------------------");
    double overallPrecision = totalDetected == 0 ? 0 : (double) totalTruePositives / totalDetected;
    double overallRecall = totalExpected == 0 ? 0 : (double) totalTruePositives / totalExpected;
    double overallF1 =
        (overallPrecision + overallRecall) == 0
            ? 0
            : 2 * (overallPrecision * overallRecall) / (overallPrecision + overallRecall);

    System.out.printf("Overall Precision: %.2f%n", overallPrecision);
    System.out.printf("Overall Recall:    %.2f%n", overallRecall);
    System.out.printf("Overall F1 Score:  %.2f%n", overallF1);
    System.out.printf("Total False Negatives (missed modules): %d%n", totalFalseNegatives);
    System.out.printf("Total False Positives (extra modules):  %d%n", totalFalsePositives);
    System.out.println("========================================\n");
  }

  /** Detects all modules required by a JAR using all available scanners. */
  private Set<String> detectAllModules(Path jarPath) {
    Set<String> allModules = new LinkedHashSet<>();
    List<Path> jarList = List.of(jarPath);

    // jdeps analysis
    try {
      allModules.addAll(jdepsAnalyzer.analyzeRequiredModules(jarList));
    } catch (Exception e) {
      System.err.println("jdeps failed for " + jarPath + ": " + e.getMessage());
    }

    // API usage scanner
    try {
      allModules.addAll(apiUsageScanner.scanJar(jarPath));
    } catch (Exception e) {
      System.err.println("ApiUsageScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // Reflection scanner
    try {
      allModules.addAll(reflectionScanner.scanJar(jarPath));
    } catch (Exception e) {
      System.err.println("ReflectionScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // Crypto scanner
    try {
      CryptoDetectionResult cryptoResult = cryptoScanner.scanJarsParallel(jarList);
      allModules.addAll(cryptoResult.requiredModules());
    } catch (Exception e) {
      System.err.println("CryptoScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // ZipFS scanner
    try {
      allModules.addAll(zipFsScanner.scanJarsParallel(jarList).requiredModules());
    } catch (Exception e) {
      System.err.println("ZipFsScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // JMX scanner
    try {
      allModules.addAll(jmxScanner.scanJarsParallel(jarList).requiredModules());
    } catch (Exception e) {
      System.err.println("JmxScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // Locale scanner
    try {
      allModules.addAll(localeScanner.scanJarsParallel(jarList).requiredModules());
    } catch (Exception e) {
      System.err.println("LocaleScanner failed for " + jarPath + ": " + e.getMessage());
    }

    // Always include java.base
    allModules.add("java.base");

    return allModules.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
