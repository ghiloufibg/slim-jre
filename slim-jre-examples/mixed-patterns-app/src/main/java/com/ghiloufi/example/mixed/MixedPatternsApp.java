package com.ghiloufi.example.mixed;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Tests multiple scanner detection patterns in combination.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.sql - JDBC via reflection (Class.forName("java.sql.Driver"))
 *   <li>java.naming - JNDI via reflection
 *   <li>java.logging - Logger static import
 *   <li>java.xml - DocumentBuilderFactory static import
 *   <li>java.net.http - HttpClient static import
 *   <li>jdk.crypto.ec - HTTP client for HTTPS
 *   <li>jdk.zipfs - FileSystems.newFileSystem
 *   <li>java.management - ManagementFactory static import
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ReflectionBytecodeScanner: MUST detect java.sql.Driver, javax.naming.InitialContext
 *   <li>ApiUsageScanner: MUST detect Logger, DocumentBuilderFactory, HttpClient, ManagementFactory
 *   <li>CryptoModuleScanner: MUST detect HttpClient (implies HTTPS)
 *   <li>ZipFsModuleScanner: MUST detect FileSystems.newFileSystem
 *   <li>JmxModuleScanner: MUST detect ManagementFactory usage
 *   <li>jdeps: MUST detect all static imports
 * </ul>
 *
 * <p>This is a comprehensive test combining multiple detection patterns.
 */
public class MixedPatternsApp {

  private static final Logger log = Logger.getLogger(MixedPatternsApp.class.getName());

  public static void main(String[] args) {
    System.out.println("=== Mixed Patterns Test (Comprehensive Scanner Test) ===\n");

    // Pattern 1: Logging (java.logging - static import)
    testLogging();

    // Pattern 2: Reflection (java.sql - Class.forName)
    testReflection();

    // Pattern 3: XML (java.xml - static import)
    testXml();

    // Pattern 4: HTTP Client (java.net.http + jdk.crypto.ec)
    testHttpClient();

    // Pattern 5: ZipFS (jdk.zipfs)
    testZipFs();

    // Pattern 6: JMX/Management (java.management)
    testManagement();

    System.out.println("\n=== Test Complete ===");
    summarizeExpectedModules();
  }

  private static void testLogging() {
    System.out.println("--- Pattern 1: Logging (java.logging) ---");

    log.info("This is an INFO message");
    log.warning("This is a WARNING message");
    log.log(Level.FINE, "This is a FINE message");

    System.out.println("[OK] java.logging tested (static import: Logger)");
  }

  private static void testReflection() {
    System.out.println("\n--- Pattern 2: Reflection (java.sql, java.naming) ---");

    // Reflection pattern for java.sql
    try {
      Class<?> driverClass = Class.forName("java.sql.Driver");
      System.out.println("[OK] Class.forName(java.sql.Driver): " + driverClass.getName());
    } catch (ClassNotFoundException e) {
      System.out.println("[MISSING] java.sql.Driver not found (needs java.sql module)");
    }

    // Reflection pattern for java.naming
    try {
      Class<?> contextClass = Class.forName("javax.naming.InitialContext");
      System.out.println(
          "[OK] Class.forName(javax.naming.InitialContext): " + contextClass.getName());
    } catch (ClassNotFoundException e) {
      System.out.println(
          "[MISSING] javax.naming.InitialContext not found (needs java.naming module)");
    }
  }

  private static void testXml() {
    System.out.println("\n--- Pattern 3: XML (java.xml) ---");

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.newDocument();
      Element root = doc.createElement("root");
      doc.appendChild(root);

      System.out.println("[OK] java.xml tested (static import: DocumentBuilderFactory)");
    } catch (Exception e) {
      System.out.println("[ERROR] XML test failed: " + e.getMessage());
    }
  }

  private static void testHttpClient() {
    System.out.println("\n--- Pattern 4: HTTP Client (java.net.http + jdk.crypto.ec) ---");

    // This pattern requires both java.net.http AND jdk.crypto.ec
    HttpClient client = HttpClient.newBuilder().build();
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create("https://example.com")).GET().build();

    System.out.println("[OK] HttpClient created (requires java.net.http)");
    System.out.println("[OK] HTTPS URI used (requires jdk.crypto.ec)");

    // Don't actually make the request in test
    // client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void testZipFs() {
    System.out.println("\n--- Pattern 5: ZipFS (jdk.zipfs) ---");

    try {
      Path tempZip = Files.createTempFile("test", ".zip");
      Map<String, String> env = Map.of("create", "true");

      // This requires jdk.zipfs
      try (FileSystem zipFs = FileSystems.newFileSystem(tempZip, env)) {
        Path entry = zipFs.getPath("/test.txt");
        Files.writeString(entry, "Hello from ZipFS");
        System.out.println("[OK] jdk.zipfs tested (FileSystems.newFileSystem)");
      }

      Files.deleteIfExists(tempZip);
    } catch (IOException e) {
      System.out.println("[ERROR] ZipFS test failed: " + e.getMessage());
    }
  }

  private static void testManagement() {
    System.out.println("\n--- Pattern 6: JMX/Management (java.management) ---");

    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

    System.out.println("[OK] RuntimeMXBean: " + runtime.getName());
    System.out.println("[OK] Heap memory: " + memory.getHeapMemoryUsage());
    System.out.println("[OK] java.management tested (static import: ManagementFactory)");
  }

  private static void summarizeExpectedModules() {
    System.out.println("\n=== Expected Modules Summary ===");
    System.out.println("  java.base      - always included");
    System.out.println("  java.logging   - Logger static import");
    System.out.println("  java.sql       - Class.forName(java.sql.Driver)");
    System.out.println("  java.naming    - Class.forName(javax.naming.InitialContext)");
    System.out.println("  java.xml       - DocumentBuilderFactory static import");
    System.out.println("  java.net.http  - HttpClient static import");
    System.out.println("  jdk.crypto.ec  - HTTPS usage via HttpClient");
    System.out.println("  jdk.zipfs      - FileSystems.newFileSystem");
    System.out.println("  java.management - ManagementFactory static import");
  }
}
