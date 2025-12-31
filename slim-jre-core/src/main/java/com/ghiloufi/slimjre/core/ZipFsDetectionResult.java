package com.ghiloufi.slimjre.core;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of scanning JARs for ZIP filesystem API usage.
 *
 * <p>Contains the set of required modules (typically {@code jdk.zipfs}), the patterns that
 * triggered detection, and the JAR files where patterns were found.
 *
 * <p>The ZIP filesystem provider allows treating ZIP and JAR files as filesystems, enabling
 * operations like {@code FileSystems.newFileSystem(path)} to access archive contents. This
 * functionality requires the {@code jdk.zipfs} module which is not detected by jdeps because it's
 * loaded via service provider mechanism at runtime.
 *
 * @param requiredModules modules that should be added to the JRE (e.g., jdk.zipfs)
 * @param detectedPatterns ZIP filesystem API patterns found in bytecode
 * @param detectedInJars JAR file names where ZIP filesystem patterns were detected
 */
public record ZipFsDetectionResult(
    Set<String> requiredModules, Set<String> detectedPatterns, Set<String> detectedInJars) {

  public ZipFsDetectionResult {
    // Defensive copies
    requiredModules = Set.copyOf(requiredModules);
    detectedPatterns = Set.copyOf(detectedPatterns);
    detectedInJars = Set.copyOf(detectedInJars);
  }

  /**
   * Returns true if ZIP filesystem modules are required based on detected patterns.
   *
   * @return true if ZIP filesystem API usage was detected
   */
  public boolean isRequired() {
    return !requiredModules.isEmpty();
  }

  /**
   * Returns a human-readable summary of the detection results.
   *
   * @return formatted summary string
   */
  public String summary() {
    if (!isRequired()) {
      return "No ZIP filesystem patterns detected - jdk.zipfs module not required";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("ZIP filesystem modules required: ")
        .append(String.join(", ", requiredModules))
        .append("\n");
    sb.append("Detected in JARs:\n");
    for (String jar : detectedInJars) {
      sb.append("  - ").append(jar).append("\n");
    }
    sb.append("Patterns found: ");
    if (detectedPatterns.size() <= 5) {
      sb.append(String.join(", ", detectedPatterns));
    } else {
      sb.append(detectedPatterns.stream().limit(5).collect(Collectors.joining(", ")));
      sb.append(" ... (").append(detectedPatterns.size()).append(" total)");
    }
    return sb.toString();
  }

  /** Creates an empty result indicating no ZIP filesystem modules are required. */
  public static ZipFsDetectionResult empty() {
    return new ZipFsDetectionResult(Set.of(), Set.of(), Set.of());
  }
}
