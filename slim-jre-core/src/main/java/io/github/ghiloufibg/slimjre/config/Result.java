package io.github.ghiloufibg.slimjre.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of creating a minimal JRE.
 *
 * @param jrePath Path to the created JRE
 * @param includedModules Modules included in the JRE
 * @param originalJreSize Size of the original JDK/JRE in bytes
 * @param slimJreSize Size of the created slim JRE in bytes
 * @param duration Time taken to create the JRE
 */
public record Result(
    Path jrePath,
    Set<String> includedModules,
    long originalJreSize,
    long slimJreSize,
    Duration duration) {
  public Result {
    includedModules = Set.copyOf(includedModules);
  }

  /** Calculates the compression ratio (slim/original). */
  public double compressionRatio() {
    if (originalJreSize == 0) {
      return 0;
    }
    return (double) slimJreSize / originalJreSize;
  }

  /** Calculates the size reduction percentage. */
  public double reductionPercentage() {
    return (1 - compressionRatio()) * 100;
  }

  /** Returns a formatted summary of the result. */
  public String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Slim JRE created at: ").append(jrePath.toAbsolutePath()).append("\n");
    sb.append("Modules: ")
        .append(formatModules())
        .append(" (")
        .append(includedModules.size())
        .append(" modules)\n");
    sb.append("Size: ").append(formatSize(slimJreSize));

    if (originalJreSize > 0) {
      sb.append(" (was ")
          .append(formatSize(originalJreSize))
          .append(", ")
          .append(String.format("%.0f%%", reductionPercentage()))
          .append(" reduction)");
    }
    sb.append("\n");

    sb.append("Time: ").append(formatDuration(duration)).append("\n");

    return sb.toString();
  }

  private String formatModules() {
    if (includedModules.size() <= 5) {
      return includedModules.stream().sorted().collect(Collectors.joining(", "));
    }
    return includedModules.stream().sorted().limit(5).collect(Collectors.joining(", ")) + ", ...";
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024));
    } else {
      return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
  }

  private String formatDuration(Duration duration) {
    long millis = duration.toMillis();
    if (millis < 1000) {
      return millis + "ms";
    } else {
      return String.format("%.1fs", millis / 1000.0);
    }
  }
}
