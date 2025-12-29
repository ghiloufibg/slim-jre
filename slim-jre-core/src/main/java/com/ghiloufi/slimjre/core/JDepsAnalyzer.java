package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.exception.JDepsException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes JAR files using jdeps to determine required JDK modules. Uses the ToolProvider API for
 * in-process execution.
 */
public class JDepsAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(JDepsAnalyzer.class);

  private final ToolProvider jdeps;
  private final int javaVersion;

  /**
   * Creates a new JDepsAnalyzer.
   *
   * @throws JDepsException if jdeps is not available
   */
  public JDepsAnalyzer() {
    this.jdeps =
        ToolProvider.findFirst("jdeps")
            .orElseThrow(
                () -> new JDepsException("jdeps tool not found. Ensure you are running on JDK 9+"));
    this.javaVersion = Runtime.version().feature();
  }

  /**
   * Analyzes a JAR and its classpath to determine required JDK modules.
   *
   * @param jarPath the main JAR to analyze
   * @param classpath additional JARs on the classpath
   * @return set of required JDK module names
   * @throws JDepsException if analysis fails
   */
  public Set<String> analyzeRequiredModules(Path jarPath, List<Path> classpath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    List<Path> allJars = new ArrayList<>();
    allJars.add(jarPath);
    if (classpath != null) {
      allJars.addAll(classpath);
    }

    return analyzeRequiredModules(allJars);
  }

  /**
   * Analyzes multiple JARs and merges their required modules. Modular JARs (those containing
   * module-info.class) are filtered out since jdeps requires all module dependencies to be present
   * on the module-path, which is impractical for mixed modular/non-modular classpaths.
   *
   * @param jars list of JAR files to analyze
   * @return set of required JDK module names
   * @throws JDepsException if analysis fails
   */
  public Set<String> analyzeRequiredModules(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      throw new JDepsException("At least one JAR file must be specified");
    }

    // Filter out modular JARs - jdeps fails on modular JARs when module dependencies
    // aren't all present on the module-path
    List<Path> nonModularJars = filterNonModularJars(jars);
    if (nonModularJars.isEmpty()) {
      log.warn("All {} JAR(s) are modular, defaulting to java.base", jars.size());
      return Set.of("java.base");
    }

    log.debug(
        "Analyzing {} non-modular JAR(s) for module dependencies (filtered {} modular JARs)",
        nonModularJars.size(),
        jars.size() - nonModularJars.size());

    List<String> args = buildArguments(nonModularJars, jars);
    log.trace("jdeps arguments: {}", args);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    int exitCode =
        jdeps.run(new PrintStream(out), new PrintStream(err), args.toArray(new String[0]));

    String output = out.toString().trim();
    String error = err.toString().trim();

    if (!error.isEmpty()) {
      // jdeps may output warnings to stderr that aren't fatal
      log.warn("jdeps warnings: {}", error);
    }

    if (exitCode != 0) {
      throw new JDepsException(
          "jdeps failed with exit code " + exitCode + (error.isEmpty() ? "" : ": " + error));
    }

    Set<String> modules = parseModuleOutput(output);
    log.debug("Detected modules: {}", modules);

    return modules;
  }

  /**
   * Filters out modular JARs (those containing module-info.class).
   *
   * @param jars list of JAR files
   * @return list of non-modular JARs
   */
  private List<Path> filterNonModularJars(List<Path> jars) {
    List<Path> nonModular = new ArrayList<>();
    for (Path jar : jars) {
      if (!isModularJar(jar)) {
        nonModular.add(jar);
      } else {
        log.trace("Skipping modular JAR: {}", jar.getFileName());
      }
    }
    return nonModular;
  }

  /**
   * Checks if a JAR is modular (contains module-info.class).
   *
   * <p>Handles multi-release JARs by checking both root level and versioned directories.
   *
   * @param jar the JAR file to check
   * @return true if the JAR contains module-info.class at root or in any versioned directory
   */
  private boolean isModularJar(Path jar) {
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      // Check root level
      if (jarFile.getEntry("module-info.class") != null) {
        return true;
      }
      // Check multi-release versioned directories (9+)
      // jdeps uses --multi-release so we must detect these as modular too
      return jarFile.stream()
          .anyMatch(
              e ->
                  e.getName().startsWith("META-INF/versions/")
                      && e.getName().endsWith("/module-info.class"));
    } catch (IOException e) {
      log.warn("Failed to check if {} is modular: {}", jar.getFileName(), e.getMessage());
      return false; // Assume non-modular if we can't check
    }
  }

  /**
   * Analyzes each non-modular JAR individually and returns a map of JAR to its required modules.
   * Modular JARs are skipped and not included in the result.
   *
   * @param jars list of JAR files to analyze
   * @return map of JAR path to its required modules (modular JARs excluded)
   */
  public Map<Path, Set<String>> analyzeRequiredModulesPerJar(List<Path> jars) {
    Map<Path, Set<String>> result = new LinkedHashMap<>();
    List<Path> nonModularJars = filterNonModularJars(jars);

    for (Path jar : nonModularJars) {
      try {
        // Pass single JAR as target, but include all JARs on classpath for resolution
        List<String> args = buildArguments(List.of(jar), jars);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode =
            jdeps.run(new PrintStream(out), new PrintStream(err), args.toArray(new String[0]));

        if (exitCode == 0) {
          Set<String> modules = parseModuleOutput(out.toString().trim());
          result.put(jar, modules);
        } else {
          log.warn("Failed to analyze {}: exit code {}", jar.getFileName(), exitCode);
          result.put(jar, Set.of());
        }
      } catch (Exception e) {
        log.warn("Failed to analyze {}: {}", jar.getFileName(), e.getMessage());
        result.put(jar, Set.of());
      }
    }

    return result;
  }

  /**
   * Builds the jdeps command-line arguments.
   *
   * <p>IMPORTANT: Only TARGET JARs need to be non-modular. Modular JARs on the classpath are fine -
   * jdeps treats them as regular class files for resolution purposes. The module graph resolution
   * only triggers when analyzing a modular JAR as a target.
   *
   * @param targetJars JARs to analyze (must be non-modular only)
   * @param classpathJars all JARs to include on the classpath (modular JARs are OK here)
   */
  private List<String> buildArguments(List<Path> targetJars, List<Path> classpathJars) {
    List<String> args = new ArrayList<>();

    // Ignore missing dependencies (common for incomplete classpaths)
    args.add("--ignore-missing-deps");

    // Print only the comma-separated list of module names
    args.add("--print-module-deps");

    // Handle multi-release JARs
    args.add("--multi-release");
    args.add(String.valueOf(javaVersion));

    // Include ALL JARs on classpath - modular JARs are fine here for class resolution
    // jdeps only fails when modular JARs are analysis TARGETS, not on classpath
    if (!classpathJars.isEmpty()) {
      String classpathStr =
          classpathJars.stream()
              .map(Path::toAbsolutePath)
              .map(Path::toString)
              .collect(Collectors.joining(System.getProperty("path.separator")));
      args.add("-classpath");
      args.add(classpathStr);
    }

    // Add only non-modular JARs as targets to analyze
    for (Path jar : targetJars) {
      args.add(jar.toAbsolutePath().toString());
    }

    return args;
  }

  /**
   * Parses the module output from jdeps. jdeps --print-module-deps outputs a comma-separated list
   * of modules.
   */
  private Set<String> parseModuleOutput(String output) {
    if (output == null || output.isBlank()) {
      log.warn("jdeps produced no output, defaulting to java.base");
      return Set.of("java.base");
    }

    return Arrays.stream(output.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** Returns the Java version being used for analysis. */
  public int getJavaVersion() {
    return javaVersion;
  }
}
