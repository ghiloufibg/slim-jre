package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.exception.JDepsException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes JAR files using jdeps to determine required JDK modules. Uses the ToolProvider API for
 * in-process execution.
 *
 * <p>Handles both modular and non-modular JARs:
 *
 * <ul>
 *   <li>Non-modular JARs: Analyzed via jdeps bytecode analysis
 *   <li>Modular JARs: Dependencies extracted from module-info.class using {@link ModuleDescriptor}
 * </ul>
 *
 * <p>Note: Modules that are not available in the current JDK (e.g., java.xml.ws removed in JDK 11)
 * are automatically filtered out.
 */
public class JDepsAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(JDepsAnalyzer.class);

  /** JDK module name prefix - used to filter only JDK modules from requires directives. */
  private static final Set<String> JDK_MODULE_PREFIXES =
      Set.of("java.", "jdk.", "javafx.", "oracle.");

  private final ToolProvider jdeps;
  private final int javaVersion;
  private final Set<String> availableJdkModules;

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
    this.availableJdkModules = discoverAvailableModules();
    log.debug("JDK {} has {} available modules", javaVersion, availableJdkModules.size());
  }

  /**
   * Discovers all available JDK modules in the current runtime.
   *
   * @return set of available module names
   */
  private Set<String> discoverAvailableModules() {
    return java.lang.module.ModuleFinder.ofSystem().findAll().stream()
        .map(ref -> ref.descriptor().name())
        .collect(Collectors.toCollection(TreeSet::new));
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
   * <p>Uses a hybrid approach:
   *
   * <ul>
   *   <li>Non-modular JARs: Analyzed via jdeps bytecode analysis
   *   <li>Modular JARs: JDK dependencies extracted from module-info.class requires directives
   * </ul>
   *
   * <p>This ensures we don't miss JDK module dependencies from modular JARs like Jackson, SLF4J,
   * etc.
   *
   * @param jars list of JAR files to analyze
   * @return set of required JDK module names
   * @throws JDepsException if analysis fails
   */
  public Set<String> analyzeRequiredModules(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      throw new JDepsException("At least one JAR file must be specified");
    }

    Set<String> allModules = new TreeSet<>();

    // Separate modular and non-modular JARs
    List<Path> nonModularJars = new ArrayList<>();
    List<Path> modularJars = new ArrayList<>();
    for (Path jar : jars) {
      if (isModularJar(jar)) {
        modularJars.add(jar);
      } else {
        nonModularJars.add(jar);
      }
    }

    log.debug(
        "Analyzing {} JAR(s): {} non-modular (jdeps), {} modular (module-info parsing)",
        jars.size(),
        nonModularJars.size(),
        modularJars.size());

    // Step 1: Analyze non-modular JARs with jdeps
    if (!nonModularJars.isEmpty()) {
      Set<String> jdepsModules = analyzeWithJdeps(nonModularJars, jars);
      allModules.addAll(jdepsModules);
      log.debug("jdeps detected {} modules from non-modular JARs", jdepsModules.size());
    }

    // Step 2: Extract JDK module dependencies from modular JARs via module-info.class
    if (!modularJars.isEmpty()) {
      Set<String> modularDeps = extractModularJarDependencies(modularJars);
      int newModules = 0;
      for (String mod : modularDeps) {
        if (allModules.add(mod)) {
          newModules++;
        }
      }
      log.debug(
          "module-info parsing added {} new modules from {} modular JARs",
          newModules,
          modularJars.size());
    }

    // Ensure java.base is always present
    allModules.add("java.base");

    log.debug("Total detected modules: {}", allModules);
    return allModules;
  }

  /**
   * Analyzes non-modular JARs using jdeps.
   *
   * @param targetJars non-modular JARs to analyze
   * @param allJars all JARs for classpath resolution
   * @return set of required JDK module names
   */
  private Set<String> analyzeWithJdeps(List<Path> targetJars, List<Path> allJars) {
    List<String> args = buildArguments(targetJars, allJars);
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

    return parseModuleOutput(output);
  }

  /**
   * Extracts JDK module dependencies from modular JARs by parsing their module-info.class files.
   *
   * <p>This addresses the limitation that jdeps cannot analyze modular JARs without all their
   * module dependencies present. Instead, we directly read the module descriptor and extract only
   * JDK module requires.
   *
   * <p>Note: Modules that are not available in the current JDK (e.g., java.xml.ws removed in JDK
   * 11) are automatically filtered out with a warning.
   *
   * @param modularJars list of modular JARs to analyze
   * @return set of JDK module names required by these JARs
   */
  private Set<String> extractModularJarDependencies(List<Path> modularJars) {
    Set<String> jdkModules = new TreeSet<>();

    for (Path jar : modularJars) {
      try {
        Set<String> deps = readModuleDescriptorRequires(jar);
        for (String dep : deps) {
          if (isJdkModule(dep)) {
            if (availableJdkModules.contains(dep)) {
              jdkModules.add(dep);
              log.trace("{} requires JDK module: {}", jar.getFileName(), dep);
            } else {
              log.debug(
                  "{} requires unavailable JDK module '{}' (removed in newer JDK), skipping",
                  jar.getFileName(),
                  dep);
            }
          }
        }
      } catch (IOException e) {
        log.warn("Failed to read module-info from {}: {}", jar.getFileName(), e.getMessage());
      }
    }

    return jdkModules;
  }

  /**
   * Reads the module descriptor from a JAR and returns all required module names.
   *
   * <p>Handles both regular modular JARs and multi-release JARs.
   *
   * @param jar the modular JAR file
   * @return set of module names from requires directives
   * @throws IOException if reading fails
   */
  private Set<String> readModuleDescriptorRequires(Path jar) throws IOException {
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      // Try root level first
      JarEntry entry = jarFile.getJarEntry("module-info.class");

      // If not at root, find in versioned directory (prefer highest version)
      if (entry == null) {
        entry = findVersionedModuleInfo(jarFile);
      }

      if (entry == null) {
        return Set.of();
      }

      try (InputStream is = jarFile.getInputStream(entry)) {
        ModuleDescriptor descriptor = ModuleDescriptor.read(is);
        return descriptor.requires().stream()
            .map(ModuleDescriptor.Requires::name)
            .collect(Collectors.toSet());
      }
    }
  }

  /**
   * Finds the module-info.class entry in a multi-release JAR, preferring the highest version.
   *
   * @param jarFile the JAR file to search
   * @return the module-info entry or null if not found
   */
  private JarEntry findVersionedModuleInfo(JarFile jarFile) {
    return jarFile.stream()
        .filter(
            e ->
                e.getName().startsWith("META-INF/versions/")
                    && e.getName().endsWith("/module-info.class"))
        .max(Comparator.comparingInt(this::extractVersion))
        .orElse(null);
  }

  /**
   * Extracts the version number from a versioned entry path.
   *
   * @param entry the JAR entry
   * @return the version number, or 0 if not parseable
   */
  private int extractVersion(JarEntry entry) {
    // Format: META-INF/versions/N/module-info.class
    String name = entry.getName();
    int start = "META-INF/versions/".length();
    int end = name.indexOf('/', start);
    if (end > start) {
      try {
        return Integer.parseInt(name.substring(start, end));
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  /**
   * Checks if a module name is a JDK module (java.*, jdk.*, javafx.*, oracle.*).
   *
   * @param moduleName the module name to check
   * @return true if this is a JDK module
   */
  private boolean isJdkModule(String moduleName) {
    for (String prefix : JDK_MODULE_PREFIXES) {
      if (moduleName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
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
   * Analyzes each JAR individually and returns a map of JAR to its required JDK modules.
   *
   * <p>Non-modular JARs are analyzed with jdeps. Modular JARs have their JDK dependencies extracted
   * from module-info.class.
   *
   * @param jars list of JAR files to analyze
   * @return map of JAR path to its required JDK modules
   */
  public Map<Path, Set<String>> analyzeRequiredModulesPerJar(List<Path> jars) {
    Map<Path, Set<String>> result = new LinkedHashMap<>();

    for (Path jar : jars) {
      if (isModularJar(jar)) {
        // Modular JAR: extract JDK modules from module-info.class
        try {
          Set<String> allDeps = readModuleDescriptorRequires(jar);
          Set<String> jdkDeps =
              allDeps.stream()
                  .filter(this::isJdkModule)
                  .filter(availableJdkModules::contains) // Filter unavailable modules
                  .collect(Collectors.toCollection(TreeSet::new));
          result.put(jar, jdkDeps);
        } catch (IOException e) {
          log.warn("Failed to read module-info from {}: {}", jar.getFileName(), e.getMessage());
          result.put(jar, Set.of());
        }
      } else {
        // Non-modular JAR: analyze with jdeps
        try {
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
