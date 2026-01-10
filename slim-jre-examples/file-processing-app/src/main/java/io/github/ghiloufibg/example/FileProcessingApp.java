package io.github.ghiloufibg.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * File processing application demonstrating NIO.2 file operations. Uses java.base module features
 * for file I/O.
 */
public class FileProcessingApp {

  public static void main(String[] args) {
    System.out.println("=================================");
    System.out.println("  File Processing - Slim JRE Demo");
    System.out.println("=================================");
    System.out.println();

    // Display Java version
    System.out.println("Java Version: " + System.getProperty("java.version"));
    System.out.println("Java Home: " + System.getProperty("java.home"));
    System.out.println();

    Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "slim-jre-demo");

    try {
      // Create working directory
      Files.createDirectories(workDir);
      System.out.println("Working directory: " + workDir);
      System.out.println();

      // Create and write a sample file
      Path dataFile = workDir.resolve("sample-data.txt");
      List<String> lines =
          IntStream.rangeClosed(1, 10)
              .mapToObj(
                  i ->
                      "Line "
                          + i
                          + ": Sample data at "
                          + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .collect(Collectors.toList());

      Files.write(dataFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      System.out.println("Created file: " + dataFile.getFileName());
      System.out.println("Written " + lines.size() + " lines");
      System.out.println();

      // Read and process the file
      System.out.println("Reading file contents:");
      List<String> readLines = Files.readAllLines(dataFile);
      readLines.forEach(line -> System.out.println("  " + line));
      System.out.println();

      // File statistics
      System.out.println("File Statistics:");
      System.out.println("  Size: " + Files.size(dataFile) + " bytes");
      System.out.println("  Last Modified: " + Files.getLastModifiedTime(dataFile));
      System.out.println("  Is Regular File: " + Files.isRegularFile(dataFile));
      System.out.println("  Is Readable: " + Files.isReadable(dataFile));
      System.out.println("  Is Writable: " + Files.isWritable(dataFile));
      System.out.println();

      // Create a summary file using streams
      Path summaryFile = workDir.resolve("summary.txt");
      long wordCount =
          readLines.stream().flatMap(line -> List.of(line.split("\\s+")).stream()).count();
      long charCount = readLines.stream().mapToLong(String::length).sum();

      String summary =
          String.format(
              "Summary Report%n"
                  + "==============%n"
                  + "Total Lines: %d%n"
                  + "Total Words: %d%n"
                  + "Total Characters: %d%n"
                  + "Generated: %s%n",
              readLines.size(),
              wordCount,
              charCount,
              LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

      Files.writeString(summaryFile, summary);
      System.out.println("Created summary: " + summaryFile.getFileName());
      System.out.println(summary);

      // List directory contents
      System.out.println("Directory contents:");
      try (var stream = Files.list(workDir)) {
        stream.forEach(
            p -> {
              try {
                System.out.println("  " + p.getFileName() + " (" + Files.size(p) + " bytes)");
              } catch (IOException e) {
                System.out.println("  " + p.getFileName() + " (size unknown)");
              }
            });
      }
      System.out.println();

      // Cleanup (optional - keep files for inspection)
      System.out.println("Cleanup:");
      Files.deleteIfExists(dataFile);
      System.out.println("  Deleted: " + dataFile.getFileName());
      Files.deleteIfExists(summaryFile);
      System.out.println("  Deleted: " + summaryFile.getFileName());
      Files.deleteIfExists(workDir);
      System.out.println("  Deleted: " + workDir.getFileName());
      System.out.println();

      System.out.println("File processing completed successfully!");
      System.out.println("(This proves NIO.2 file operations are working)");

    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
