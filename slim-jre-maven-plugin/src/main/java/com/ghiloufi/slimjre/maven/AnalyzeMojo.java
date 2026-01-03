package com.ghiloufi.slimjre.maven;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.core.SlimJre;
import com.ghiloufi.slimjre.exception.SlimJreException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Analyzes the project to determine required JDK modules without creating a JRE.
 *
 * <p>This is a dry-run goal useful for debugging and understanding what modules your application
 * requires.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * mvn slim-jre:analyze
 * }</pre>
 */
@Mojo(
    name = "analyze",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    threadSafe = true)
public class AnalyzeMojo extends AbstractSlimJreMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping slim-jre:analyze");
      return;
    }

    // Skip for POM packaging
    if ("pom".equals(project.getPackaging())) {
      getLog().info("Skipping slim-jre for POM packaging");
      return;
    }

    try {
      getLog().info("Analyzing " + project.getArtifactId() + " for module dependencies");

      // Collect JARs
      List<Path> jars = collectJars();

      // Analyze
      SlimJre slimJre = new SlimJre();
      AnalysisResult result = slimJre.analyzeOnly(jars, scanServiceLoaders, scanGraalVmMetadata);

      // Combine with additional/excluded modules
      Set<String> allModules = new java.util.TreeSet<>(result.allModules());
      allModules.addAll(getIncludeModulesSet());
      allModules.removeAll(getExcludedModulesSet());

      // Log results
      getLog().info("");
      getLog().info("=== Module Analysis ===");
      getLog().info("");

      getLog().info("Required Modules (jdeps): " + result.requiredModules().size());
      for (String module : result.requiredModules().stream().sorted().toList()) {
        getLog().info("  - " + module);
      }

      if (!result.serviceLoaderModules().isEmpty()) {
        getLog().info("");
        getLog().info("Service Loader Modules: " + result.serviceLoaderModules().size());
        for (String module : result.serviceLoaderModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.reflectionModules().isEmpty()) {
        getLog().info("");
        getLog().info("Reflection Modules: " + result.reflectionModules().size());
        for (String module : result.reflectionModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.apiUsageModules().isEmpty()) {
        getLog().info("");
        getLog().info("API Usage Modules: " + result.apiUsageModules().size());
        for (String module : result.apiUsageModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.graalVmMetadataModules().isEmpty()) {
        getLog().info("");
        getLog().info("GraalVM Metadata Modules: " + result.graalVmMetadataModules().size());
        for (String module : result.graalVmMetadataModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.cryptoModules().isEmpty()) {
        getLog().info("");
        getLog().info("Crypto Modules (SSL/TLS): " + result.cryptoModules().size());
        for (String module : result.cryptoModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.localeModules().isEmpty()) {
        getLog().info("");
        getLog().info("Locale Modules (i18n): " + result.localeModules().size());
        for (String module : result.localeModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.zipFsModules().isEmpty()) {
        getLog().info("");
        getLog().info("ZipFS Modules (ZIP filesystem): " + result.zipFsModules().size());
        for (String module : result.zipFsModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!result.jmxModules().isEmpty()) {
        getLog().info("");
        getLog().info("JMX Modules (remote management): " + result.jmxModules().size());
        for (String module : result.jmxModules().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      if (!getIncludeModulesSet().isEmpty()) {
        getLog().info("");
        getLog().info("Include Modules (configured): " + getIncludeModulesSet().size());
        for (String module : getIncludeModulesSet().stream().sorted().toList()) {
          getLog().info("  + " + module);
        }
      }

      if (!getExcludedModulesSet().isEmpty()) {
        getLog().info("");
        getLog().info("Excluded Modules (configured): " + getExcludedModulesSet().size());
        for (String module : getExcludedModulesSet().stream().sorted().toList()) {
          getLog().info("  - " + module);
        }
      }

      getLog().info("");
      getLog().info("Total Modules: " + allModules.size());
      getLog().info("  " + allModules.stream().sorted().collect(Collectors.joining(",")));

      if (verbose && !result.perJarModules().isEmpty()) {
        getLog().info("");
        getLog().info("=== Per-JAR Breakdown ===");
        for (var entry : result.perJarModules().entrySet()) {
          getLog().info("");
          getLog().info(entry.getKey().getFileName().toString() + ":");
          for (String module : entry.getValue().stream().sorted().toList()) {
            getLog().info("  - " + module);
          }
        }
      }

      // Print jlink command
      getLog().info("");
      getLog().info("=== jlink Command ===");
      getLog()
          .info(
              "jlink --add-modules "
                  + allModules.stream().sorted().collect(Collectors.joining(","))
                  + " --strip-debug --compress zip-6 --no-header-files --no-man-pages --output slim-jre");

    } catch (SlimJreException e) {
      throw new MojoExecutionException("Failed to analyze modules: " + e.getMessage(), e);
    }
  }
}
