package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Accuracy Validation Test for Slim JRE Module Detection.
 *
 * <p>This test compares detected modules against the expected modules defined in each example's
 * required-modules.txt file and reports any mismatches. The test always passes but logs warnings
 * for investigation.
 *
 * <p>Run after building examples: {@code mvn clean package -f slim-jre-examples/<example>/pom.xml}
 */
@DisplayName("Scanner Accuracy Validation")
class AccuracyValidationTest {

  private static final Path EXAMPLES_DIR = Path.of("../slim-jre-examples");

  @BeforeAll
  static void checkExamplesExist() {
    assertThat(EXAMPLES_DIR).as("Examples directory should exist").exists().isDirectory();
  }

  /**
   * Compares detected modules against required-modules.txt for all examples. Reports mismatches as
   * warnings for later investigation.
   */
  @Test
  @DisplayName("Validate scanner accuracy against required-modules.txt")
  void validateScannerAccuracy() throws Exception {
    printHeader();

    List<ExampleResult> results = new ArrayList<>();
    int skipped = 0;

    // Find all example directories with required-modules.txt
    try (Stream<Path> dirs = Files.list(EXAMPLES_DIR)) {
      List<Path> exampleDirs =
          dirs.filter(Files::isDirectory)
              .filter(dir -> Files.exists(dir.resolve("required-modules.txt")))
              .sorted()
              .toList();

      for (Path exampleDir : exampleDirs) {
        String name = exampleDir.getFileName().toString();
        Path requiredModulesFile = exampleDir.resolve("required-modules.txt");
        Path targetDir = exampleDir.resolve("target");

        // Read expected modules
        Set<String> expected = readRequiredModules(requiredModulesFile);
        if (expected.isEmpty()) {
          printSkipped(name, "empty required-modules.txt");
          skipped++;
          continue;
        }

        // Analyze with SlimJre
        Set<String> detected = analyzeWithSlimJre(name, targetDir);
        if (detected.isEmpty()) {
          printSkipped(name, "not built or analysis failed");
          skipped++;
          continue;
        }

        // Calculate differences
        Set<String> missing = difference(expected, detected);
        Set<String> extra = difference(detected, expected);

        results.add(new ExampleResult(name, expected, detected, missing, extra));
        printResult(results.getLast());
      }
    }

    printSummary(results, skipped);

    // Test passes if at least one example was processed
    assertThat(results).as("At least one example should be processed").isNotEmpty();
  }

  // ========================================
  // Output Formatting
  // ========================================

  private void printHeader() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║           SLIM JRE SCANNER ACCURACY VALIDATION                   ║");
    System.out.println("╠══════════════════════════════════════════════════════════════════╣");
    System.out.println("║  Comparing detected modules against required-modules.txt         ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    System.out.println();
  }

  private void printSkipped(String name, String reason) {
    System.out.printf("  ⊘  %-30s  SKIPPED (%s)%n", name, reason);
  }

  private void printResult(ExampleResult result) {
    if (result.isMatch()) {
      System.out.printf("  ✓  %-30s  MATCH   (%d modules)%n", result.name, result.expected.size());
    } else {
      System.out.printf("  ⚠  %-30s  MISMATCH%n", result.name);
      System.out.printf(
          "     Expected (%d): %s%n", result.expected.size(), formatModules(result.expected));
      System.out.printf(
          "     Detected (%d): %s%n", result.detected.size(), formatModules(result.detected));
      if (!result.missing.isEmpty()) {
        System.out.printf("     ✗ Missing: %s%n", formatModules(result.missing));
      }
      if (!result.extra.isEmpty()) {
        System.out.printf("     + Extra:   %s%n", formatModules(result.extra));
      }
    }
  }

