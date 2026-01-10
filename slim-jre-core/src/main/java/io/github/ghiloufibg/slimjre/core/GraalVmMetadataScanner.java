package io.github.ghiloufibg.slimjre.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans JARs for GraalVM native-image metadata to detect additional module requirements.
 *
 * <p>Scans embedded metadata in JAR's META-INF/native-image/ directory to detect JDK class
 * references that indicate module requirements beyond what jdeps detects.
 *
 * <p>Example detections:
 *
 * <pre>
 *   reflect-config.json: {"name": "java.sql.Driver"} → java.sql module
 *   jni-config.json: {"name": "javax.naming.Context"} → java.naming module
 * </pre>
 */
public class GraalVmMetadataScanner {

  private static final Logger log = LoggerFactory.getLogger(GraalVmMetadataScanner.class);

  private static final String EMBEDDED_METADATA_PREFIX = "META-INF/native-image/";
  private static final String REFLECT_CONFIG = "reflect-config.json";
  private static final String RESOURCE_CONFIG = "resource-config.json";
  private static final String JNI_CONFIG = "jni-config.json";

  /** Pattern for extracting class names from reflect-config.json and jni-config.json. */
  private static final Pattern CLASS_NAME_PATTERN =
      Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

  /** Pattern for extracting resource patterns from resource-config.json. */
  private static final Pattern RESOURCE_PATTERN_PATTERN =
      Pattern.compile("\"pattern\"\\s*:\\s*\"([^\"]+)\"");

  private final Map<String, String> classToModuleCache;

  /** Creates a scanner with the default configuration. */
  public GraalVmMetadataScanner() {
    this.classToModuleCache = buildClassToModuleCache();
    log.debug("Initialized GraalVmMetadataScanner for embedded metadata scanning");
  }

