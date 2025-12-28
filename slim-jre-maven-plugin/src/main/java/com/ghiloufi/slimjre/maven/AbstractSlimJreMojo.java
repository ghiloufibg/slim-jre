package com.ghiloufi.slimjre.maven;

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

  /** Additional modules to include beyond those detected. */
  @Parameter(property = "slimjre.additionalModules")
  protected List<String> additionalModules;

  /** Modules to exclude from the final JRE. */
  @Parameter(property = "slimjre.excludeModules")
  protected List<String> excludeModules;

  /** Whether to scan for service loader dependencies. */
  @Parameter(property = "slimjre.scanServiceLoaders", defaultValue = "true")
  protected boolean scanServiceLoaders;

  /** Whether to scan GraalVM native-image metadata for additional modules. */
  @Parameter(property = "slimjre.scanGraalVmMetadata", defaultValue = "true")
  protected boolean scanGraalVmMetadata;

  /** Whether to output verbose logging. */
  @Parameter(property = "slimjre.verbose", defaultValue = "false")
  protected boolean verbose;

  /** Skip execution of this plugin. */
  @Parameter(property = "slimjre.skip", defaultValue = "false")
  protected boolean skip;

  /**
   * Collects all JAR files for analysis. Includes the project's main artifact and all runtime
   * dependencies.
   */
  protected List<Path> collectJars() throws MojoExecutionException {
    List<Path> jars = new ArrayList<>();

    // Add project artifact
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
    for (Artifact artifact : project.getArtifacts()) {
      if ("compile".equals(artifact.getScope()) || "runtime".equals(artifact.getScope())) {
        File file = artifact.getFile();
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
  protected Set<String> getAdditionalModulesSet() {
    if (additionalModules == null || additionalModules.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(additionalModules);
  }

  /** Returns excluded modules as a set. */
  protected Set<String> getExcludedModulesSet() {
    if (excludeModules == null || excludeModules.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(excludeModules);
  }
}
