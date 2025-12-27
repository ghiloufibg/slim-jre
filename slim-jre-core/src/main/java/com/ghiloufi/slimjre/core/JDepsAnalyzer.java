package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.exception.JDepsException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
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
   * Analyzes multiple JARs and merges their required modules.
   *
   * @param jars list of JAR files to analyze
   * @return set of required JDK module names
   * @throws JDepsException if analysis fails
   */
  public Set<String> analyzeRequiredModules(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      throw new JDepsException("At least one JAR file must be specified");
    }

    log.debug("Analyzing {} JAR(s) for module dependencies", jars.size());

    List<String> args = buildArguments(jars);
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
   * Analyzes each JAR individually and returns a map of JAR to its required modules.
   *
   * @param jars list of JAR files to analyze
   * @return map of JAR path to its required modules
   */
  public Map<Path, Set<String>> analyzeRequiredModulesPerJar(List<Path> jars) {
    Map<Path, Set<String>> result = new LinkedHashMap<>();

    for (Path jar : jars) {
      try {
        Set<String> modules = analyzeRequiredModules(List.of(jar));
        result.put(jar, modules);
      } catch (JDepsException e) {
        log.warn("Failed to analyze {}: {}", jar.getFileName(), e.getMessage());
        result.put(jar, Set.of());
      }
    }

    return result;
  }

  /** Builds the jdeps command-line arguments. */
  private List<String> buildArguments(List<Path> jars) {
    List<String> args = new ArrayList<>();

    // Ignore missing dependencies (common for incomplete classpaths)
    args.add("--ignore-missing-deps");

    // Print only the comma-separated list of module names
    args.add("--print-module-deps");

    // Handle multi-release JARs
    args.add("--multi-release");
    args.add(String.valueOf(javaVersion));

    // Build classpath from all JARs
    if (jars.size() > 1) {
      args.add("-classpath");
      args.add(
          jars.stream()
              .map(Path::toAbsolutePath)
              .map(Path::toString)
              .collect(Collectors.joining(System.getProperty("path.separator"))));
    }

    // Add main JAR(s) to analyze
    for (Path jar : jars) {
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
