package io.github.ghiloufibg.slimjre.core;

import java.util.Objects;

/**
 * Represents Maven artifact coordinates extracted from a JAR.
 *
 * @param groupId the Maven group ID (e.g., "org.apache.commons")
 * @param artifactId the Maven artifact ID (e.g., "commons-lang3")
 * @param version the Maven version (e.g., "3.12.0")
 */
public record MavenCoordinates(String groupId, String artifactId, String version) {

  public MavenCoordinates {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(artifactId, "artifactId must not be null");
    Objects.requireNonNull(version, "version must not be null");
  }

  /**
   * Returns GAV format for GraalVM library queries.
   *
   * @return coordinates in "groupId:artifactId:version" format
   */
  public String toGav() {
    return groupId + ":" + artifactId + ":" + version;
  }

  /**
   * Returns the path format used in Maven repositories.
   *
   * @return path in "groupId/artifactId" format with dots replaced by slashes
   */
  public String toPath() {
    return groupId.replace('.', '/') + "/" + artifactId;
  }

  @Override
  public String toString() {
    return toGav();
  }
}
