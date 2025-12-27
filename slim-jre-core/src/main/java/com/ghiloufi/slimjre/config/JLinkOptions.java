package com.ghiloufi.slimjre.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Options for jlink execution.
 *
 * @param modules Modules to include in the custom JRE
 * @param outputPath Where to create the runtime image
 * @param stripDebug Whether to strip debug information
 * @param compression Compression level (zip-0 to zip-9)
 * @param noHeaderFiles Whether to exclude header files
 * @param noManPages Whether to exclude man pages
 * @param additionalModulePaths Additional module paths to search
 */
public record JLinkOptions(
    Set<String> modules,
    Path outputPath,
    boolean stripDebug,
    String compression,
    boolean noHeaderFiles,
    boolean noManPages,
    List<Path> additionalModulePaths) {
  public JLinkOptions {
    // Defensive copies
    modules = Set.copyOf(modules);
    additionalModulePaths = List.copyOf(additionalModulePaths);
  }

  /** Creates a new builder for JLinkOptions. */
  public static Builder builder() {
    return new Builder();
  }

  /** Converts these options to jlink command-line arguments. */
  public List<String> toArguments() {
    List<String> args = new ArrayList<>();

    // Add modules
    if (!modules.isEmpty()) {
      args.add("--add-modules");
      args.add(String.join(",", modules));
    }

    // Output path
    args.add("--output");
    args.add(outputPath.toAbsolutePath().toString());

    // Strip debug
    if (stripDebug) {
      args.add("--strip-debug");
    }

    // Compression
    args.add("--compress");
    args.add(compression);

    // No header files
    if (noHeaderFiles) {
      args.add("--no-header-files");
    }

    // No man pages
    if (noManPages) {
      args.add("--no-man-pages");
    }

    // Additional module paths
    if (!additionalModulePaths.isEmpty()) {
      args.add("--module-path");
      StringBuilder pathBuilder = new StringBuilder();
      for (int i = 0; i < additionalModulePaths.size(); i++) {
        if (i > 0) {
          pathBuilder.append(System.getProperty("path.separator"));
        }
        pathBuilder.append(additionalModulePaths.get(i).toAbsolutePath());
      }
      args.add(pathBuilder.toString());
    }

    return args;
  }

  /** Builder for JLinkOptions. */
  public static class Builder {
    private final Set<String> modules = new HashSet<>();
    private Path outputPath;
    private boolean stripDebug = true;
    private String compression = "zip-6";
    private boolean noHeaderFiles = true;
    private boolean noManPages = true;
    private final List<Path> additionalModulePaths = new ArrayList<>();

    /** Adds a module to include. */
    public Builder addModule(String module) {
      this.modules.add(module);
      return this;
    }

    /** Adds multiple modules to include. */
    public Builder modules(Set<String> modules) {
      this.modules.addAll(modules);
      return this;
    }

    /** Sets the output path. */
    public Builder outputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    /** Sets whether to strip debug information. */
    public Builder stripDebug(boolean stripDebug) {
      this.stripDebug = stripDebug;
      return this;
    }

    /** Sets the compression level. */
    public Builder compression(String compression) {
      this.compression = compression;
      return this;
    }

    /** Sets whether to exclude header files. */
    public Builder noHeaderFiles(boolean noHeaderFiles) {
      this.noHeaderFiles = noHeaderFiles;
      return this;
    }

    /** Sets whether to exclude man pages. */
    public Builder noManPages(boolean noManPages) {
      this.noManPages = noManPages;
      return this;
    }

    /** Adds an additional module path. */
    public Builder addModulePath(Path path) {
      this.additionalModulePaths.add(path);
      return this;
    }

    /** Builds the options. */
    public JLinkOptions build() {
      return new JLinkOptions(
          modules,
          outputPath,
          stripDebug,
          compression,
          noHeaderFiles,
          noManPages,
          additionalModulePaths);
    }
  }
}
