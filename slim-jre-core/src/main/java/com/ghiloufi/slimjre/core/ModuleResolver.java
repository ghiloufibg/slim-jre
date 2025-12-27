package com.ghiloufi.slimjre.core;

import com.ghiloufi.slimjre.exception.ModuleResolutionException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves module dependencies including transitive requirements. Uses the Java module system APIs
 * to discover available modules and their dependencies.
 */
public class ModuleResolver {

  private static final Logger log = LoggerFactory.getLogger(ModuleResolver.class);

  private final Set<String> availableModules;
  private final Map<String, Set<String>> moduleRequires;

  /** Creates a new ModuleResolver for the current JDK. */
  public ModuleResolver() {
    this.availableModules = new TreeSet<>();
    this.moduleRequires = new HashMap<>();
    discoverModules();
  }

  /** Discovers all available JDK modules and their dependencies. */
  private void discoverModules() {
    // Use system module finder to get all JDK modules
    ModuleFinder finder = ModuleFinder.ofSystem();

    for (ModuleReference ref : finder.findAll()) {
      ModuleDescriptor descriptor = ref.descriptor();
      String moduleName = descriptor.name();

      availableModules.add(moduleName);

      // Get required modules (excludes 'java.base' as it's always implicitly required)
      Set<String> requires =
          descriptor.requires().stream()
              .map(ModuleDescriptor.Requires::name)
              .collect(Collectors.toSet());

      moduleRequires.put(moduleName, requires);
    }

    log.debug("Discovered {} JDK modules", availableModules.size());
  }

  /**
   * Resolves all required modules including transitive dependencies.
   *
   * @param directModules modules directly required
   * @return complete set of modules including transitive dependencies
   * @throws ModuleResolutionException if a required module is not found
   */
  public Set<String> resolveWithTransitive(Set<String> directModules) {
    Set<String> resolved = new TreeSet<>();
    Deque<String> toProcess = new ArrayDeque<>(directModules);

    while (!toProcess.isEmpty()) {
      String module = toProcess.pop();

      if (resolved.contains(module)) {
        continue;
      }

      if (!availableModules.contains(module)) {
        // Check if it's an application module (not a JDK module)
        if (module.startsWith("java.")
            || module.startsWith("jdk.")
            || module.startsWith("javafx.")
            || module.startsWith("oracle.")) {
          throw new ModuleResolutionException(
              module,
              "JDK module '"
                  + module
                  + "' not found in current JDK. "
                  + "Available modules: "
                  + availableModules);
        }
        // Skip application modules - they don't need to be included
        log.debug("Skipping non-JDK module: {}", module);
        continue;
      }

      resolved.add(module);

      // Add transitive dependencies
      Set<String> requires = moduleRequires.get(module);
      if (requires != null) {
        for (String req : requires) {
          if (!resolved.contains(req)) {
            toProcess.add(req);
          }
        }
      }
    }

    // Always ensure java.base is included
    resolved.add("java.base");

    log.debug(
        "Resolved {} modules from {} direct dependencies", resolved.size(), directModules.size());

    return resolved;
  }

  /**
   * Returns all available JDK modules in the current runtime.
   *
   * @return set of available module names
   */
  public Set<String> availableModules() {
    return Collections.unmodifiableSet(availableModules);
  }

  /**
   * Checks if a module is available in the current JDK.
   *
   * @param moduleName module name to check
   * @return true if the module is available
   */
  public boolean isAvailable(String moduleName) {
    return availableModules.contains(moduleName);
  }

  /**
   * Returns the direct dependencies of a module.
   *
   * @param moduleName module to query
   * @return set of directly required modules, or empty set if not found
   */
  public Set<String> getDirectDependencies(String moduleName) {
    Set<String> requires = moduleRequires.get(moduleName);
    return requires != null ? Collections.unmodifiableSet(requires) : Set.of();
  }

  /**
   * Filters a set of modules to only include those available in the current JDK.
   *
   * @param modules modules to filter
   * @return filtered set containing only available JDK modules
   */
  public Set<String> filterToAvailable(Set<String> modules) {
    return modules.stream()
        .filter(availableModules::contains)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Returns modules that are commonly included but optional. These can be excluded if not needed to
   * reduce JRE size.
   */
  public Set<String> getOptionalModules() {
    return Set.of(
        "java.desktop",
        "java.rmi",
        "java.compiler",
        "java.instrument",
        "jdk.management",
        "jdk.management.agent",
        "jdk.jcmd",
        "jdk.jconsole",
        "jdk.jshell",
        "jdk.jdeps",
        "jdk.jlink",
        "jdk.jfr");
  }
}
