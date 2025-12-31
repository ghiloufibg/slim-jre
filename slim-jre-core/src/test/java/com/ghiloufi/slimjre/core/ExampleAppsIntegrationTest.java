package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import com.ghiloufi.slimjre.config.AnalysisResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Integration tests that analyze the example applications to verify scanner detection. These tests
 * verify that ZipFsModuleScanner and JmxModuleScanner correctly detect module requirements in
 * real-world example applications.
 */
class ExampleAppsIntegrationTest {

  private SlimJre slimJre;
  private static final Path EXAMPLES_DIR =
      Path.of("../slim-jre-examples").toAbsolutePath().normalize();

  @BeforeEach
  void setUp() {
    slimJre = new SlimJre();
  }

  private static boolean zipfsAppExists() {
    return Path.of("../slim-jre-examples/zipfs-app/target/zipfs-app-1.0.0-SNAPSHOT.jar")
        .toFile()
        .exists();
  }

  private static boolean jmxAppExists() {
    return Path.of("../slim-jre-examples/jmx-app/target/jmx-app-1.0.0-SNAPSHOT.jar")
        .toFile()
        .exists();
  }

  private static boolean localeAppExists() {
    return Path.of("../slim-jre-examples/locale-app/target/locale-app-1.0.0-SNAPSHOT.jar")
        .toFile()
        .exists();
  }

  @Test
  @EnabledIf("zipfsAppExists")
  void shouldDetectZipFsModuleInZipFsApp() {
    // Given: zipfs-app JAR that uses FileSystems.newFileSystem() and jar: URIs
    Path zipfsJar = EXAMPLES_DIR.resolve("zipfs-app/target/zipfs-app-1.0.0-SNAPSHOT.jar");

    // When: analyzing the JAR
    AnalysisResult result = slimJre.analyzeOnly(List.of(zipfsJar));

    // Then: jdk.zipfs should be detected
    assertThat(result.zipFsModules())
        .as("ZipFS modules detected in zipfs-app")
        .contains("jdk.zipfs");

    assertThat(result.allModules()).as("jdk.zipfs included in all modules").contains("jdk.zipfs");

    // Print summary for visibility
    System.out.println("=== ZipFS App Analysis ===");
    System.out.println(result.summary());
  }

  @Test
  @EnabledIf("jmxAppExists")
  void shouldDetectJmxModuleInJmxApp() {
    // Given: jmx-app JAR that uses JMXConnectorFactory, JMXServiceURL
    Path jmxJar = EXAMPLES_DIR.resolve("jmx-app/target/jmx-app-1.0.0-SNAPSHOT.jar");

    // When: analyzing the JAR
    AnalysisResult result = slimJre.analyzeOnly(List.of(jmxJar));

    // Then: java.management.rmi should be detected
    assertThat(result.jmxModules())
        .as("JMX modules detected in jmx-app")
        .contains("java.management.rmi");

    assertThat(result.allModules())
        .as("java.management.rmi included in all modules")
        .contains("java.management.rmi");

    // Print summary for visibility
    System.out.println("=== JMX App Analysis ===");
    System.out.println(result.summary());
  }

  @Test
  @EnabledIf("localeAppExists")
  void shouldDetectLocaleModuleInLocaleApp() {
    // Given: locale-app JAR that uses Locale.FRENCH, DateTimeFormatter.ofLocalizedDate(), etc.
    Path localeJar = EXAMPLES_DIR.resolve("locale-app/target/locale-app-1.0.0-SNAPSHOT.jar");

    // When: analyzing the JAR
    AnalysisResult result = slimJre.analyzeOnly(List.of(localeJar));

    // Then: jdk.localedata should be detected
    assertThat(result.localeModules())
        .as("Locale modules detected in locale-app")
        .contains("jdk.localedata");

    assertThat(result.allModules())
        .as("jdk.localedata included in all modules")
        .contains("jdk.localedata");

    // Print summary for visibility
    System.out.println("=== Locale App Analysis ===");
    System.out.println(result.summary());
  }

  @Test
  @EnabledIf("zipfsAppExists")
  void shouldIncludeZipFsInCombinedAnalysis() {
    // Verify that when combined with other modules, zipfs is properly included
    Path zipfsJar = EXAMPLES_DIR.resolve("zipfs-app/target/zipfs-app-1.0.0-SNAPSHOT.jar");
    AnalysisResult result = slimJre.analyzeOnly(List.of(zipfsJar));

    // Should have both jdeps-detected modules AND zipfs
    assertThat(result.requiredModules()).isNotEmpty();
    assertThat(result.zipFsModules()).contains("jdk.zipfs");
    assertThat(result.allModules()).containsAll(result.requiredModules());
    assertThat(result.allModules()).containsAll(result.zipFsModules());
  }

  @Test
  @EnabledIf("jmxAppExists")
  void shouldIncludeJmxInCombinedAnalysis() {
    // Verify that when combined with other modules, jmx is properly included
    Path jmxJar = EXAMPLES_DIR.resolve("jmx-app/target/jmx-app-1.0.0-SNAPSHOT.jar");
    AnalysisResult result = slimJre.analyzeOnly(List.of(jmxJar));

    // Should have both jdeps-detected modules AND jmx
    assertThat(result.requiredModules()).isNotEmpty();
    assertThat(result.jmxModules()).contains("java.management.rmi");
    assertThat(result.allModules()).containsAll(result.requiredModules());
    assertThat(result.allModules()).containsAll(result.jmxModules());
  }
}
