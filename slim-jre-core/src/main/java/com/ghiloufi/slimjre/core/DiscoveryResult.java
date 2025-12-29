package com.ghiloufi.slimjre.core;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Result of JAR discovery from directories, archives, fat JARs, and WARs.
 *
 * <p>Implements AutoCloseable to clean up any temporary directories created during nested JAR
 * extraction (e.g., from Spring Boot fat JARs or WAR files).
 *
 * @param jars All discovered JAR files
 * @param tempDirectory Temporary directory for extracted nested JARs (null if no extraction needed)
 * @param hasNestedJars Whether the discovery included nested JAR extraction
 * @param warnings Any warnings encountered during discovery
 */
public record DiscoveryResult(
    Set<Path> jars, Path tempDirectory, boolean hasNestedJars, List<String> warnings)
    implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryResult.class);

  public DiscoveryResult {
    // Defensive copies for immutability
    jars = Set.copyOf(jars);
    warnings = List.copyOf(warnings);
  }

  /**
   * Builds a class path string from all discovered JARs.
   *
   * <p>Uses the platform-specific path separator (';' on Windows, ':' on Unix).
   *
   * @return Class path string suitable for jdeps --class-path argument
   */
  public String buildClassPath() {
    return jars.stream()
        .map(Path::toAbsolutePath)
        .map(Path::toString)
        .collect(Collectors.joining(File.pathSeparator));
  }

  /**
   * Returns all discovered JARs as a list (preserving order).
   *
   * @return List of JAR paths
   */
  public List<Path> jarList() {
    return List.copyOf(jars);
  }

  /**
   * Returns the number of discovered JARs.
   *
   * @return JAR count
   */
  public int jarCount() {
    return jars.size();
  }

  /**
   * Checks if any warnings were produced during discovery.
   *
   * @return true if warnings exist
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /**
   * Closes this discovery result and cleans up any temporary files.
   *
   * <p>If a temporary directory was created for nested JAR extraction, it will be recursively
   * deleted. This method is idempotent - calling it multiple times is safe.
   */
  @Override
  public void close() {
    if (tempDirectory != null && Files.exists(tempDirectory)) {
      try {
        deleteRecursively(tempDirectory);
        log.debug("Cleaned up temp directory: {}", tempDirectory);
      } catch (IOException e) {
        log.warn("Failed to clean up temp directory {}: {}", tempDirectory, e.getMessage());
      }
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param path Directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete: " + path, e);
    }
  }

  @Override
  public String toString() {
    return "DiscoveryResult{"
        + "jarCount="
        + jars.size()
        + ", hasNestedJars="
        + hasNestedJars
        + ", warnings="
        + warnings.size()
        + ", tempDirectory="
        + (tempDirectory != null ? tempDirectory : "none")
        + '}';
  }
}
