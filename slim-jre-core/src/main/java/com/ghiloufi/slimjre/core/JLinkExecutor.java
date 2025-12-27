package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.config.JLinkOptions;
import com.ghiloufi.slimjre.exception.JLinkException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.spi.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes jlink to create custom runtime images. Uses the ToolProvider API for in-process
 * execution.
 */
public class JLinkExecutor {

  private static final Logger log = LoggerFactory.getLogger(JLinkExecutor.class);

  private final ToolProvider jlink;

  /**
   * Creates a new JLinkExecutor.
   *
   * @throws JLinkException if jlink is not available
   */
  public JLinkExecutor() {
    this.jlink =
        ToolProvider.findFirst("jlink")
            .orElseThrow(
                () -> new JLinkException("jlink tool not found. Ensure you are running on JDK 9+"));
  }

  /**
   * Creates a custom JRE with the specified options.
   *
   * @param options jlink configuration
   * @return path to the created runtime
   * @throws JLinkException if jlink execution fails
   */
  public Path createRuntime(JLinkOptions options) {
    Path outputPath = options.outputPath();

    // Clean up existing output if present
    if (Files.exists(outputPath)) {
      log.debug("Removing existing output directory: {}", outputPath);
      try {
        deleteDirectory(outputPath);
      } catch (IOException e) {
        throw new JLinkException("Failed to remove existing output directory: " + outputPath, e);
      }
    }

    // Build arguments
    List<String> args = options.toArguments();
    log.debug("jlink arguments: {}", args);

    // Execute jlink
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    log.info("Creating custom JRE at {}...", outputPath);

    int exitCode =
        jlink.run(new PrintStream(out), new PrintStream(err), args.toArray(new String[0]));

    String output = out.toString().trim();
    String error = err.toString().trim();

    if (!output.isEmpty()) {
      log.debug("jlink output: {}", output);
    }

    if (exitCode != 0) {
      throw new JLinkException(
          "jlink failed with exit code " + exitCode + (error.isEmpty() ? "" : ": " + error));
    }

    if (!error.isEmpty()) {
      log.warn("jlink warnings: {}", error);
    }

    if (!Files.exists(outputPath)) {
      throw new JLinkException(
          "jlink completed but output directory was not created: " + outputPath);
    }

    log.info("Custom JRE created successfully at {}", outputPath);

    return outputPath;
  }

  /**
   * Calculates the size of the JRE directory.
   *
   * @param jrePath path to the JRE
   * @return size in bytes
   */
  public long calculateJreSize(Path jrePath) {
    if (!Files.exists(jrePath)) {
      return 0;
    }

    AtomicLong size = new AtomicLong(0);

    try {
      Files.walkFileTree(
          jrePath,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              size.addAndGet(attrs.size());
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      log.warn("Failed to calculate JRE size: {}", e.getMessage());
      return 0;
    }

    return size.get();
  }

  /** Returns the size of the current JDK/JRE. */
  public long getCurrentJdkSize() {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null) {
      return 0;
    }
    return calculateJreSize(Path.of(javaHome));
  }

  /** Deletes a directory recursively. */
  private void deleteDirectory(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }

    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Verifies that the created JRE can run Java.
   *
   * @param jrePath path to the JRE
   * @return true if the JRE is functional
   */
  public boolean verifyJre(Path jrePath) {
    Path javaBin =
        jrePath
            .resolve("bin")
            .resolve(
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

    if (!Files.exists(javaBin)) {
      log.error("Java binary not found in JRE: {}", javaBin);
      return false;
    }

    try {
      ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), "-version");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("Java verification failed with exit code: {}", exitCode);
        return false;
      }

      log.debug("JRE verified successfully");
      return true;

    } catch (IOException | InterruptedException e) {
      log.error("Failed to verify JRE: {}", e.getMessage());
      return false;
    }
  }
}
