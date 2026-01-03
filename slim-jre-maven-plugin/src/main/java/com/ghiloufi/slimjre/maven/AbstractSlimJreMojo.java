package com.ghiloufi.slimjre.maven;

import com.ghiloufi.slimjre.config.CryptoMode;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Base class for Slim JRE Maven plugin mojos. */
public abstract class AbstractSlimJreMojo extends AbstractMojo {

  /** The Maven project. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;

  /**
   * Custom artifact (JAR file or directory) to analyze instead of the project's artifact. If not
   * specified, the plugin analyzes the project's main JAR and its runtime dependencies. Can be an
   * absolute path or relative to the project directory.
   *
   * <p>Example:
   *
   * <pre>{@code
   * <configuration>
   *     <artifact>${project.build.directory}/my-custom.jar</artifact>
   * </configuration>
   * }</pre>
   */
  @Parameter(property = "slimjre.artifact")
  protected File artifact;

  /** Additional modules to include beyond those detected. */
  @Parameter(property = "slimjre.includeModules")
  protected List<String> includeModules;

  /** Modules to exclude from the final JRE. */
  @Parameter(property = "slimjre.excludeModules")
  protected List<String> excludeModules;

  /** Whether to scan for service loader dependencies. */
  @Parameter(property = "slimjre.scanServiceLoaders", defaultValue = "true")
  protected boolean scanServiceLoaders;

  /** Whether to scan GraalVM native-image metadata for additional modules. */
  @Parameter(property = "slimjre.scanGraalVmMetadata", defaultValue = "true")
  protected boolean scanGraalVmMetadata;

  /**
   * Controls how SSL/TLS and cryptographic module requirements are handled.
   *
   * <ul>
   *   <li>{@code AUTO} (default): Automatically detect SSL/TLS usage and include crypto modules if
   *       needed
   *   <li>{@code ALWAYS}: Always include crypto modules regardless of detection
   *   <li>{@code NEVER}: Never include crypto modules, even if SSL/TLS usage is detected
   * </ul>
   */
  @Parameter(property = "slimjre.cryptoMode", defaultValue = "AUTO")
  protected CryptoMode cryptoMode;

  /** Whether to output verbose logging. */
  @Parameter(property = "slimjre.verbose", defaultValue = "false")
  protected boolean verbose;

  /** Skip execution of this plugin. */
  @Parameter(property = "slimjre.skip", defaultValue = "false")
  protected boolean skip;

  /**
   * Collects all JAR files for analysis. If a custom artifact is specified, only that artifact is
   * analyzed. Otherwise, includes the project's main artifact and all runtime dependencies.
   */
  protected List<Path> collectJars() throws MojoExecutionException {
    List<Path> jars = new ArrayList<>();

    // If custom artifact is specified, use only that
    if (artifact != null) {
      if (!artifact.exists()) {
        throw new MojoExecutionException("Specified artifact not found: " + artifact);
      }

      if (artifact.isDirectory()) {
        // Collect all JARs from the directory
        File[] jarFiles = artifact.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles != null) {
          for (File jarFile : jarFiles) {
            jars.add(jarFile.toPath());
            getLog().debug("Added JAR from directory: " + jarFile);
          }
        }
        if (jars.isEmpty()) {
          throw new MojoExecutionException("No JAR files found in directory: " + artifact);
        }
      } else {
        // Single JAR file
        jars.add(artifact.toPath());
        getLog().debug("Added custom artifact: " + artifact);
      }

      getLog().info("Using custom artifact: " + artifact);
      getLog().info("Collected " + jars.size() + " JAR(s) for analysis");
      return jars;
    }

    // Default behavior: use project artifact and dependencies
    File artifactFile = project.getArtifact().getFile();
    if (artifactFile == null) {
      // Try the build output directory
      artifactFile =
          new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar");
    }

    if (!artifactFile.exists()) {
      throw new MojoExecutionException(
          "Project artifact not found: "
              + artifactFile
              + ". Ensure the project has been built (mvn package).");
    }

    jars.add(artifactFile.toPath());
    getLog().debug("Added project artifact: " + artifactFile);

    // Add runtime dependencies
    for (Artifact dep : project.getArtifacts()) {
      if ("compile".equals(dep.getScope()) || "runtime".equals(dep.getScope())) {
        File file = dep.getFile();
        if (file != null && file.exists() && file.getName().endsWith(".jar")) {
          jars.add(file.toPath());
          getLog().debug("Added dependency: " + file);
        }
      }
    }

    getLog().info("Collected " + jars.size() + " JAR(s) for analysis");
    return jars;
  }

  /** Returns additional modules as a set. */
  protected Set<String> getIncludeModulesSet() {
    if (includeModules == null || includeModules.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(includeModules);
  }

  /** Returns excluded modules as a set. */
  protected Set<String> getExcludedModulesSet() {
    if (excludeModules == null || excludeModules.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(excludeModules);
  }
}
