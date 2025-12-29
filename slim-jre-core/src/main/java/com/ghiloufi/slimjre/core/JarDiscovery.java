package com.ghiloufi.slimjre.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-agnostic JAR discovery for any Java application.
 *
 * <p>Discovers all JAR files from:
 *
 * <ul>
 *   <li>Directories (recursive scan with symlink protection)
 *   <li>Fat JARs (Spring Boot BOOT-INF/lib, etc.)
 *   <li>WAR files (WEB-INF/lib extraction)
 *   <li>MANIFEST Class-Path references
 * </ul>
 *
 * <p>Uses Java 21 GA features for maximum performance:
 *
 * <ul>
 *   <li>{@code parallelStream()} for I/O-bound extraction operations
 *   <li>{@code ConcurrentHashMap.newKeySet()} for thread-safe collections
 * </ul>
 *
 * <p>Supported frameworks: Spring Boot, Quarkus, Micronaut, Helidon, plain Maven/Gradle, WAR
 * deployments.
 */
public class JarDiscovery {

  private static final Logger log = LoggerFactory.getLogger(JarDiscovery.class);

  /** Common library paths in various archive formats. */
  private static final List<String> LIBRARY_PREFIXES =
      List.of(
          "BOOT-INF/lib/", // Spring Boot
          "WEB-INF/lib/", // WAR
          "lib/" // Standard
          );

  /**
   * Discovers all JARs from any input - directory, JAR, WAR, or fat JAR.
   *
   * <p>Framework-agnostic: works with Quarkus, Spring Boot, MicroProfile, etc.
   *
   * @param input Path to directory or archive file
   * @return DiscoveryResult containing all discovered JARs
   * @throws IOException if I/O error occurs during discovery
   */
  public DiscoveryResult discover(Path input) throws IOException {
    Objects.requireNonNull(input, "input must not be null");

    if (!Files.exists(input)) {
      throw new IOException("Input path does not exist: " + input);
    }

    log.info("Discovering JARs from: {}", input);

    Set<Path> allJars = ConcurrentHashMap.newKeySet(); // Thread-safe for parallel ops
    Path tempDir = null;
    boolean hasNested = false;
    List<String> warnings = Collections.synchronizedList(new ArrayList<>());

    if (Files.isDirectory(input)) {
      // Strategy 1: Recursive directory scan (with symlink protection)
      allJars.addAll(findJarsInDirectory(input, warnings));
    } else if (isArchive(input)) {
      // Strategy 2: Archive extraction with parallel I/O
      tempDir = Files.createTempDirectory("slim-jre-extract-");
      hasNested = true;
      allJars.addAll(discoverFromArchive(input, tempDir, warnings));
    } else {
      throw new IOException("Input must be directory or archive: " + input);
    }

    // Sort for deterministic order
    Set<Path> sortedJars = new LinkedHashSet<>(allJars.stream().sorted().toList());

    log.info("Discovered {} JAR(s)", sortedJars.size());
    if (!warnings.isEmpty()) {
      log.warn("Discovery completed with {} warning(s)", warnings.size());
    }

    return new DiscoveryResult(sortedJars, tempDir, hasNested, warnings);
  }

  /**
   * Recursively finds all JARs in a directory with symlink loop protection.
   *
   * @param dir Directory to scan
   * @param warnings List to collect warnings
   * @return Set of discovered JAR paths
   */
  private Set<Path> findJarsInDirectory(Path dir, List<String> warnings) {
    Set<Path> visited = ConcurrentHashMap.newKeySet(); // Thread-safe for parallel

    try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
      return walk.filter(
              p -> {
                // Symlink loop protection
                if (Files.isDirectory(p)) {
                  try {
                    Path real = p.toRealPath();
                    if (!visited.add(real)) {
                      warnings.add("Skipped symlink loop: " + p);
                      return false;
                    }
                  } catch (IOException e) {
                    warnings.add("Cannot resolve path: " + p + " - " + e.getMessage());
                    return false;
                  }
                }
                return true;
              })
          .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
          .filter(Files::isRegularFile)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (IOException e) {
      warnings.add("Error scanning directory: " + dir + " - " + e.getMessage());
      log.error("Failed to scan directory: {}", dir, e);
      return Collections.emptySet();
    }
  }

