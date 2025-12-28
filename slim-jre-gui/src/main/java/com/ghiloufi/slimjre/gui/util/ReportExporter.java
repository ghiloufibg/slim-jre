package com.ghiloufi.slimjre.gui.util;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exports analysis and creation results to various formats.
 *
 * <p>Supports exporting to:
 *
 * <ul>
 *   <li>Text format - Human-readable report
 *   <li>JSON format - Machine-readable structured data
 * </ul>
 */
public final class ReportExporter {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private ReportExporter() {}

  /**
   * Exports analysis result to a text file.
   *
   * @param result the analysis result
   * @param jars list of analyzed JAR files
   * @param outputPath path to write the report
   * @throws IOException if writing fails
   */
  public static void exportAnalysisToText(AnalysisResult result, List<Path> jars, Path outputPath)
      throws IOException {
    StringBuilder sb = new StringBuilder();

    sb.append("Slim JRE Analysis Report\n");
    sb.append("========================\n\n");
    sb.append("Date: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
    sb.append("JARs Analyzed: ").append(jars.size()).append("\n\n");

    // JAR files section
    sb.append("JAR Files:\n");
    for (Path jar : jars) {
      try {
        long size = Files.size(jar);
        sb.append("  - ").append(jar.getFileName());
        sb.append(" (").append(SizeFormatter.format(size)).append(")\n");
      } catch (IOException e) {
        sb.append("  - ").append(jar.getFileName()).append("\n");
      }
    }
    sb.append("\n");

    // Module detection summary
    sb.append("Module Detection Summary:\n");
    sb.append(String.format("  jdeps:          %3d modules\n", result.requiredModules().size()));
    sb.append(
        String.format("  service loader: %3d modules\n", result.serviceLoaderModules().size()));
    sb.append(String.format("  reflection:     %3d modules\n", result.reflectionModules().size()));
    sb.append(String.format("  api_usage:      %3d modules\n", result.apiUsageModules().size()));
    sb.append(
        String.format("  graalvm:        %3d modules\n", result.graalVmMetadataModules().size()));
    sb.append("  ").append("-".repeat(25)).append("\n");
    sb.append(String.format("  Total:          %3d modules\n\n", result.allModules().size()));

    // All modules list
    sb.append("All Required Modules:\n");
    String moduleList = result.allModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 70, "  ")).append("\n\n");

    // Per-JAR breakdown
    Map<Path, Set<String>> perJar = result.perJarModules();
    if (perJar != null && !perJar.isEmpty()) {
      sb.append("Per-JAR Breakdown:\n");
      for (var entry : perJar.entrySet()) {
        sb.append("  ").append(entry.getKey().getFileName()).append(":\n");
        String jarModules = entry.getValue().stream().sorted().collect(Collectors.joining(", "));
        sb.append(wrapText(jarModules, 66, "    ")).append("\n");
      }
      sb.append("\n");
    }

    // Recommended jlink command
    sb.append("Recommended jlink command:\n");
    String modules = result.allModules().stream().sorted().collect(Collectors.joining(","));
    sb.append("  jlink --add-modules ").append(modules).append(" \\\n");
    sb.append("        --strip-debug --compress zip-6 \\\n");
    sb.append("        --no-header-files --no-man-pages \\\n");
    sb.append("        --output slim-jre\n");

