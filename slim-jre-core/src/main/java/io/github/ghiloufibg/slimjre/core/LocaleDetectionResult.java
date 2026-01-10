package io.github.ghiloufibg.slimjre.core;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of scanning JARs for locale-related API usage.
 *
 * <p>Contains the confidence level, detected patterns organized by tier, and the JAR files where
 * patterns were found.
 *
 * <p>Detection tiers:
 *
 * <ul>
 *   <li><b>Tier 1 (Definite):</b> Explicit non-English locale constants like {@code Locale.FRENCH}
 *   <li><b>Tier 2 (Strong):</b> Internationalization APIs like {@code
 *       DateTimeFormatter.ofLocalizedDate()}
 *   <li><b>Tier 3 (Possible):</b> Common locale APIs like {@code Locale.getDefault()}
 * </ul>
 *
 * @param requiredModules modules that should be added (jdk.localedata if Tier 1 detected)
 * @param tier1Patterns definite patterns - explicit non-English locale constants
 * @param tier2Patterns strong indication patterns - i18n APIs
 * @param tier3Patterns possible need patterns - common locale APIs
 * @param detectedInJars JAR file names where locale patterns were detected
 * @param confidence overall confidence level for the detection
 */
public record LocaleDetectionResult(
    Set<String> requiredModules,
    Set<String> tier1Patterns,
    Set<String> tier2Patterns,
    Set<String> tier3Patterns,
    Set<String> detectedInJars,
    LocaleConfidence confidence) {

  public LocaleDetectionResult {
    // Defensive copies
    requiredModules = Set.copyOf(requiredModules);
    tier1Patterns = Set.copyOf(tier1Patterns);
    tier2Patterns = Set.copyOf(tier2Patterns);
    tier3Patterns = Set.copyOf(tier3Patterns);
    detectedInJars = Set.copyOf(detectedInJars);
  }

  /**
   * Returns true if locale data module is definitely required.
   *
   * @return true if Tier 1 patterns (explicit non-English locales) were detected
   */
  public boolean isRequired() {
    return !requiredModules.isEmpty();
  }

  /**
   * Returns true if internationalization APIs were detected (Tier 1 or Tier 2).
   *
   * @return true if the application appears to use internationalization
   */
  public boolean isI18nDetected() {
    return !tier1Patterns.isEmpty() || !tier2Patterns.isEmpty();
  }

  /**
   * Returns a human-readable summary of the detection results.
   *
   * @return formatted summary string
   */
  public String summary() {
    if (confidence == LocaleConfidence.NONE) {
      return "No locale-related patterns detected";
    }

    StringBuilder sb = new StringBuilder();

    switch (confidence) {
      case DEFINITE -> {
        sb.append("⚠️  Non-English locale detected - jdk.localedata required\n");
        sb.append("Patterns: ").append(formatPatterns(tier1Patterns)).append("\n");
      }
      case STRONG -> {
        sb.append("ℹ️  Internationalization APIs detected\n");
        sb.append("Consider --add-modules jdk.localedata if supporting non-English users\n");
        sb.append("Patterns: ").append(formatPatterns(tier2Patterns)).append("\n");
      }
      case POSSIBLE -> {
        sb.append("ℹ️  Common locale APIs detected (may work with English only)\n");
        sb.append("Patterns: ").append(formatPatterns(tier3Patterns)).append("\n");
      }
      default -> {}
    }

    if (!detectedInJars.isEmpty()) {
      sb.append("Detected in: ");
      if (detectedInJars.size() <= 3) {
        sb.append(String.join(", ", detectedInJars));
      } else {
        sb.append(detectedInJars.stream().limit(3).collect(Collectors.joining(", ")));
        sb.append(" ... (").append(detectedInJars.size()).append(" JARs total)");
      }
    }

    return sb.toString();
  }

  private String formatPatterns(Set<String> patterns) {
    if (patterns.isEmpty()) {
      return "(none)";
    }
    if (patterns.size() <= 5) {
      return String.join(", ", patterns);
    }
    return patterns.stream().limit(5).collect(Collectors.joining(", "))
        + " ... ("
        + patterns.size()
        + " total)";
  }

  /** Creates an empty result indicating no locale patterns were detected. */
  public static LocaleDetectionResult empty() {
    return new LocaleDetectionResult(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), LocaleConfidence.NONE);
  }
}
