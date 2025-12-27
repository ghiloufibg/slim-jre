package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.config.*;
import com.ghiloufi.slimjre.exception.SlimJreException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level facade for creating minimal JREs. Orchestrates jdeps analysis, service loader
 * scanning, module resolution, and jlink execution.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var result = SlimJre.builder()
 *     .jar(Path.of("myapp.jar"))
 *     .outputPath(Path.of("custom-jre"))
 *     .build()
 *     .create();
 *
 * System.out.println(result.summary());
 * }</pre>
 */
public class SlimJre {

  private static final Logger log = LoggerFactory.getLogger(SlimJre.class);

  private final JDepsAnalyzer jdepsAnalyzer;
  private final ServiceLoaderScanner serviceLoaderScanner;
  private final ModuleResolver moduleResolver;
  private final JLinkExecutor jlinkExecutor;

  /** Creates a new SlimJre instance with default components. */
  public SlimJre() {
    this.jdepsAnalyzer = new JDepsAnalyzer();
    this.serviceLoaderScanner = new ServiceLoaderScanner();
    this.moduleResolver = new ModuleResolver();
    this.jlinkExecutor = new JLinkExecutor();
  }

  /** Creates a new SlimJre instance with custom components. */
  public SlimJre(
      JDepsAnalyzer jdepsAnalyzer,
      ServiceLoaderScanner serviceLoaderScanner,
      ModuleResolver moduleResolver,
      JLinkExecutor jlinkExecutor) {
    this.jdepsAnalyzer = Objects.requireNonNull(jdepsAnalyzer);
    this.serviceLoaderScanner = Objects.requireNonNull(serviceLoaderScanner);
    this.moduleResolver = Objects.requireNonNull(moduleResolver);
    this.jlinkExecutor = Objects.requireNonNull(jlinkExecutor);
  }

  /**
   * Creates a minimal JRE for the given configuration.
   *
   * @param config the configuration for JRE creation
   * @return the result containing the path and statistics
   * @throws SlimJreException if creation fails
   */
  public Result createMinimalJre(SlimJreConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    config.validate();

    Instant start = Instant.now();
    log.info("Creating minimal JRE for {} JAR(s)...", config.jars().size());

    if (config.verbose()) {
      log.info("Configuration: {}", config);
    }

    // Step 1: Analyze JARs with jdeps
    log.debug("Step 1: Analyzing JARs with jdeps...");
    Set<String> jdepsModules = jdepsAnalyzer.analyzeRequiredModules(config.jars());
    log.info("jdeps detected {} module(s): {}", jdepsModules.size(), formatModules(jdepsModules));

    // Step 2: Scan for service loader dependencies
    Set<String> serviceModules = Set.of();
    if (config.scanServiceLoaders()) {
      log.debug("Step 2: Scanning for service loader dependencies...");
      serviceModules = serviceLoaderScanner.scanForServiceModules(config.jars());
      if (!serviceModules.isEmpty()) {
        log.info(
            "Service loaders require {} module(s): {}",
            serviceModules.size(),
            formatModules(serviceModules));
      }
    }

    // Step 3: Combine all modules
    Set<String> allModules = new TreeSet<>();
    allModules.addAll(jdepsModules);
    allModules.addAll(serviceModules);
    allModules.addAll(config.additionalModules());

    // Remove excluded modules
    allModules.removeAll(config.excludeModules());

    log.debug("Combined modules before resolution: {}", allModules);

    // Step 4: Resolve transitive dependencies
    log.debug("Step 3: Resolving transitive module dependencies...");
    Set<String> resolvedModules = moduleResolver.resolveWithTransitive(allModules);
    log.info("Resolved {} total module(s) (including transitive)", resolvedModules.size());

    // Step 5: Create the JRE with jlink
    log.debug("Step 4: Creating custom JRE with jlink...");
    JLinkOptions jlinkOptions =
        JLinkOptions.builder()
            .modules(resolvedModules)
            .outputPath(config.outputPath())
            .stripDebug(config.stripDebug())
            .compression(config.compression())
            .noHeaderFiles(config.noHeaderFiles())
            .noManPages(config.noManPages())
            .build();

    Path jrePath = jlinkExecutor.createRuntime(jlinkOptions);

    // Calculate sizes
    long slimJreSize = jlinkExecutor.calculateJreSize(jrePath);
    long originalJreSize = jlinkExecutor.getCurrentJdkSize();

    Duration duration = Duration.between(start, Instant.now());

    Result result = new Result(jrePath, resolvedModules, originalJreSize, slimJreSize, duration);

    log.info("Minimal JRE creation complete!");
    if (config.verbose()) {
      log.info(result.summary());
    }

    return result;
  }