    Files.writeString(outputPath, sb.toString());
  }

  /**
   * Exports analysis result to a JSON file.
   *
   * @param result the analysis result
   * @param jars list of analyzed JAR files
   * @param outputPath path to write the JSON
   * @throws IOException if writing fails
   */
  public static void exportAnalysisToJson(AnalysisResult result, List<Path> jars, Path outputPath)
      throws IOException {
    StringBuilder json = new StringBuilder();

    json.append("{\n");
    json.append("  \"timestamp\": \"")
        .append(LocalDateTime.now().format(ISO_FORMAT))
        .append("\",\n");
    json.append("  \"jars\": [\n");

    for (int i = 0; i < jars.size(); i++) {
      Path jar = jars.get(i);
      long size = 0;
      try {
        size = Files.size(jar);
      } catch (IOException e) {
        // Ignore
      }
      json.append("    {\"path\": \"").append(escapeJson(jar.toString()));
      json.append("\", \"size\": ").append(size).append("}");
      if (i < jars.size() - 1) json.append(",");
      json.append("\n");
    }

    json.append("  ],\n");
    json.append("  \"modules\": {\n");
    json.append("    \"jdeps\": ").append(toJsonArray(result.requiredModules())).append(",\n");
    json.append("    \"serviceLoader\": ")
        .append(toJsonArray(result.serviceLoaderModules()))
        .append(",\n");
    json.append("    \"reflection\": ")
        .append(toJsonArray(result.reflectionModules()))
        .append(",\n");
    json.append("    \"apiUsage\": ").append(toJsonArray(result.apiUsageModules())).append(",\n");
    json.append("    \"graalvm\": ")
        .append(toJsonArray(result.graalVmMetadataModules()))
        .append("\n");
    json.append("  },\n");
    json.append("  \"allModules\": ").append(toJsonArray(result.allModules())).append(",\n");
    json.append("  \"totalCount\": ").append(result.allModules().size()).append("\n");
    json.append("}\n");

    Files.writeString(outputPath, json.toString());
  }

  /**
   * Exports JRE creation result to a text file.
   *
   * @param result the creation result
   * @param outputPath path to write the report
   * @throws IOException if writing fails
   */
  public static void exportCreationResultToText(Result result, Path outputPath) throws IOException {
    StringBuilder sb = new StringBuilder();

    sb.append("Slim JRE Creation Report\n");
    sb.append("========================\n\n");
    sb.append("Date: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n\n");

    sb.append("Output Location:\n");
    sb.append("  ").append(result.jrePath()).append("\n\n");

    sb.append("Size Comparison:\n");
    sb.append(
        String.format("  Original JDK:  %s\n", SizeFormatter.format(result.originalJreSize())));
    sb.append(String.format("  Slim JRE:      %s\n", SizeFormatter.format(result.slimJreSize())));
    sb.append(String.format("  Reduction:     %.1f%%\n\n", result.reductionPercentage()));

    sb.append("Included Modules (").append(result.includedModules().size()).append("):\n");
    String moduleList =
        result.includedModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 70, "  ")).append("\n\n");

    sb.append("Duration: ").append(formatDuration(result.duration())).append("\n");

    Files.writeString(outputPath, sb.toString());
  }

  /**
   * Exports JRE creation result to a JSON file.
   *
   * @param result the creation result
   * @param outputPath path to write the JSON
   * @throws IOException if writing fails
   */
  public static void exportCreationResultToJson(Result result, Path outputPath) throws IOException {
    StringBuilder json = new StringBuilder();

    json.append("{\n");
    json.append("  \"timestamp\": \"")
        .append(LocalDateTime.now().format(ISO_FORMAT))
        .append("\",\n");
    json.append("  \"jrePath\": \"")
        .append(escapeJson(result.jrePath().toString()))
        .append("\",\n");
    json.append("  \"originalJreSize\": ").append(result.originalJreSize()).append(",\n");
    json.append("  \"slimJreSize\": ").append(result.slimJreSize()).append(",\n");
    json.append("  \"reductionPercentage\": ").append(result.reductionPercentage()).append(",\n");
    json.append("  \"durationMs\": ").append(result.duration().toMillis()).append(",\n");
    json.append("  \"includedModules\": ")
        .append(toJsonArray(result.includedModules()))
        .append(",\n");
    json.append("  \"moduleCount\": ").append(result.includedModules().size()).append("\n");
    json.append("}\n");

    Files.writeString(outputPath, json.toString());
  }

  private static String toJsonArray(Set<String> items) {
    if (items == null || items.isEmpty()) {
      return "[]";
    }
    return items.stream()
        .sorted()
        .map(s -> "\"" + escapeJson(s) + "\"")
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String wrapText(String text, int width, String indent) {
    StringBuilder sb = new StringBuilder();
    int lineLength = 0;
    boolean first = true;

    for (String word : text.split(", ")) {
      if (lineLength + word.length() + 2 > width) {
        sb.append(",\n").append(indent);
        lineLength = indent.length();
        first = true;
      }

      if (!first) {
        sb.append(", ");
        lineLength += 2;
      } else {
        if (sb.isEmpty()) {
          sb.append(indent);
          lineLength = indent.length();
        }
        first = false;
      }

      sb.append(word);
      lineLength += word.length();
    }

    return sb.toString();
  }

  private static String formatDuration(java.time.Duration duration) {
    long seconds = duration.getSeconds();
    long millis = duration.toMillisPart();
    if (seconds > 0) {
      return String.format("%d.%03d seconds", seconds, millis);
    } else {
      return String.format("%d ms", millis);
    }
  }
}
