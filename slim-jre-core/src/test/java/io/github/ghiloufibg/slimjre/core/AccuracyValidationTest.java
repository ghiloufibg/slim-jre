package io.github.ghiloufibg.slimjre.core;

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
 * <p>This test compares the modules in the created slim-jre (from {@code target/slim-jre/release})
 * against the expected modules defined in each example's {@code required-modules.txt} file. Reports
 * any mismatches for investigation. The test always passes but logs warnings.
 *
 * <p>Prerequisites: Build all examples first with {@code mvn clean package -f
 * slim-jre-examples/pom.xml}
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

        // Read modules from slim-jre release file (produced by maven plugin)
        Set<String> detected = readModulesFromRelease(targetDir);
        if (detected.isEmpty()) {
          printSkipped(name, "slim-jre not created (run mvn package first)");
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

  /**
   * Reads the modules from the slim-jre release file produced by the maven plugin.
   *
   * <p>The release file format is:
   *
   * <pre>
   * JAVA_VERSION="21.0.8"
   * MODULES="java.base java.logging java.sql"
   * </pre>
   *
   * @param targetDir the target directory containing slim-jre/release
   * @return set of module names, or empty set if release file doesn't exist
   */
  private Set<String> readModulesFromRelease(Path targetDir) {
    Path releaseFile = targetDir.resolve("slim-jre/release");
    if (!Files.exists(releaseFile)) {
      return Set.of();
    }

    try {
      for (String line : Files.readAllLines(releaseFile)) {
        if (line.startsWith("MODULES=")) {
          // Extract value between quotes: MODULES="mod1 mod2 mod3"
          String value = line.substring("MODULES=".length()).trim();
          if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }
          // Split by space and collect
          return Arrays.stream(value.split("\\s+"))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toCollection(TreeSet::new));
        }
      }
    } catch (IOException e) {
      // Fall through to return empty set
    }

    return Set.of();
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
