package com.ghiloufi.slimjre.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of analyzing JARs for module dependencies.
 *
 * @param requiredModules Modules detected by jdeps analysis
 * @param serviceLoaderModules Modules required by service loader declarations
 * @param reflectionModules Modules required by Class.forName/loadClass/findClass reflection
 *     patterns
 * @param apiUsageModules Modules required by JDK API usage patterns
 * @param allModules Combined set of all required modules
 * @param perJarModules Breakdown of modules required by each JAR
 */
public record AnalysisResult(
    Set<String> requiredModules,
    Set<String> serviceLoaderModules,
    Set<String> reflectionModules,
    Set<String> apiUsageModules,
    Set<String> allModules,
    Map<Path, Set<String>> perJarModules) {

  /** Creates an AnalysisResult without API usage modules (backward compatibility). */
  public AnalysisResult(
      Set<String> requiredModules,
      Set<String> serviceLoaderModules,
      Set<String> reflectionModules,
      Set<String> allModules,
      Map<Path, Set<String>> perJarModules) {
    this(
        requiredModules,
        serviceLoaderModules,
        reflectionModules,
        Set.of(),
        allModules,
        perJarModules);
  }

  public AnalysisResult {
    // Defensive copies
    requiredModules = Set.copyOf(requiredModules);
    serviceLoaderModules = Set.copyOf(serviceLoaderModules);
    reflectionModules = Set.copyOf(reflectionModules);
    apiUsageModules = Set.copyOf(apiUsageModules);
    allModules = Set.copyOf(allModules);
    perJarModules = Map.copyOf(perJarModules);
  }

  /** Returns a formatted summary of the analysis. */
  public String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Analysis Result:\n");
    sb.append("  Required modules (jdeps): ").append(formatModules(requiredModules)).append("\n");
    sb.append("  Service loader modules: ")
        .append(formatModules(serviceLoaderModules))
        .append("\n");
    sb.append("  Reflection modules: ").append(formatModules(reflectionModules)).append("\n");
    sb.append("  API usage modules: ").append(formatModules(apiUsageModules)).append("\n");
    sb.append("  Total modules: ").append(allModules.size()).append("\n");

    if (!perJarModules.isEmpty()) {
      sb.append("\nPer-JAR breakdown:\n");
      for (var entry : perJarModules.entrySet()) {
        sb.append("  ")
            .append(entry.getKey().getFileName())
            .append(": ")
            .append(formatModules(entry.getValue()))
            .append("\n");
      }
    }

    return sb.toString();
  }

  private String formatModules(Set<String> modules) {
    if (modules.isEmpty()) {
      return "(none)";
    }
    return modules.stream().sorted().collect(Collectors.joining(", "));
  }
}
