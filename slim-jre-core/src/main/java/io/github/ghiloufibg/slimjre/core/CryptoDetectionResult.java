package io.github.ghiloufibg.slimjre.core;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of scanning JARs for SSL/TLS and cryptographic API usage.
 *
 * <p>Contains the set of required crypto modules (typically {@code jdk.crypto.ec}), the patterns
 * that triggered detection, and the JAR files where patterns were found.
 *
 * @param requiredModules crypto modules that should be added to the JRE (e.g., jdk.crypto.ec)
 * @param detectedPatterns SSL/TLS API patterns found in bytecode (e.g., javax/net/ssl/SSLContext)
 * @param detectedInJars JAR file names where crypto patterns were detected
 */
public record CryptoDetectionResult(
    Set<String> requiredModules, Set<String> detectedPatterns, Set<String> detectedInJars) {

  public CryptoDetectionResult {
    // Defensive copies
    requiredModules = Set.copyOf(requiredModules);
    detectedPatterns = Set.copyOf(detectedPatterns);
    detectedInJars = Set.copyOf(detectedInJars);
  }

  /**
   * Returns true if crypto modules are required based on detected patterns.
   *
   * @return true if SSL/TLS or crypto API usage was detected
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
      return "No SSL/TLS patterns detected - crypto modules not required";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Crypto modules required: ").append(String.join(", ", requiredModules)).append("\n");
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

  /** Creates an empty result indicating no crypto modules are required. */
  public static CryptoDetectionResult empty() {
    return new CryptoDetectionResult(Set.of(), Set.of(), Set.of());
  }
}
