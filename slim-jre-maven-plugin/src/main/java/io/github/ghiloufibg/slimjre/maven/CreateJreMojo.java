package io.github.ghiloufibg.slimjre.maven;

import io.github.ghiloufibg.slimjre.config.Result;
import io.github.ghiloufibg.slimjre.config.SlimJreConfig;
import io.github.ghiloufibg.slimjre.core.SlimJre;
import io.github.ghiloufibg.slimjre.exception.SlimJreException;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Creates a minimal custom JRE for the project.
 *
 * <p>This goal analyzes the project's JAR and its dependencies to determine the required JDK
 * modules, then uses jlink to create a slim JRE.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * <plugin>
 *     <groupId>io.github.ghiloufibg</groupId>
 *     <artifactId>slim-jre-maven-plugin</artifactId>
 *     <version>1.0.0-alpha.1</version>
 *     <executions>
 *         <execution>
 *             <phase>package</phase>
 *             <goals>
 *                 <goal>create-jre</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(
    name = "create-jre",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    threadSafe = true)
public class CreateJreMojo extends AbstractSlimJreMojo {

  /** Output directory for the slim JRE. */
  @Parameter(
      property = "slimjre.outputDirectory",
      defaultValue = "${project.build.directory}/slim-jre")
  private File outputDirectory;

  /** Whether to strip debug information from the JRE. */
  @Parameter(property = "slimjre.stripDebug", defaultValue = "true")
  private boolean stripDebug;

  /** Compression level: zip-0 to zip-9. */
  @Parameter(property = "slimjre.compression", defaultValue = "zip-6")
  private String compression;

  /** Whether to exclude header files from the JRE. */
  @Parameter(property = "slimjre.noHeaderFiles", defaultValue = "true")
  private boolean noHeaderFiles;

  /** Whether to exclude man pages from the JRE. */
  @Parameter(property = "slimjre.noManPages", defaultValue = "true")
  private boolean noManPages;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping slim-jre:create-jre");
      return;
    }

    // Skip for POM packaging
    if ("pom".equals(project.getPackaging())) {
      getLog().info("Skipping slim-jre for POM packaging");
      return;
    }

    try {
      getLog().info("Creating minimal JRE for " + project.getArtifactId());

      // Collect JARs
      List<Path> jars = collectJars();

      // Build configuration
      SlimJreConfig config =
          SlimJreConfig.builder()
              .jars(jars)
              .outputPath(outputDirectory.toPath())
              .stripDebug(stripDebug)
              .compression(compression)
              .noHeaderFiles(noHeaderFiles)
              .noManPages(noManPages)
              .scanServiceLoaders(scanServiceLoaders)
              .scanGraalVmMetadata(scanGraalVmMetadata)
              .cryptoMode(cryptoMode)
              .includeModules(getIncludeModulesSet())
              .excludeModules(getExcludedModulesSet())
              .verbose(verbose)
              .build();

      // Create the slim JRE
      SlimJre slimJre = new SlimJre();
      Result result = slimJre.createMinimalJre(config);

      // Log result
      getLog().info("");
      getLog().info("Slim JRE created successfully!");
      getLog().info("  Path: " + result.jrePath());
      getLog().info("  Modules: " + result.includedModules().size());
      getLog().info("  Size: " + formatSize(result.slimJreSize()));

      if (result.originalJreSize() > 0) {
        getLog().info("  Reduction: " + String.format("%.0f%%", result.reductionPercentage()));
      }

      getLog().info("  Time: " + formatDuration(result.duration().toMillis()));

    } catch (SlimJreException e) {
      throw new MojoExecutionException("Failed to create slim JRE: " + e.getMessage(), e);
    }
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else {
      return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
  }

  private String formatDuration(long millis) {
    if (millis < 1000) {
      return millis + "ms";
    } else {
      return String.format("%.1fs", millis / 1000.0);
    }
  }
}