  /**
   * Analyzes JARs and returns required modules without creating a JRE. Useful for dry-run or
   * debugging.
   *
   * @param jarPath main JAR to analyze
   * @param classpath additional classpath JARs
   * @return analysis result with module breakdown
   */
  public AnalysisResult analyzeOnly(Path jarPath, List<Path> classpath) {
    List<Path> allJars = new ArrayList<>();
    allJars.add(jarPath);
    if (classpath != null) {
      allJars.addAll(classpath);
    }

    return analyzeOnly(allJars);
  }

  /**
   * Analyzes JARs and returns required modules without creating a JRE.
   *
   * @param jars JARs to analyze
   * @return analysis result with module breakdown
   */
  public AnalysisResult analyzeOnly(List<Path> jars) {
    log.info("Analyzing {} JAR(s)...", jars.size());

    // Analyze with jdeps
    Set<String> jdepsModules = jdepsAnalyzer.analyzeRequiredModules(jars);

    // Scan for service loaders
    Set<String> serviceModules = serviceLoaderScanner.scanForServiceModules(jars);

    // Per-JAR breakdown
    Map<Path, Set<String>> perJarModules = jdepsAnalyzer.analyzeRequiredModulesPerJar(jars);

    // Combine all modules
    Set<String> allModules = new TreeSet<>();
    allModules.addAll(jdepsModules);
    allModules.addAll(serviceModules);

    return new AnalysisResult(jdepsModules, serviceModules, allModules, perJarModules);
  }

  /** Creates a new fluent builder for SlimJre operations. */
  public static FluentBuilder builder() {
    return new FluentBuilder();
  }

  private String formatModules(Set<String> modules) {
    if (modules.isEmpty()) {
      return "(none)";
    }
    if (modules.size() <= 5) {
      return modules.stream().sorted().collect(Collectors.joining(", "));
    }
    return modules.stream().sorted().limit(5).collect(Collectors.joining(", "))
        + ", ... ("
        + modules.size()
        + " total)";
  }

  /** Fluent builder for SlimJre operations. */
  public static class FluentBuilder {
    private final SlimJreConfig.Builder configBuilder = SlimJreConfig.builder();
    private SlimJre slimJre;

    /** Adds a JAR file to analyze. */
    public FluentBuilder jar(Path jar) {
      configBuilder.jar(jar);
      return this;
    }

    /** Adds multiple JAR files to analyze. */
    public FluentBuilder jars(List<Path> jars) {
      configBuilder.jars(jars);
      return this;
    }

    /**
     * Adds a directory containing JARs to analyze. All JAR files in the directory will be included.
     */
    public FluentBuilder classpath(Path directory) {
      if (java.nio.file.Files.isDirectory(directory)) {
        try (var stream = java.nio.file.Files.list(directory)) {
          stream.filter(p -> p.toString().endsWith(".jar")).forEach(configBuilder::jar);
        } catch (java.io.IOException e) {
          throw new SlimJreException("Failed to read classpath directory: " + directory, e);
        }
      } else {
        configBuilder.jar(directory);
      }
      return this;
    }

    /** Sets the output path for the slim JRE. */
    public FluentBuilder outputPath(Path outputPath) {
      configBuilder.outputPath(outputPath);
      return this;
    }

    /** Adds a module to force-include. */
    public FluentBuilder addModule(String module) {
      configBuilder.addModule(module);
      return this;
    }

    /** Adds multiple modules to force-include. */
    public FluentBuilder additionalModules(Set<String> modules) {
      configBuilder.additionalModules(modules);
      return this;
    }

    /** Adds a module to exclude. */
    public FluentBuilder excludeModule(String module) {
      configBuilder.excludeModule(module);
      return this;
    }

    /** Sets whether to strip debug information. */
    public FluentBuilder stripDebug(boolean stripDebug) {
      configBuilder.stripDebug(stripDebug);
      return this;
    }

    /** Sets the compression level (zip-0 to zip-9). */
    public FluentBuilder compression(String compression) {
      configBuilder.compression(compression);
      return this;
    }

    /** Sets whether to scan for service loader dependencies. */
    public FluentBuilder scanServiceLoaders(boolean scan) {
      configBuilder.scanServiceLoaders(scan);
      return this;
    }

    /** Sets verbose output mode. */
    public FluentBuilder verbose(boolean verbose) {
      configBuilder.verbose(verbose);
      return this;
    }

    /** Builds the SlimJre instance. */
    public SlimJre build() {
      this.slimJre = new SlimJre();
      return slimJre;
    }

    /** Creates the minimal JRE. This is a shortcut for {@code build().createMinimalJre(...)}. */
    public Result create() {
      if (slimJre == null) {
        slimJre = new SlimJre();
      }
      return slimJre.createMinimalJre(configBuilder.build());
    }

    /**
     * Analyzes the JARs without creating a JRE. This is a shortcut for {@code
     * build().analyzeOnly(...)}.
     */
    public AnalysisResult analyze() {
      if (slimJre == null) {
        slimJre = new SlimJre();
      }
      SlimJreConfig config = configBuilder.build();
      return slimJre.analyzeOnly(config.jars());
    }
  }
}
