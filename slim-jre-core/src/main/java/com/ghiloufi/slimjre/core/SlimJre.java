package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.JLinkOptions;
import com.ghiloufi.slimjre.config.Result;
import com.ghiloufi.slimjre.config.SlimJreConfig;
import com.ghiloufi.slimjre.exception.SlimJreException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  private final ReflectionBytecodeScanner reflectionScanner;
  private final ApiUsageScanner apiUsageScanner;
  private final GraalVmMetadataScanner graalVmMetadataScanner;
  private final ModuleResolver moduleResolver;
  private final JLinkExecutor jlinkExecutor;

  /** Creates a new SlimJre instance with default components. */
  public SlimJre() {
    this.jdepsAnalyzer = new JDepsAnalyzer();
    this.serviceLoaderScanner = new ServiceLoaderScanner();
    this.reflectionScanner = new ReflectionBytecodeScanner();
    this.apiUsageScanner = new ApiUsageScanner();
    this.graalVmMetadataScanner = new GraalVmMetadataScanner();
    this.moduleResolver = new ModuleResolver();
    this.jlinkExecutor = new JLinkExecutor();
  }

  /** Creates a new SlimJre instance with custom components. */
  public SlimJre(
      JDepsAnalyzer jdepsAnalyzer,
      ServiceLoaderScanner serviceLoaderScanner,
      ReflectionBytecodeScanner reflectionScanner,
      ApiUsageScanner apiUsageScanner,
      GraalVmMetadataScanner graalVmMetadataScanner,
      ModuleResolver moduleResolver,
      JLinkExecutor jlinkExecutor) {
    this.jdepsAnalyzer = Objects.requireNonNull(jdepsAnalyzer);
    this.serviceLoaderScanner = Objects.requireNonNull(serviceLoaderScanner);
    this.reflectionScanner = Objects.requireNonNull(reflectionScanner);
    this.apiUsageScanner = Objects.requireNonNull(apiUsageScanner);
    this.graalVmMetadataScanner = Objects.requireNonNull(graalVmMetadataScanner);
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

    // Run all analyzers in parallel using virtual threads
    log.debug("Running all analyzers in parallel...");
    Set<String> jdepsModules;
    Set<String> serviceModules;
    Set<String> reflectionModules;
    Set<String> apiUsageModules;
    Set<String> graalVmModules;

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // Submit all analysis tasks in parallel
      Future<Set<String>> jdepsFuture =
          executor.submit(() -> jdepsAnalyzer.analyzeRequiredModules(config.jars()));

      Future<Set<String>> serviceFuture =
          config.scanServiceLoaders()
              ? executor.submit(
                  () -> serviceLoaderScanner.scanForServiceModulesParallel(config.jars()))
              : null;

      Future<Set<String>> reflectionFuture =
          executor.submit(() -> reflectionScanner.scanJarsParallel(config.jars()));

      Future<Set<String>> apiUsageFuture =
          executor.submit(() -> apiUsageScanner.scanJarsParallel(config.jars()));

      Future<Set<String>> graalVmFuture =
          config.scanGraalVmMetadata()
              ? executor.submit(() -> graalVmMetadataScanner.scanJarsParallel(config.jars()))
              : null;

      // Collect results
      try {
        jdepsModules = jdepsFuture.get();
        log.info(
            "jdeps detected {} module(s): {}", jdepsModules.size(), formatModules(jdepsModules));

        serviceModules = serviceFuture != null ? serviceFuture.get() : Set.of();
        if (!serviceModules.isEmpty()) {
          log.info(
              "Service loaders require {} module(s): {}",
              serviceModules.size(),
              formatModules(serviceModules));
        }

        reflectionModules = reflectionFuture.get();
        if (!reflectionModules.isEmpty()) {
          log.info(
              "Reflection patterns require {} module(s): {}",
              reflectionModules.size(),
              formatModules(reflectionModules));
        }

        apiUsageModules = apiUsageFuture.get();
        if (!apiUsageModules.isEmpty()) {
          log.info(
              "API usage patterns require {} module(s): {}",
              apiUsageModules.size(),
              formatModules(apiUsageModules));
        }

        graalVmModules = graalVmFuture != null ? graalVmFuture.get() : Set.of();
        if (!graalVmModules.isEmpty()) {
          log.info(
              "GraalVM metadata requires {} module(s): {}",
              graalVmModules.size(),
              formatModules(graalVmModules));
        }
      } catch (Exception e) {
        throw new SlimJreException("Parallel analysis failed: " + e.getMessage(), e);
      }
    }

    // Combine all modules
    Set<String> allModules = new TreeSet<>();
    allModules.addAll(jdepsModules);
    allModules.addAll(serviceModules);
    allModules.addAll(reflectionModules);
    allModules.addAll(apiUsageModules);
    allModules.addAll(graalVmModules);
    allModules.addAll(config.additionalModules());

    // Remove excluded modules
    allModules.removeAll(config.excludeModules());

    log.debug("Combined modules before resolution: {}", allModules);

    // Step 5: Resolve transitive dependencies
    log.debug("Step 5: Resolving transitive module dependencies...");
    Set<String> resolvedModules = moduleResolver.resolveWithTransitive(allModules);
    log.info("Resolved {} total module(s) (including transitive)", resolvedModules.size());

    // Step 6: Create the JRE with jlink
    log.debug("Step 6: Creating custom JRE with jlink...");
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
    return analyzeOnly(jars, true, true);
  }

  /**
   * Analyzes JARs and returns required modules without creating a JRE.
   *
   * @param jars JARs to analyze
   * @param scanServiceLoaders whether to scan for service loader dependencies
   * @param scanGraalVmMetadata whether to scan GraalVM native-image metadata
   * @return analysis result with module breakdown
   */
  public AnalysisResult analyzeOnly(
      List<Path> jars, boolean scanServiceLoaders, boolean scanGraalVmMetadata) {
    log.info("Analyzing {} JAR(s) in parallel...", jars.size());

    Set<String> jdepsModules;
    Set<String> serviceModules;
    Set<String> reflectionModules;
    Set<String> apiUsageModules;
    Set<String> graalVmModules;
    Map<Path, Set<String>> perJarModules;

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // Submit all analysis tasks in parallel
      Future<Set<String>> jdepsFuture =
          executor.submit(() -> jdepsAnalyzer.analyzeRequiredModules(jars));
      Future<Set<String>> serviceFuture =
          scanServiceLoaders
              ? executor.submit(() -> serviceLoaderScanner.scanForServiceModulesParallel(jars))
              : null;
      Future<Set<String>> reflectionFuture =
          executor.submit(() -> reflectionScanner.scanJarsParallel(jars));
      Future<Set<String>> apiUsageFuture =
          executor.submit(() -> apiUsageScanner.scanJarsParallel(jars));
      Future<Set<String>> graalVmFuture =
          scanGraalVmMetadata
              ? executor.submit(() -> graalVmMetadataScanner.scanJarsParallel(jars))
              : null;
      Future<Map<Path, Set<String>>> perJarFuture =
          executor.submit(() -> jdepsAnalyzer.analyzeRequiredModulesPerJar(jars));

      // Collect results
      try {
        jdepsModules = jdepsFuture.get();
        serviceModules = serviceFuture != null ? serviceFuture.get() : Set.of();
        reflectionModules = reflectionFuture.get();
        apiUsageModules = apiUsageFuture.get();
        graalVmModules = graalVmFuture != null ? graalVmFuture.get() : Set.of();
        perJarModules = perJarFuture.get();
      } catch (Exception e) {
        throw new SlimJreException("Parallel analysis failed: " + e.getMessage(), e);
      }
    }

    // Combine all modules
    Set<String> allModules = new TreeSet<>();
    allModules.addAll(jdepsModules);
    allModules.addAll(serviceModules);
    allModules.addAll(reflectionModules);
    allModules.addAll(apiUsageModules);
    allModules.addAll(graalVmModules);

    return new AnalysisResult(
        jdepsModules,
        serviceModules,
        reflectionModules,
        apiUsageModules,
        graalVmModules,
        allModules,
        perJarModules);
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
    private DiscoveryResult discoveryResult;

    /**
     * Discovers all JARs from a directory, fat JAR, or WAR file.
     *
     * <p>Supports:
     *
     * <ul>
     *   <li>Directories (recursive scan, e.g., Quarkus target/quarkus-app)
     *   <li>Fat JARs (Spring Boot BOOT-INF/lib extraction)
     *   <li>WAR files (WEB-INF/lib extraction)
     *   <li>JARs with MANIFEST Class-Path references
     * </ul>
     *
     * @param input path to directory or archive to discover JARs from
     * @return this builder for chaining
     * @throws SlimJreException if discovery fails
     */
    public FluentBuilder discover(Path input) {
      try {
        JarDiscovery jarDiscovery = new JarDiscovery();
        this.discoveryResult = jarDiscovery.discover(input);

        // Pass discovered JARs to the config builder
        configBuilder.jars(discoveryResult.jarList());

        // Log discovery results
        log.info("Discovered {} JAR(s) from: {}", discoveryResult.jarCount(), input);
        if (discoveryResult.hasWarnings()) {
          discoveryResult.warnings().forEach(w -> log.warn("Discovery warning: {}", w));
        }

        return this;
      } catch (IOException e) {
        throw new SlimJreException("JAR discovery failed: " + e.getMessage(), e);
      }
    }

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

    /** Sets whether to scan GraalVM native-image metadata for additional modules. */
    public FluentBuilder scanGraalVmMetadata(boolean scan) {
      configBuilder.scanGraalVmMetadata(scan);
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

    /**
     * Creates the minimal JRE. This is a shortcut for {@code build().createMinimalJre(...)}.
     *
     * <p>If {@link #discover(Path)} was used, the temporary directory containing extracted nested
     * JARs will be cleaned up after JRE creation.
     */
    public Result create() {
      try {
        if (slimJre == null) {
          slimJre = new SlimJre();
        }
        return slimJre.createMinimalJre(configBuilder.build());
      } finally {
        cleanupDiscovery();
      }
    }

    /**
     * Analyzes the JARs without creating a JRE. This is a shortcut for {@code
     * build().analyzeOnly(...)}.
     *
     * <p>If {@link #discover(Path)} was used, the temporary directory containing extracted nested
     * JARs will be cleaned up after analysis.
     */
    public AnalysisResult analyze() {
      try {
        if (slimJre == null) {
          slimJre = new SlimJre();
        }
        SlimJreConfig config = configBuilder.build();
        return slimJre.analyzeOnly(
            config.jars(), config.scanServiceLoaders(), config.scanGraalVmMetadata());
      } finally {
        cleanupDiscovery();
      }
    }

    /** Cleans up any temporary files created during JAR discovery. */
    private void cleanupDiscovery() {
      if (discoveryResult != null) {
        discoveryResult.close();
        discoveryResult = null;
      }
    }
  }
}
