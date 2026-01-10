package io.github.ghiloufibg.example.zipfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Demonstrates ZIP filesystem features that require jdk.zipfs module.
 *
 * <p>This application uses the ZIP filesystem provider to treat ZIP/JAR files as filesystems. The
 * ZipFsModuleScanner should detect these patterns:
 *
 * <ul>
 *   <li>FileSystems.newFileSystem() - Creates filesystem from ZIP/JAR path
 *   <li>FileSystems.getFileSystem() - Gets existing filesystem by URI
 *   <li>"jar:" URI scheme - Used to reference entries inside archives
 * </ul>
 *
 * <p>Without jdk.zipfs, this app will fail with: FileSystemNotFoundException: Provider "jar" not
 * found
 */
public class ZipFsApp {

  public static void main(String[] args) throws IOException {
    System.out.println("=== ZIP Filesystem Application Demo ===\n");

    // Create a temporary ZIP file for demonstration
    Path tempZip = Files.createTempFile("demo", ".zip");
    System.out.println("Created temporary ZIP file: " + tempZip);

    try {
      // Demonstrate creating a ZIP filesystem
      demonstrateNewFileSystem(tempZip);

      // Demonstrate using jar: URI scheme
      demonstrateJarUri(tempZip);

      System.out.println("\n=== Demo Complete ===");
    } finally {
      // Cleanup
      Files.deleteIfExists(tempZip);
      System.out.println("Cleaned up temporary file.");
    }
  }

  /**
   * Demonstrates FileSystems.newFileSystem() usage.
   *
   * <p>This pattern is detected by ZipFsModuleScanner as it requires jdk.zipfs.
   */
  private static void demonstrateNewFileSystem(Path zipPath) throws IOException {
    System.out.println("\n--- FileSystems.newFileSystem() Demo ---");

    // Create environment for creating new ZIP file
    Map<String, String> env = new HashMap<>();
    env.put("create", "true");

    // Create a new ZIP filesystem (requires jdk.zipfs)
    try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, env)) {
      System.out.println("Created ZIP filesystem: " + zipFs);

      // Create a file inside the ZIP
      Path entryPath = zipFs.getPath("/hello.txt");
      Files.writeString(entryPath, "Hello from inside the ZIP!");
      System.out.println("Created entry: " + entryPath);

      // List root contents
      System.out.println("ZIP contents:");
      Files.list(zipFs.getPath("/")).forEach(p -> System.out.println("  - " + p));
    }
  }

  /**
   * Demonstrates jar: URI scheme usage.
   *
   * <p>This pattern is detected by ZipFsModuleScanner via string constant detection.
   */
  private static void demonstrateJarUri(Path zipPath) throws IOException {
    System.out.println("\n--- jar: URI Scheme Demo ---");

    // Build jar: URI for the ZIP file
    URI jarUri = URI.create("jar:" + zipPath.toUri());
    System.out.println("JAR URI: " + jarUri);

    // Open existing ZIP filesystem using URI
    try (FileSystem zipFs = FileSystems.newFileSystem(jarUri, Map.of())) {
      // Read the file we created earlier
      Path entryPath = zipFs.getPath("/hello.txt");
      String content = Files.readString(entryPath);
      System.out.println("Read from ZIP: " + content);

      // Show file attributes
      System.out.println("Entry size: " + Files.size(entryPath) + " bytes");
      System.out.println("Entry exists: " + Files.exists(entryPath));
    }
  }
}
