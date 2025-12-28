package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for ServiceLoaderScanner. */
class ServiceLoaderScannerTest {

  private ServiceLoaderScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new ServiceLoaderScanner();
  }

  @Test
  void shouldReturnKnownServiceMappings() {
    Map<String, String> mappings = scanner.getKnownServiceMappings();

    assertThat(mappings)
        .isNotEmpty()
        .containsEntry("java.sql.Driver", "java.sql")
        .containsEntry("javax.xml.parsers.DocumentBuilderFactory", "java.xml");
  }

  @Test
  void shouldScanEmptyJarList() {
    Set<String> modules = scanner.scanForServiceModules(List.of());

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldScanJarWithSqlDriverService() throws IOException {
    // Create a JAR with META-INF/services/java.sql.Driver
    Path jar = createJarWithService(tempDir, "test.jar", "java.sql.Driver");

    Set<String> modules = scanner.scanForServiceModules(List.of(jar));

    assertThat(modules).contains("java.sql");
  }

  @Test
  void shouldScanJarWithXmlService() throws IOException {
    Path jar = createJarWithService(tempDir, "xml.jar", "javax.xml.parsers.DocumentBuilderFactory");

    Set<String> modules = scanner.scanForServiceModules(List.of(jar));

    assertThat(modules).contains("java.xml");
  }

  @Test
  void shouldScanMultipleJars() throws IOException {
    Path jar1 = createJarWithService(tempDir, "sql.jar", "java.sql.Driver");
    Path jar2 = createJarWithService(tempDir, "xml.jar", "javax.xml.parsers.SAXParserFactory");

    Set<String> modules = scanner.scanForServiceModules(List.of(jar1, jar2));

    assertThat(modules).contains("java.sql", "java.xml");
  }

  @Test
  void shouldHandleUnknownServices() throws IOException {
    Path jar = createJarWithService(tempDir, "custom.jar", "com.example.CustomService");

    Set<String> modules = scanner.scanForServiceModules(List.of(jar));

    // Unknown services should not add any JDK modules
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldFindServiceInterfaces() throws IOException {
    Path jar =
        createJarWithService(tempDir, "multi.jar", "java.sql.Driver", "com.example.MyService");

    Set<String> services = scanner.scanForServiceInterfaces(List.of(jar));

    assertThat(services).contains("java.sql.Driver", "com.example.MyService");
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    // Should not throw, just log a warning
    Set<String> modules = scanner.scanForServiceModules(List.of(nonExistent));

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldMapPackagePrefixesToModules() throws IOException {
    // Test package-based resolution for javax.naming
    Path jar =
        createJarWithService(tempDir, "naming.jar", "javax.naming.spi.InitialContextFactory");

    Set<String> modules = scanner.scanForServiceModules(List.of(jar));

    assertThat(modules).contains("java.naming");
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    // Create multiple JARs with different services
    Path jar1 = createJarWithService(tempDir, "jar1.jar", "java.sql.Driver");
    Path jar2 = createJarWithService(tempDir, "jar2.jar", "javax.xml.parsers.SAXParserFactory");
    Path jar3 = createJarWithService(tempDir, "jar3.jar", "javax.script.ScriptEngineFactory");
    Path jar4 = createJarWithService(tempDir, "jar4.jar", "java.util.logging.Handler");

    Set<String> modules = scanner.scanForServiceModulesParallel(List.of(jar1, jar2, jar3, jar4));

    assertThat(modules).contains("java.sql", "java.xml", "java.scripting", "java.logging");
  }

  @Test
  void shouldHandleEmptyListInParallelScan() {
    Set<String> modules = scanner.scanForServiceModulesParallel(List.of());
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldHandleNullListInParallelScan() {
    Set<String> modules = scanner.scanForServiceModulesParallel(null);
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldFallbackToSequentialForSmallJarCount() throws IOException {
    // With 2 or fewer JARs, should use sequential scan
    Path jar1 = createJarWithService(tempDir, "single.jar", "java.sql.Driver");

    Set<String> modules = scanner.scanForServiceModulesParallel(List.of(jar1));

    assertThat(modules).contains("java.sql");
  }

  /** Creates a JAR file with the specified service declarations. */
  private Path createJarWithService(Path dir, String jarName, String... serviceInterfaces)
      throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      for (String service : serviceInterfaces) {
        JarEntry entry = new JarEntry("META-INF/services/" + service);
        jos.putNextEntry(entry);
        // Write a dummy implementation class name
        jos.write(("com.example.impl." + service.replace(".", "") + "Impl\n").getBytes());
        jos.closeEntry();
      }
    }

    return jarPath;
  }
}
