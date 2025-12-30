package com.ghiloufi.slimjre.config;

import com.ghiloufi.slimjre.exception.ConfigurationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for creating a minimal JRE.
 *
 * @param jars JARs to analyze for module dependencies
 * @param outputPath Where to create the slim JRE
 * @param additionalModules Modules to force-include beyond those detected
 * @param excludeModules Modules to exclude from the final JRE
 * @param stripDebug Whether to strip debug information (default: true)
 * @param compression Compression level: zip-0 to zip-9 (default: zip-6)
 * @param noHeaderFiles Whether to exclude header files (default: true)
 * @param noManPages Whether to exclude man pages (default: true)
 * @param scanServiceLoaders Whether to scan META-INF/services (default: true)
 * @param scanGraalVmMetadata Whether to scan GraalVM native-image metadata (default: true)
 * @param cryptoMode How to handle SSL/TLS crypto module detection (default: AUTO)
 * @param verbose Whether to output verbose logging (default: false)
 */
public record SlimJreConfig(
    List<Path> jars,
    Path outputPath,
    Set<String> additionalModules,
    Set<String> excludeModules,
    boolean stripDebug,
    String compression,
    boolean noHeaderFiles,
    boolean noManPages,
    boolean scanServiceLoaders,
    boolean scanGraalVmMetadata,
    CryptoMode cryptoMode,
    boolean verbose) {
  public SlimJreConfig {
    // Defensive copies
    jars = List.copyOf(jars);
    additionalModules = Set.copyOf(additionalModules);
    excludeModules = Set.copyOf(excludeModules);
  }

  /** Creates a new builder for SlimJreConfig. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Validates this configuration.
   *
   * @throws ConfigurationException if the configuration is invalid
   */
  public void validate() {
    if (jars.isEmpty()) {
      throw new ConfigurationException("At least one JAR file must be specified");
    }

    for (Path jar : jars) {
      if (!Files.exists(jar)) {
        throw new ConfigurationException("JAR file does not exist: " + jar);
      }
      if (!Files.isReadable(jar)) {
        throw new ConfigurationException("JAR file is not readable: " + jar);
      }
    }

    if (outputPath == null) {
      throw new ConfigurationException("Output path must be specified");
    }

    Path parent = outputPath.getParent();
    if (parent != null && !Files.exists(parent)) {
      throw new ConfigurationException("Output directory parent does not exist: " + parent);
    }

    if (Files.exists(outputPath) && !Files.isDirectory(outputPath)) {
      throw new ConfigurationException("Output path exists but is not a directory: " + outputPath);
    }

    if (!compression.matches("zip-[0-9]")) {
      throw new ConfigurationException(
          "Invalid compression level: " + compression + ". Must be zip-0 to zip-9");
    }

    // Check JDK version >= 9
    int javaVersion = Runtime.version().feature();
    if (javaVersion < 9) {
      throw new ConfigurationException(
          "JDK 9 or higher is required for jlink. Current version: " + javaVersion);
    }
  }

  /** Builder for SlimJreConfig. */
  public static class Builder {
    private final List<Path> jars = new ArrayList<>();
    private Path outputPath = Path.of("slim-jre");
    private final Set<String> additionalModules = new HashSet<>();
    private final Set<String> excludeModules = new HashSet<>();
    private boolean stripDebug = true;
    private String compression = "zip-6";
    private boolean noHeaderFiles = true;
    private boolean noManPages = true;
    private boolean scanServiceLoaders = true;
    private boolean scanGraalVmMetadata = true;
    private CryptoMode cryptoMode = CryptoMode.AUTO;
    private boolean verbose = false;

    /** Adds a JAR file to analyze. */
    public Builder jar(Path jar) {
      this.jars.add(jar);
      return this;
    }

    /** Adds multiple JAR files to analyze. */
    public Builder jars(List<Path> jars) {
      this.jars.addAll(jars);
      return this;
    }

    /** Sets the output path for the slim JRE. */
    public Builder outputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    /** Adds a module to force-include. */
    public Builder addModule(String module) {
      this.additionalModules.add(module);
      return this;
    }

    /** Adds multiple modules to force-include. */
    public Builder additionalModules(Set<String> modules) {
      this.additionalModules.addAll(modules);
      return this;
    }

    /** Adds a module to exclude. */
    public Builder excludeModule(String module) {
      this.excludeModules.add(module);
      return this;
    }

    /** Adds multiple modules to exclude. */
    public Builder excludeModules(Set<String> modules) {
      this.excludeModules.addAll(modules);
      return this;
    }

    /** Sets whether to strip debug information. */
    public Builder stripDebug(boolean stripDebug) {
      this.stripDebug = stripDebug;
      return this;
    }

    /** Sets the compression level (zip-0 to zip-9). */
    public Builder compression(String compression) {
      this.compression = compression;
      return this;
    }

    /** Sets whether to exclude header files. */
    public Builder noHeaderFiles(boolean noHeaderFiles) {
      this.noHeaderFiles = noHeaderFiles;
      return this;
    }

    /** Sets whether to exclude man pages. */
    public Builder noManPages(boolean noManPages) {
      this.noManPages = noManPages;
      return this;
    }

    /** Sets whether to scan for service loader dependencies. */
    public Builder scanServiceLoaders(boolean scanServiceLoaders) {
      this.scanServiceLoaders = scanServiceLoaders;
      return this;
    }

    /** Sets whether to scan GraalVM native-image metadata for additional modules. */
    public Builder scanGraalVmMetadata(boolean scanGraalVmMetadata) {
      this.scanGraalVmMetadata = scanGraalVmMetadata;
      return this;
    }

    /** Sets the crypto module handling mode. */
    public Builder cryptoMode(CryptoMode cryptoMode) {
      this.cryptoMode = cryptoMode;
      return this;
    }

    /** Sets verbose output mode. */
    public Builder verbose(boolean verbose) {
      this.verbose = verbose;
      return this;
    }

    /** Builds the configuration. */
    public SlimJreConfig build() {
      return new SlimJreConfig(
          jars,
          outputPath,
          additionalModules,
          excludeModules,
          stripDebug,
          compression,
          noHeaderFiles,
          noManPages,
          scanServiceLoaders,
          scanGraalVmMetadata,
          cryptoMode,
          verbose);
    }
  }
}