  /**
   * Extracts JARs from an archive (fat JAR, WAR) using parallel streams for I/O.
   *
   * @param archive Archive file to extract from
   * @param tempDir Temporary directory for extraction
   * @param warnings List to collect warnings
   * @return Set of discovered JAR paths
   */
  private Set<Path> discoverFromArchive(Path archive, Path tempDir, List<String> warnings) {
    Set<Path> jars = ConcurrentHashMap.newKeySet();
    jars.add(archive); // Include the archive itself

    try (JarFile jar = new JarFile(archive.toFile())) {
      // Collect entries first, then extract in parallel
      List<JarEntry> entriesToExtract =
          jar.stream()
              .filter(e -> !e.isDirectory())
              .filter(e -> e.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
              .filter(e -> isLibraryPath(e.getName()))
              .toList();

      log.debug("Found {} nested JARs to extract", entriesToExtract.size());

      // Parallel extraction using parallelStream() - Java 21 GA feature
      // I/O-bound operations benefit from parallel execution
      entriesToExtract.parallelStream()
          .forEach(
              entry -> {
                try {
                  Path extracted = extractEntry(jar, entry, tempDir);
                  jars.add(extracted);
                } catch (IOException e) {
                  warnings.add(
                      "Failed to extract nested JAR: " + entry.getName() + " - " + e.getMessage());
                }
              });

      // Follow MANIFEST Class-Path header
      Manifest manifest = jar.getManifest();
      if (manifest != null) {
        String classPath = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPath != null && !classPath.isBlank()) {
          Set<Path> manifestJars = resolveClassPath(archive.getParent(), classPath, warnings);
          jars.addAll(manifestJars);
          log.debug("Resolved {} JARs from MANIFEST Class-Path", manifestJars.size());
        }
      }

    } catch (ZipException e) {
      warnings.add("Corrupted archive skipped: " + archive + " - " + e.getMessage());
      log.warn("Corrupted archive: {}", archive, e);
    } catch (IOException e) {
      warnings.add("Cannot read archive: " + archive + " - " + e.getMessage());
      log.error("Failed to read archive: {}", archive, e);
    }

    return jars;
  }

  /**
   * Checks if an entry name is a library path in common archive formats.
   *
   * @param name Entry name to check
   * @return true if entry is in a library directory
   */
  private boolean isLibraryPath(String name) {
    for (String prefix : LIBRARY_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    // Generic nested lib pattern: any/path/lib/something.jar
    return name.matches(".*/lib/[^/]+\\.jar");
  }

  /**
   * Extracts a JAR entry to a temporary directory.
   *
   * @param jarFile Source JAR file
   * @param entry Entry to extract
   * @param tempDir Target directory
   * @return Path to extracted file
   * @throws IOException if extraction fails
   */
  private Path extractEntry(JarFile jarFile, JarEntry entry, Path tempDir) throws IOException {
    // Flatten nested paths to avoid deep directory structures
    String fileName = Path.of(entry.getName()).getFileName().toString();
    Path target = tempDir.resolve(fileName);

    // Handle duplicate file names by adding a unique suffix
    if (Files.exists(target)) {
      String baseName = fileName.substring(0, fileName.length() - 4); // Remove .jar
      target =
          tempDir.resolve(baseName + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jar");
    }

    try (InputStream in = jarFile.getInputStream(entry);
        OutputStream out = Files.newOutputStream(target)) {
      in.transferTo(out);
    }

    log.trace("Extracted: {} -> {}", entry.getName(), target.getFileName());
    return target;
  }

  /**
   * Resolves JARs referenced in a MANIFEST Class-Path header.
   *
   * @param baseDir Base directory for relative path resolution
   * @param classPath Space-separated list of JAR paths
   * @param warnings List to collect warnings
   * @return Set of resolved JAR paths
   */
  private Set<Path> resolveClassPath(Path baseDir, String classPath, List<String> warnings) {
    Set<Path> resolved = new LinkedHashSet<>();

    for (String entry : classPath.split("\\s+")) {
      if (entry.isBlank()) {
        continue;
      }

      Path jarPath = baseDir.resolve(entry).normalize();
      if (Files.exists(jarPath) && Files.isRegularFile(jarPath)) {
        resolved.add(jarPath);
      } else {
        warnings.add("MANIFEST Class-Path entry not found: " + entry);
      }
    }

    return resolved;
  }

  /**
   * Checks if a file is an archive (JAR or WAR).
   *
   * @param path Path to check
   * @return true if file is an archive
   */
  private boolean isArchive(Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".jar") || name.endsWith(".war");
  }
}
