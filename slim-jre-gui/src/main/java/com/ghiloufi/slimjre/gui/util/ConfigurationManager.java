package com.ghiloufi.slimjre.gui.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages saving and loading GUI configuration to/from JSON files.
 *
 * <p>Configuration includes:
 *
 * <ul>
 *   <li>Selected JAR files
 *   <li>Output directory
 *   <li>All JRE creation options
 * </ul>
 */
public final class ConfigurationManager {

  private ConfigurationManager() {}

  /**
   * Saves configuration to a JSON file.
   *
   * @param config the configuration to save
   * @param outputPath path to write the JSON file
   * @throws IOException if writing fails
   */
  public static void saveConfiguration(GuiConfiguration config, Path outputPath)
      throws IOException {
    StringBuilder json = new StringBuilder();

    json.append("{\n");
    json.append("  \"version\": 1,\n");

    // JAR files
    json.append("  \"jarFiles\": [\n");
    List<Path> jars = config.jarFiles();
    for (int i = 0; i < jars.size(); i++) {
      json.append("    \"").append(escapeJson(jars.get(i).toString())).append("\"");
      if (i < jars.size() - 1) json.append(",");
      json.append("\n");
    }
    json.append("  ],\n");

    // Output directory
    json.append("  \"outputDirectory\": \"")
        .append(escapeJson(config.outputDirectory().toString()))
        .append("\",\n");

    // Options
    json.append("  \"options\": {\n");
    json.append("    \"stripDebug\": ").append(config.stripDebug()).append(",\n");
    json.append("    \"scanServiceLoaders\": ").append(config.scanServiceLoaders()).append(",\n");
    json.append("    \"scanGraalVmMetadata\": ").append(config.scanGraalVmMetadata()).append(",\n");
    json.append("    \"verbose\": ").append(config.verbose()).append(",\n");
    json.append("    \"compression\": \"").append(config.compression()).append("\",\n");
    json.append("    \"additionalModules\": \"")
        .append(escapeJson(config.additionalModules()))
        .append("\",\n");
    json.append("    \"excludeModules\": \"")
        .append(escapeJson(config.excludeModules()))
        .append("\"\n");
    json.append("  }\n");

    json.append("}\n");

    Files.writeString(outputPath, json.toString());
  }

  /**
   * Loads configuration from a JSON file.
   *
   * @param inputPath path to read the JSON file from
   * @return loaded configuration
   * @throws IOException if reading or parsing fails
   */
  public static GuiConfiguration loadConfiguration(Path inputPath) throws IOException {
    String content = Files.readString(inputPath);

    // Simple JSON parsing (no external library)
    List<Path> jarFiles = new ArrayList<>();
    Path outputDirectory = Path.of("./slim-jre");
    boolean stripDebug = true;
    boolean scanServiceLoaders = true;
    boolean scanGraalVmMetadata = true;
    boolean verbose = false;
    String compression = "zip-6";
    String additionalModules = "";
    String excludeModules = "";

    // Parse JAR files
    int jarArrayStart = content.indexOf("\"jarFiles\"");
    if (jarArrayStart != -1) {
      int arrayStart = content.indexOf("[", jarArrayStart);
      int arrayEnd = content.indexOf("]", arrayStart);
      if (arrayStart != -1 && arrayEnd != -1) {
        String jarArrayContent = content.substring(arrayStart + 1, arrayEnd);
        for (String part : jarArrayContent.split(",")) {
          String jarPath = extractStringValue(part);
          if (jarPath != null && !jarPath.isEmpty()) {
            jarFiles.add(Path.of(jarPath));
          }
        }
      }
    }

    // Parse output directory
    String outputDirValue = extractJsonValue(content, "outputDirectory");
    if (outputDirValue != null) {
      outputDirectory = Path.of(outputDirValue);
    }

    // Parse options
    stripDebug = extractBooleanValue(content, "stripDebug", true);
    scanServiceLoaders = extractBooleanValue(content, "scanServiceLoaders", true);
    scanGraalVmMetadata = extractBooleanValue(content, "scanGraalVmMetadata", true);
    verbose = extractBooleanValue(content, "verbose", false);

    String compressionValue = extractJsonValue(content, "compression");
    if (compressionValue != null) {
      compression = compressionValue;
    }

    String additionalValue = extractJsonValue(content, "additionalModules");
    if (additionalValue != null) {
      additionalModules = additionalValue;
    }

    String excludeValue = extractJsonValue(content, "excludeModules");
    if (excludeValue != null) {
      excludeModules = excludeValue;
    }

    return new GuiConfiguration(
        jarFiles,
        outputDirectory,
        stripDebug,
        scanServiceLoaders,
        scanGraalVmMetadata,
        verbose,
        compression,
        additionalModules,
        excludeModules);
  }

  private static String extractJsonValue(String json, String key) {
    String pattern = "\"" + key + "\"";
    int keyIndex = json.indexOf(pattern);
    if (keyIndex == -1) return null;

    int colonIndex = json.indexOf(":", keyIndex);
    if (colonIndex == -1) return null;

    int valueStart = colonIndex + 1;
    // Skip whitespace
    while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
      valueStart++;
    }

    if (valueStart >= json.length()) return null;

    if (json.charAt(valueStart) == '"') {
      // String value
      int valueEnd = json.indexOf("\"", valueStart + 1);
      if (valueEnd == -1) return null;
      return unescapeJson(json.substring(valueStart + 1, valueEnd));
    }

    return null;
  }

  private static boolean extractBooleanValue(String json, String key, boolean defaultValue) {
    String pattern = "\"" + key + "\"";
    int keyIndex = json.indexOf(pattern);
    if (keyIndex == -1) return defaultValue;

    int colonIndex = json.indexOf(":", keyIndex);
    if (colonIndex == -1) return defaultValue;

    String remainder = json.substring(colonIndex + 1).trim();
    if (remainder.startsWith("true")) return true;
    if (remainder.startsWith("false")) return false;
    return defaultValue;
  }

  private static String extractStringValue(String part) {
    int firstQuote = part.indexOf("\"");
    int lastQuote = part.lastIndexOf("\"");
    if (firstQuote != -1 && lastQuote > firstQuote) {
      return unescapeJson(part.substring(firstQuote + 1, lastQuote));
    }
    return null;
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String unescapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t");
  }

  /**
   * Configuration data holder.
   *
   * @param jarFiles list of JAR file paths
   * @param outputDirectory output directory for JRE
   * @param stripDebug strip debug information
   * @param scanServiceLoaders scan for service loader modules
   * @param scanGraalVmMetadata scan for GraalVM metadata
   * @param verbose verbose output
   * @param compression compression level
   * @param additionalModules additional modules to include
   * @param excludeModules modules to exclude
   */
  public record GuiConfiguration(
      List<Path> jarFiles,
      Path outputDirectory,
      boolean stripDebug,
      boolean scanServiceLoaders,
      boolean scanGraalVmMetadata,
      boolean verbose,
      String compression,
      String additionalModules,
      String excludeModules) {}
}