  /**
   * Scans multiple JARs in parallel for GraalVM metadata.
   *
   * @param jars list of JAR paths to scan
   * @return set of JDK module names required by GraalVM metadata
   */
  public Set<String> scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return Set.of();
    }

    Set<String> allModules = ConcurrentHashMap.newKeySet();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Set<String>>> futures =
          jars.stream().map(jar -> executor.submit(() -> scanJar(jar))).toList();

      for (Future<Set<String>> future : futures) {
        try {
          allModules.addAll(future.get());
        } catch (Exception e) {
          log.warn("Failed to get scan result: {}", e.getMessage());
        }
      }
    }

    return new TreeSet<>(allModules);
  }

  /**
   * Scans a single JAR for GraalVM metadata.
   *
   * @param jarPath path to JAR file
   * @return set of JDK module names required by GraalVM metadata
   */
  public Set<String> scanJar(Path jarPath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    if (!Files.exists(jarPath)) {
      log.warn("JAR file does not exist: {}", jarPath);
      return Set.of();
    }

    // Scan embedded metadata
    Set<String> modules = scanEmbeddedMetadata(jarPath);
    if (!modules.isEmpty()) {
      log.debug("Found embedded metadata in {}: {} module(s)", jarPath.getFileName(), modules);
    }

    return modules;
  }

  /**
   * Extracts Maven coordinates from JAR's pom.properties.
   *
   * @param jarPath path to the JAR file
   * @return optional containing coordinates if found
   */
  Optional<MavenCoordinates> extractMavenCoordinates(Path jarPath) {
    try (JarFile jar = new JarFile(jarPath.toFile())) {
      return jar.stream()
          .filter(e -> e.getName().endsWith("pom.properties"))
          .filter(e -> e.getName().startsWith("META-INF/maven/"))
          .findFirst()
          .flatMap(entry -> parsePomProperties(jar, entry));
    } catch (IOException e) {
      log.trace("Failed to read JAR {}: {}", jarPath, e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<MavenCoordinates> parsePomProperties(JarFile jar, JarEntry entry) {
    try (InputStream is = jar.getInputStream(entry)) {
      Properties props = new Properties();
      props.load(is);

      String groupId = props.getProperty("groupId");
      String artifactId = props.getProperty("artifactId");
      String version = props.getProperty("version");

      if (groupId != null && artifactId != null && version != null) {
        return Optional.of(new MavenCoordinates(groupId, artifactId, version));
      }
    } catch (IOException e) {
      log.trace("Failed to parse pom.properties: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Scans embedded META-INF/native-image/ metadata.
   *
   * @param jarPath path to the JAR file
   * @return set of detected module names
   */
  Set<String> scanEmbeddedMetadata(Path jarPath) {
    Set<String> modules = new HashSet<>();

    try (JarFile jar = new JarFile(jarPath.toFile())) {
      jar.stream()
          .filter(e -> e.getName().startsWith(EMBEDDED_METADATA_PREFIX))
          .filter(e -> e.getName().endsWith(".json"))
          .filter(e -> isRelevantConfigFile(e.getName()))
          .forEach(
              entry -> {
                try (InputStream is = jar.getInputStream(entry)) {
                  modules.addAll(parseMetadataConfig(is, entry.getName()));
                } catch (IOException e) {
                  log.trace("Failed to parse {}: {}", entry.getName(), e.getMessage());
                }
              });
    } catch (IOException e) {
      log.trace("Failed to scan embedded metadata in {}: {}", jarPath, e.getMessage());
    }

    return modules;
  }

  private boolean isRelevantConfigFile(String name) {
    return name.endsWith(REFLECT_CONFIG)
        || name.endsWith(RESOURCE_CONFIG)
        || name.endsWith(JNI_CONFIG);
  }

  /**
   * Parses all config files in a directory.
   *
   * @param configDir path to the configuration directory
   * @return set of detected module names
   */
  Set<String> parseConfigDirectory(Path configDir) {
    Set<String> modules = new HashSet<>();

    for (String configFile : List.of(REFLECT_CONFIG, RESOURCE_CONFIG, JNI_CONFIG)) {
      Path configPath = configDir.resolve(configFile);
      if (Files.exists(configPath)) {
        try {
          String content = Files.readString(configPath);
          modules.addAll(parseMetadataContent(content, configFile));
        } catch (IOException e) {
          log.trace("Failed to read {}: {}", configPath, e.getMessage());
        }
      }
    }

    return modules;
  }

  /**
   * Parses metadata config and extracts referenced JDK classes → modules.
   *
   * @param is input stream of the config file
   * @param fileName name of the config file (for determining parse strategy)
   * @return set of detected module names
   */
  Set<String> parseMetadataConfig(InputStream is, String fileName) throws IOException {
    String content = new String(is.readAllBytes());
    return parseMetadataContent(content, fileName);
  }

  /**
   * Parses metadata content and extracts module references.
   *
   * @param content the JSON content
   * @param fileName name of the config file
   * @return set of detected module names
   */
  Set<String> parseMetadataContent(String content, String fileName) {
    if (fileName.contains("reflect") || fileName.contains("jni")) {
      return parseReflectConfig(content);
    } else if (fileName.contains("resource")) {
      return parseResourceConfig(content);
    }
    return Set.of();
  }

  /**
   * Parses reflect-config.json or jni-config.json for class names. Format: [{"name":
   * "java.sql.Driver", ...}, ...]
   *
   * @param json the JSON content
   * @return set of detected module names
   */
  Set<String> parseReflectConfig(String json) {
    Set<String> modules = new HashSet<>();

    Matcher matcher = CLASS_NAME_PATTERN.matcher(json);

    while (matcher.find()) {
      String className = matcher.group(1);
      String module = classToModuleCache.get(className);
      if (module != null && !module.equals("java.base")) {
        modules.add(module);
      }
    }

    return modules;
  }

  /**
   * Parses resource-config.json for class resource patterns.
   *
   * @param json the JSON content
   * @return set of detected module names
   */
  Set<String> parseResourceConfig(String json) {
    Set<String> modules = new HashSet<>();

    Matcher matcher = RESOURCE_PATTERN_PATTERN.matcher(json);

    while (matcher.find()) {
      String resourcePattern = matcher.group(1);
      if (resourcePattern.endsWith(".class")) {
        String className = resourcePattern.replace("/", ".").replace(".class", "");
        String module = classToModuleCache.get(className);
        if (module != null && !module.equals("java.base")) {
          modules.add(module);
        }
      }
    }

    return modules;
  }

  /**
   * Builds a cache mapping JDK class names to their containing modules using the ModuleFinder API.
   */
  private Map<String, String> buildClassToModuleCache() {
    Map<String, String> cache = new HashMap<>();
    java.lang.module.ModuleFinder finder = java.lang.module.ModuleFinder.ofSystem();

    for (var ref : finder.findAll()) {
      String moduleName = ref.descriptor().name();
      try {
        ref.open()
            .list()
            .filter(name -> name.endsWith(".class"))
            .forEach(
                resource -> {
                  String className = resource.replace('/', '.').substring(0, resource.length() - 6);
                  cache.put(className, moduleName);
                });
      } catch (IOException e) {
        log.trace("Failed to read module {}: {}", moduleName, e.getMessage());
      }
    }

    log.debug("Built class-to-module cache with {} entries", cache.size());
    return cache;
  }

  /**
   * Returns the module name for a given JDK class.
   *
   * @param className fully qualified class name
   * @return Optional containing the module name, or empty if not found
   */
  public Optional<String> getModuleForClass(String className) {
    return Optional.ofNullable(classToModuleCache.get(className));
  }
}
