package com.ghiloufi.slimjre.cli;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.CryptoMode;
import com.ghiloufi.slimjre.config.Result;
import com.ghiloufi.slimjre.config.SlimJreConfig;
import com.ghiloufi.slimjre.core.DiscoveryResult;
import com.ghiloufi.slimjre.core.JarDiscovery;
import com.ghiloufi.slimjre.core.SlimJre;
import com.ghiloufi.slimjre.exception.SlimJreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.*;

/** Command-line interface for Slim JRE. Creates minimal custom JREs for Java applications. */
@Command(
    name = "slim-jre",
    mixinStandardHelpOptions = true,
    version = "slim-jre 1.0.0",
    description = "Creates a minimal custom JRE for your Java application.",
    footer = {
      "",
      "Examples:",
      "  slim-jre myapp.jar",
      "  slim-jre myapp.jar -o custom-runtime --compress zip-9",
      "  slim-jre target/libs/ --add-modules java.management",
      "  slim-jre myapp.jar --analyze-only"
    })
public class SlimJreCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "JAR file or directory containing JARs")
  private Path input;

  @Option(
      names = {"-o", "--output"},
      description = "Output directory for the slim JRE (default: ${DEFAULT-VALUE})",
      defaultValue = "slim-jre")
  private Path outputPath;

  @Option(
      names = {"-cp", "--classpath"},
      description = "Additional classpath entries (path separator separated)",
      split = "[;:]")
  private List<Path> classpath;

  @Option(
      names = {"--add-modules"},
      description = "Additional modules to include (comma separated)",
      split = ",")
  private Set<String> addModules;

  @Option(
      names = {"--exclude-modules"},
      description = "Modules to exclude (comma separated)",
      split = ",")
  private Set<String> excludeModules;

  @Option(
      names = {"--compress"},
      description = "Compression level: zip-0 to zip-9 (default: ${DEFAULT-VALUE})",
      defaultValue = "zip-6")
  private String compression;

  @Option(
      names = {"--no-strip"},
      description = "Don't strip debug information",
      negatable = true,
      defaultValue = "false")
  private boolean noStrip;

  @Option(
      names = {"--no-service-scan"},
      description = "Don't scan for service loader dependencies",
      negatable = true,
      defaultValue = "false")
  private boolean noServiceScan;

  @Option(
      names = {"--no-graalvm-metadata"},
      description = "Don't scan GraalVM native-image metadata",
      negatable = true,
      defaultValue = "false")
  private boolean noGraalVmMetadata;

  @Option(
      names = {"--crypto"},
      description =
          "Crypto module handling: AUTO (detect and include if needed), "
              + "ALWAYS (always include for safety), NEVER (never include, HTTP-only apps). "
              + "Default: ${DEFAULT-VALUE}",
      defaultValue = "AUTO")
  private CryptoMode cryptoMode;

  @Option(
      names = {"--analyze-only"},
      description = "Only print required modules, don't create JRE")
  private boolean analyzeOnly;

  @Option(
      names = {"--verbose", "-v"},
      description = "Verbose output")
  private boolean verbose;

  @Override
  public Integer call() {
    DiscoveryResult discoveryResult = null;

    try {
      // Use JarDiscovery for comprehensive JAR discovery
      // Supports: directories, fat JARs (Spring Boot), WARs, MANIFEST Class-Path
      JarDiscovery jarDiscovery = new JarDiscovery();
      discoveryResult = jarDiscovery.discover(input);

      List<Path> jars = new ArrayList<>(discoveryResult.jarList());

      if (jars.isEmpty()) {
        System.err.println("Error: No JAR files found in " + input);
        return 1;
      }

      // Log discovery details
      if (discoveryResult.hasNestedJars()) {
        System.out.println("Discovered " + jars.size() + " JAR(s) from " + input);
        if (discoveryResult.tempDirectory() != null) {
          System.out.println("  (extracted nested JARs to temp directory)");
        }
      }

      if (discoveryResult.hasWarnings()) {
        for (String warning : discoveryResult.warnings()) {
          System.err.println("Warning: " + warning);
        }
      }

      // Add classpath JARs if specified
      if (classpath != null) {
        for (Path cpEntry : classpath) {
          DiscoveryResult cpDiscovery = jarDiscovery.discover(cpEntry);
          jars.addAll(cpDiscovery.jarList());
          // Note: cpDiscovery temp dirs will be cleaned up with discoveryResult
        }
      }

      if (verbose) {
        System.out.println("Analyzing " + jars.size() + " JAR(s):");
        for (Path jar : jars) {
          System.out.println("  - " + jar.getFileName());
        }
        System.out.println();
      }

      SlimJre slimJre = new SlimJre();

      if (analyzeOnly) {
        // Analysis mode
        AnalysisResult analysis = slimJre.analyzeOnly(jars, !noServiceScan, !noGraalVmMetadata);
        printAnalysis(analysis);
        return 0;
      }

      // Build configuration
      SlimJreConfig.Builder configBuilder =
          SlimJreConfig.builder()
              .jars(jars)
              .outputPath(outputPath)
              .stripDebug(!noStrip)
              .compression(compression)
              .scanServiceLoaders(!noServiceScan)
              .scanGraalVmMetadata(!noGraalVmMetadata)
              .cryptoMode(cryptoMode)
              .verbose(verbose);

      if (addModules != null) {
        configBuilder.additionalModules(addModules);
      }

      if (excludeModules != null) {
        configBuilder.excludeModules(excludeModules);
      }

      // Create the slim JRE
      Result result = slimJre.createMinimalJre(configBuilder.build());

      // Print result summary
      System.out.println();
      System.out.println(result.summary());

      return 0;

    } catch (SlimJreException e) {
      System.err.println("Error: " + e.getMessage());
      if (verbose && e.getCause() != null) {
        e.getCause().printStackTrace(System.err);
      }
      return 1;
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      if (verbose) {
        e.printStackTrace(System.err);
      }
      return 1;
    } finally {
      // Clean up temp directory from nested JAR extraction
      if (discoveryResult != null) {
        discoveryResult.close();
      }
    }
  }

  /** Collects JAR files from a path (either a single JAR or a directory). */
  private List<Path> collectJars(Path path) {
    List<Path> jars = new ArrayList<>();

    if (!Files.exists(path)) {
      throw new SlimJreException("Path does not exist: " + path);
    }

    if (Files.isDirectory(path)) {
      try (var stream = Files.list(path)) {
        stream.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(jars::add);
      } catch (IOException e) {
        throw new SlimJreException("Failed to read directory: " + path, e);
      }
    } else if (path.toString().toLowerCase().endsWith(".jar")) {
      jars.add(path);
    }

    return jars;
  }

  /** Prints the analysis result. */
  private void printAnalysis(AnalysisResult analysis) {
    System.out.println("Required Modules (jdeps):");
    printModuleSet(analysis.requiredModules(), "  ");

    if (!analysis.serviceLoaderModules().isEmpty()) {
      System.out.println();
      System.out.println("Service Loader Modules:");
      printModuleSet(analysis.serviceLoaderModules(), "  ");
    }

    if (!analysis.reflectionModules().isEmpty()) {
      System.out.println();
      System.out.println("Reflection Modules:");
      printModuleSet(analysis.reflectionModules(), "  ");
    }

    if (!analysis.apiUsageModules().isEmpty()) {
      System.out.println();
      System.out.println("API Usage Modules:");
      printModuleSet(analysis.apiUsageModules(), "  ");
    }

    if (!analysis.graalVmMetadataModules().isEmpty()) {
      System.out.println();
      System.out.println("GraalVM Metadata Modules:");
      printModuleSet(analysis.graalVmMetadataModules(), "  ");
    }

    if (!analysis.cryptoModules().isEmpty()) {
      System.out.println();
      System.out.println("Crypto Modules (SSL/TLS):");
      printModuleSet(analysis.cryptoModules(), "  ");
    }

    System.out.println();
    System.out.println("All Required Modules (" + analysis.allModules().size() + "):");
    System.out.println(
        "  " + analysis.allModules().stream().sorted().collect(Collectors.joining(",")));

    if (verbose && !analysis.perJarModules().isEmpty()) {
      System.out.println();
      System.out.println("Per-JAR Breakdown:");
      for (var entry : analysis.perJarModules().entrySet()) {
        System.out.println("  " + entry.getKey().getFileName() + ":");
        printModuleSet(entry.getValue(), "    ");
      }
    }

    // Print jlink command that would be executed
    System.out.println();
    System.out.println("To create a JRE manually, run:");
    System.out.println(
        "  jlink --add-modules "
            + analysis.allModules().stream().sorted().collect(Collectors.joining(","))
            + " --strip-debug --compress zip-6 --no-header-files --no-man-pages --output slim-jre");
  }

  /** Prints a set of modules with indentation. */
  private void printModuleSet(Set<String> modules, String indent) {
    if (modules.isEmpty()) {
      System.out.println(indent + "(none)");
    } else {
      modules.stream().sorted().forEach(m -> System.out.println(indent + m));
    }
  }

  /** Main entry point. */
  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new SlimJreCommand())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
    System.exit(exitCode);
  }
}