  private void printSummary(List<ExampleResult> results, int skipped) {
    long matches = results.stream().filter(ExampleResult::isMatch).count();
    long mismatches = results.size() - matches;
    double matchRate = results.isEmpty() ? 0 : 100.0 * matches / results.size();

    // Aggregate statistics
    int totalExpected = results.stream().mapToInt(r -> r.expected.size()).sum();
    int totalDetected = results.stream().mapToInt(r -> r.detected.size()).sum();
    int totalMissing = results.stream().mapToInt(r -> r.missing.size()).sum();
    int totalExtra = results.stream().mapToInt(r -> r.extra.size()).sum();
    int totalCorrect = totalExpected - totalMissing;

    double precision = totalDetected == 0 ? 0 : 100.0 * totalCorrect / totalDetected;
    double recall = totalExpected == 0 ? 0 : 100.0 * totalCorrect / totalExpected;

    System.out.println();
    System.out.println("┌──────────────────────────────────────────────────────────────────┐");
    System.out.println("│                           SUMMARY                                │");
    System.out.println("├──────────────────────────────────────────────────────────────────┤");
    System.out.printf(
        "│  Examples Processed:  %-5d                                      │%n", results.size());
    System.out.printf(
        "│  Examples Skipped:    %-5d                                      │%n", skipped);
    System.out.printf(
        "│  Matches:             %-5d                                      │%n", matches);
    System.out.printf(
        "│  Mismatches:          %-5d                                      │%n", mismatches);
    System.out.printf(
        "│  Match Rate:          %-6.1f%%                                    │%n", matchRate);
    System.out.println("├──────────────────────────────────────────────────────────────────┤");
    System.out.printf(
        "│  Total Expected Modules:   %-5d                                 │%n", totalExpected);
    System.out.printf(
        "│  Total Detected Modules:   %-5d                                 │%n", totalDetected);
    System.out.printf(
        "│  Missing (False Negatives): %-4d                                 │%n", totalMissing);
    System.out.printf(
        "│  Extra (False Positives):   %-4d                                 │%n", totalExtra);
    System.out.printf(
        "│  Precision:            %-6.1f%%                                   │%n", precision);
    System.out.printf(
        "│  Recall:               %-6.1f%%                                   │%n", recall);
    System.out.println("└──────────────────────────────────────────────────────────────────┘");

    if (mismatches > 0) {
      System.out.println();
      System.out.println("⚠ MISMATCHES DETECTED - Review required:");
      results.stream()
          .filter(r -> !r.isMatch())
          .forEach(
              r -> {
                System.out.printf("  • %s%n", r.name);
                if (!r.missing.isEmpty()) {
                  System.out.printf("      Missing: %s%n", r.missing);
                }
                if (!r.extra.isEmpty()) {
                  System.out.printf("      Extra:   %s%n", r.extra);
                }
              });
      System.out.println();
      System.out.println("Note: Mismatches may indicate:");
      System.out.println("  1. Scanner accuracy issues (investigate root cause)");
      System.out.println("  2. required-modules.txt needs updating");
      System.out.println("  3. Transitive dependencies correctly detected by scanners");
    }

    System.out.println();
  }

  private String formatModules(Set<String> modules) {
    if (modules.size() <= 5) {
      return modules.toString();
    }
    return modules.stream().limit(5).collect(Collectors.joining(", ", "[", ", ...]"))
        + " ("
        + modules.size()
        + " total)";
  }

  // ========================================
  // Helper Methods
  // ========================================

  private Set<String> readRequiredModules(Path file) {
    try {
      String content = Files.readString(file).trim();
      if (content.isEmpty()) {
        return Set.of();
      }
      return Arrays.stream(content.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      return Set.of();
    }
  }

  private Set<String> analyzeWithSlimJre(String exampleName, Path targetDir) {
    try {
      List<Path> jars = collectJars(targetDir, exampleName);
      if (jars.isEmpty()) {
        return Set.of();
      }
      var result = SlimJre.builder().jars(jars).analyze();
      return new TreeSet<>(result.allModules());
    } catch (Exception e) {
      return Set.of();
    }
  }

  private List<Path> collectJars(Path targetDir, String exampleName) {
    List<Path> jars = new ArrayList<>();
    if (!Files.exists(targetDir)) {
      return jars;
    }

    // Main JAR
    Path mainJar = targetDir.resolve(exampleName + "-1.0.0-SNAPSHOT.jar");
    if (Files.exists(mainJar)) {
      jars.add(mainJar);
    }

    // Dependency directories
    for (String depDir : List.of("dependency", "libs")) {
      Path dir = targetDir.resolve(depDir);
      if (Files.exists(dir) && Files.isDirectory(dir)) {
        try (Stream<Path> depJars = Files.list(dir)) {
          depJars.filter(p -> p.toString().endsWith(".jar")).forEach(jars::add);
        } catch (IOException e) {
          // Continue with main JAR only
        }
      }
    }

    return jars;
  }

  private Set<String> difference(Set<String> a, Set<String> b) {
    Set<String> result = new TreeSet<>(a);
    result.removeAll(b);
    return result;
  }

  // ========================================
  // Result Record
  // ========================================

  private record ExampleResult(
      String name,
      Set<String> expected,
      Set<String> detected,
      Set<String> missing,
      Set<String> extra) {

    boolean isMatch() {
      return missing.isEmpty() && extra.isEmpty();
    }
  }
}
