package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for GraalVmMetadataScanner. */
class GraalVmMetadataScannerTest {

  private GraalVmMetadataScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new GraalVmMetadataScanner();
  }

  @Test
  void shouldExtractMavenCoordinatesFromPomProperties() throws IOException {
    Path jar = createJarWithPomProperties(tempDir, "test.jar", "org.example", "mylib", "1.0.0");

    Optional<MavenCoordinates> coords = scanner.extractMavenCoordinates(jar);

    assertThat(coords).isPresent();
    assertThat(coords.get().groupId()).isEqualTo("org.example");
    assertThat(coords.get().artifactId()).isEqualTo("mylib");
    assertThat(coords.get().version()).isEqualTo("1.0.0");
    assertThat(coords.get().toGav()).isEqualTo("org.example:mylib:1.0.0");
  }

  @Test
  void shouldReturnEmptyForJarWithoutPomProperties() throws IOException {
    Path jar = createEmptyJar(tempDir, "empty.jar");

    Optional<MavenCoordinates> coords = scanner.extractMavenCoordinates(jar);

    assertThat(coords).isEmpty();
  }

  @Test
  void shouldParseReflectConfigForSqlModule() {
    String json =
        """
        [
          {"name": "java.sql.Driver", "allDeclaredMethods": true},
          {"name": "java.sql.Connection", "allPublicMethods": true}
        ]
        """;

    Set<String> modules = scanner.parseReflectConfig(json);

    assertThat(modules).contains("java.sql");
  }

  @Test
  void shouldParseReflectConfigForNamingModule() {
    String json =
        """
        [
          {"name": "javax.naming.InitialContext", "allDeclaredMethods": true}
        ]
        """;

    Set<String> modules = scanner.parseReflectConfig(json);

    assertThat(modules).contains("java.naming");
  }

  @Test
  void shouldIgnoreJavaBaseClasses() {
    String json =
        """
        [
          {"name": "java.lang.String", "allDeclaredMethods": true},
          {"name": "java.util.HashMap", "allPublicMethods": true}
        ]
        """;

    Set<String> modules = scanner.parseReflectConfig(json);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldIgnoreNonJdkClasses() {
    String json =
        """
        [
          {"name": "org.apache.logging.Log", "allDeclaredMethods": true},
          {"name": "com.example.MyClass", "allPublicMethods": true}
        ]
        """;

    Set<String> modules = scanner.parseReflectConfig(json);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldParseResourceConfigForClassPatterns() {
    String json =
        """
        {
          "resources": {
            "includes": [
              {"pattern": "java/sql/Driver.class"},
              {"pattern": "javax/naming/Context.class"}
            ]
          }
        }
        """;

    Set<String> modules = scanner.parseResourceConfig(json);

    assertThat(modules).contains("java.sql", "java.naming");
  }

  @Test
  void shouldScanEmbeddedMetadataFromJar() throws IOException {
    String reflectConfig =
        """
        [
          {"name": "java.sql.Driver", "allDeclaredMethods": true}
        ]
        """;

    Path jar = createJarWithEmbeddedMetadata(tempDir, "test.jar", reflectConfig, null, null);

    Set<String> modules = scanner.scanEmbeddedMetadata(jar);

    assertThat(modules).contains("java.sql");
  }

  @Test
  void shouldScanJar() throws IOException {
    String reflectConfig =
        """
        [
          {"name": "java.sql.Driver", "allDeclaredMethods": true},
          {"name": "javax.naming.Context", "allPublicMethods": true}
        ]
        """;

    Path jar = createJarWithEmbeddedMetadata(tempDir, "test.jar", reflectConfig, null, null);

    Set<String> modules = scanner.scanJar(jar);

    assertThat(modules).contains("java.sql", "java.naming");
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    String reflectConfig1 =
        """
        [{"name": "java.sql.Driver"}]
        """;
    String reflectConfig2 =
        """
        [{"name": "javax.naming.Context"}]
        """;

    Path jar1 = createJarWithEmbeddedMetadata(tempDir, "jar1.jar", reflectConfig1, null, null);
    Path jar2 = createJarWithEmbeddedMetadata(tempDir, "jar2.jar", reflectConfig2, null, null);

    Set<String> modules = scanner.scanJarsParallel(List.of(jar1, jar2));

    assertThat(modules).contains("java.sql", "java.naming");
  }

  @Test
  void shouldHandleEmptyJarList() {
    Set<String> modules = scanner.scanJarsParallel(List.of());
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldHandleNullJarList() {
    Set<String> modules = scanner.scanJarsParallel(null);
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    Set<String> modules = scanner.scanJar(nonExistent);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldGetModuleForKnownClass() {
    Optional<String> module = scanner.getModuleForClass("java.sql.Driver");

    assertThat(module).isPresent();
    assertThat(module.get()).isEqualTo("java.sql");
  }

  @Test
  void shouldReturnEmptyForUnknownClass() {
    Optional<String> module = scanner.getModuleForClass("com.example.NonExistent");

    assertThat(module).isEmpty();
  }

  @Test
  void shouldCombineReflectAndJniConfig() throws IOException {
    String reflectConfig =
        """
        [{"name": "java.sql.Driver"}]
        """;
    String jniConfig =
        """
        [{"name": "javax.naming.Context"}]
        """;

    Path jar =
        createJarWithEmbeddedMetadata(tempDir, "combined.jar", reflectConfig, jniConfig, null);

    Set<String> modules = scanner.scanEmbeddedMetadata(jar);

    assertThat(modules).contains("java.sql", "java.naming");
  }

  @Test
  void shouldHandleMalformedJson() throws IOException {
    String badJson = "{ this is not valid json }";

    Path jar = createJarWithEmbeddedMetadata(tempDir, "bad.jar", badJson, null, null);

    // Should not throw, just return empty or partial results
    Set<String> modules = scanner.scanEmbeddedMetadata(jar);
    assertThat(modules).isNotNull();
  }

  @Test
  void shouldParseConfigDirectory() throws IOException {
    Path configDir = Files.createDirectories(tempDir.resolve("config"));

    String reflectConfig =
        """
        [{"name": "java.sql.Driver"}]
        """;
    Files.writeString(configDir.resolve("reflect-config.json"), reflectConfig);

    Set<String> modules = scanner.parseConfigDirectory(configDir);

    assertThat(modules).contains("java.sql");
  }

  // Helper methods

  private Path createJarWithPomProperties(
      Path dir, String jarName, String groupId, String artifactId, String version)
      throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      // Create pom.properties
      String propsPath = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
      jos.putNextEntry(new JarEntry(propsPath));

      Properties props = new Properties();
      props.setProperty("groupId", groupId);
      props.setProperty("artifactId", artifactId);
      props.setProperty("version", version);
      props.store(jos, null);

      jos.closeEntry();
    }

    return jarPath;
  }

  private Path createEmptyJar(Path dir, String jarName) throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      // Add a dummy entry
      jos.putNextEntry(new JarEntry("dummy.txt"));
      jos.write("dummy".getBytes(StandardCharsets.UTF_8));
      jos.closeEntry();
    }

    return jarPath;
  }

  private Path createJarWithEmbeddedMetadata(
      Path dir, String jarName, String reflectConfig, String jniConfig, String resourceConfig)
      throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      if (reflectConfig != null) {
        jos.putNextEntry(
            new JarEntry("META-INF/native-image/org.example/mylib/reflect-config.json"));
        jos.write(reflectConfig.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
      }

      if (jniConfig != null) {
        jos.putNextEntry(new JarEntry("META-INF/native-image/org.example/mylib/jni-config.json"));
        jos.write(jniConfig.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
      }

      if (resourceConfig != null) {
        jos.putNextEntry(
            new JarEntry("META-INF/native-image/org.example/mylib/resource-config.json"));
        jos.write(resourceConfig.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
      }
    }

    return jarPath;
  }
}
