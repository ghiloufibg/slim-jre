package io.github.ghiloufibg.slimjre.core;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of scanning JARs for remote JMX API usage.
 *
 * <p>Contains the set of required JMX modules (typically {@code java.management.rmi}), the patterns
 * that triggered detection, and the JAR files where patterns were found.
 *
 * <p>Remote JMX functionality requires the {@code java.management.rmi} module which is not
 * automatically included by jdeps because JMX remote connectors use service provider mechanisms
 * that are resolved at runtime.
 *
 * @param requiredModules JMX modules that should be added to the JRE (e.g., java.management.rmi)
 * @param detectedPatterns JMX remote API patterns found in bytecode (e.g.,
 *     javax/management/remote/JMXConnectorFactory)
 * @param detectedInJars JAR file names where JMX remote patterns were detected
 */
public record JmxDetectionResult(
    Set<String> requiredModules, Set<String> detectedPatterns, Set<String> detectedInJars) {

  public JmxDetectionResult {
    // Defensive copies
    requiredModules = Set.copyOf(requiredModules);
    detectedPatterns = Set.copyOf(detectedPatterns);
    detectedInJars = Set.copyOf(detectedInJars);
  }

  /**
   * Returns true if JMX remote modules are required based on detected patterns.
   *
   * @return true if remote JMX API usage was detected
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
      return "No remote JMX patterns detected - java.management.rmi module not required";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("JMX modules required: ").append(String.join(", ", requiredModules)).append("\n");
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

  /** Creates an empty result indicating no JMX remote modules are required. */
  public static JmxDetectionResult empty() {
    return new JmxDetectionResult(Set.of(), Set.of(), Set.of());
  }
}
