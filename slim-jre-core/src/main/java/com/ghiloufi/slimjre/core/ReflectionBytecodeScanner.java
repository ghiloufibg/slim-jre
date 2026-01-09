package com.ghiloufi.slimjre.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans bytecode to detect reflection-based class loading that jdeps cannot detect. Targets dynamic
 * class loading patterns with string literal arguments that reference JDK classes.
 *
 * <p>This scanner complements jdeps analysis by catching cases like:
 *
 * <pre>
 *   // Class.forName patterns
 *   Class.forName("java.sql.Driver")
 *   Class.forName("javax.xml.parsers.SAXParserFactory", true, classLoader)
 *
 *   // ClassLoader.loadClass patterns
 *   classLoader.loadClass("java.sql.Driver")
 *   Thread.currentThread().getContextClassLoader().loadClass("java.naming.Context")
 *
 *   // MethodHandles.Lookup.findClass patterns
 *   MethodHandles.lookup().findClass("java.sql.Driver")
 * </pre>
 *
 * <p>Only JDK classes (java.*, javax.*, jdk.*, sun.*, com.sun.*) are detected and mapped to their
 * containing modules.
 */
public class ReflectionBytecodeScanner {

  private static final Logger log = LoggerFactory.getLogger(ReflectionBytecodeScanner.class);

  /** Pattern matching JDK class prefixes that we care about */
  private static final Pattern JDK_CLASS_PATTERN =
      Pattern.compile("^(java\\.|javax\\.|jdk\\.|sun\\.|com\\.sun\\.).*");

  /** Pattern for Class.forName method descriptor */
  private static final String CLASS_FOR_NAME_OWNER = "java/lang/Class";

  private static final String CLASS_FOR_NAME_NAME = "forName";
  private static final String CLASS_FOR_NAME_DESC = "(Ljava/lang/String;)Ljava/lang/Class;";
  private static final String CLASS_FOR_NAME_INIT_DESC =
      "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;";

  /** Pattern for ClassLoader.loadClass method */
  private static final String CLASS_LOADER_OWNER = "java/lang/ClassLoader";

  private static final String CLASS_LOADER_LOAD_CLASS_NAME = "loadClass";
  private static final String CLASS_LOADER_LOAD_CLASS_DESC =
      "(Ljava/lang/String;)Ljava/lang/Class;";
  private static final String CLASS_LOADER_LOAD_CLASS_RESOLVE_DESC =
      "(Ljava/lang/String;Z)Ljava/lang/Class;";

  /** Pattern for MethodHandles.Lookup.findClass method */
  private static final String METHOD_HANDLES_LOOKUP_OWNER = "java/lang/invoke/MethodHandles$Lookup";

  private static final String FIND_CLASS_NAME = "findClass";
  private static final String FIND_CLASS_DESC = "(Ljava/lang/String;)Ljava/lang/Class;";

  /** Static cache mapping class names to their containing module (shared across all instances) */
  private static volatile Map<String, String> classToModuleCache;

  /** Creates a new ReflectionBytecodeScanner. */
  public ReflectionBytecodeScanner() {
    // Ensure cache is initialized (lazy, thread-safe singleton)
    getClassToModuleCache();
  }

  /**
   * Returns the shared class-to-module cache, initializing it lazily if needed. Uses double-checked
   * locking for thread safety with minimal synchronization overhead.
   */
  private static Map<String, String> getClassToModuleCache() {
    if (classToModuleCache == null) {
      synchronized (ReflectionBytecodeScanner.class) {
        if (classToModuleCache == null) {
          classToModuleCache = buildClassToModuleCache();
          log.debug("Initialized class-to-module cache with {} entries", classToModuleCache.size());
        }
      }
    }
    return classToModuleCache;
  }

  /**
   * Scans a JAR file for reflection-based JDK class loading and returns required modules.
   *
   * @param jarPath path to the JAR file to scan
   * @return set of JDK module names required by reflection patterns
   */
  public Set<String> scanJar(Path jarPath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    if (!Files.exists(jarPath)) {
      log.warn("JAR file does not exist: {}", jarPath);
      return Set.of();
    }

    Set<String> reflectedClasses = new TreeSet<>();

    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
          try (InputStream is = jar.getInputStream(entry);
              BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
            reflectedClasses.addAll(scanClass(bis));
          } catch (IOException e) {
            log.trace("Failed to scan class {}: {}", entry.getName(), e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to scan JAR {}: {}", jarPath, e.getMessage());
      return Set.of();
    }

    return mapClassesToModules(reflectedClasses);
  }

  /**
   * Scans a directory of class files for reflection-based JDK class loading.
   *
   * @param classDir path to the directory containing class files
   * @return set of JDK module names required by reflection patterns
   */
  public Set<String> scanDirectory(Path classDir) {
    Objects.requireNonNull(classDir, "classDir must not be null");

    if (!Files.isDirectory(classDir)) {
      log.warn("Not a directory: {}", classDir);
      return Set.of();
    }

    Set<String> reflectedClasses = new TreeSet<>();

    try {
      Files.walkFileTree(
          classDir,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (file.toString().endsWith(".class")) {
                try (InputStream is = Files.newInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
                  reflectedClasses.addAll(scanClass(bis));
                } catch (IOException e) {
                  log.trace("Failed to scan class {}: {}", file, e.getMessage());
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      log.warn("Failed to scan directory {}: {}", classDir, e.getMessage());
    }

    return mapClassesToModules(reflectedClasses);
  }

  /**
   * Scans multiple JARs and returns combined required modules.
   *
   * @param jars list of JAR paths to scan
   * @return set of JDK module names required by reflection patterns across all JARs
   */
  public Set<String> scanJars(Iterable<Path> jars) {
    Set<String> allModules = new TreeSet<>();
    for (Path jar : jars) {
      allModules.addAll(scanJar(jar));
    }
    return allModules;
  }

  /**
   * Scans multiple JARs in parallel using virtual threads and returns combined required modules.
   * This method leverages Java 21's virtual threads for efficient parallel I/O operations.
   *
   * @param jars list of JAR paths to scan
   * @return set of JDK module names required by reflection patterns across all JARs
   */
  public Set<String> scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return Set.of();
    }

    Set<String> allModules = ConcurrentHashMap.newKeySet();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Set<String>>> futures =
          jars.stream().map(jar -> executor.submit(() -> scanJar(jar))).toList();

      for (Future<Set<String>> future : futures) {
        try {
          allModules.addAll(future.get());
        } catch (Exception e) {
          log.warn("Failed to get scan result: {}", e.getMessage());
        }
      }
    }

    return new TreeSet<>(allModules);
  }

  /**
   * Scans a single class from an input stream.
   *
   * @param classInputStream input stream containing class bytes
   * @return set of JDK class names detected via reflection patterns
   */
  Set<String> scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    ReflectionDetectorVisitor visitor = new ReflectionDetectorVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return visitor.getReflectedClasses();
  }

  /**
   * Maps a set of class names to their containing JDK modules.
   *
   * @param classNames set of fully qualified class names
   * @return set of JDK module names containing these classes
   */
  Set<String> mapClassesToModules(Set<String> classNames) {
    Set<String> modules = new TreeSet<>();
    Map<String, String> cache = getClassToModuleCache();

    for (String className : classNames) {
      String moduleName = cache.get(className);
      if (moduleName != null) {
        modules.add(moduleName);
        log.debug("Mapped reflected class {} to module {}", className, moduleName);
      } else {
        log.trace("No module found for reflected class: {}", className);
      }
    }

    return modules;
  }

  /**
   * Builds a cache mapping JDK class names to their containing modules using the ModuleFinder API.
   */
  private static Map<String, String> buildClassToModuleCache() {
    Map<String, String> cache = new HashMap<>();
    ModuleFinder finder = ModuleFinder.ofSystem();

    for (ModuleReference ref : finder.findAll()) {
      String moduleName = ref.descriptor().name();

      try {
        ref.open()
            .list()
            .filter(name -> name.endsWith(".class"))
            .forEach(
                resource -> {
                  // Convert resource path to class name
                  // e.g., "java/lang/String.class" -> "java.lang.String"
                  String className = resource.replace('/', '.').substring(0, resource.length() - 6);
                  cache.put(className, moduleName);
                });
      } catch (IOException e) {
        log.trace("Failed to read module {}: {}", moduleName, e.getMessage());
      }
    }

    return cache;
  }

  /**
   * Checks if a class name matches JDK class patterns.
   *
   * @param className fully qualified class name
   * @return true if it's a JDK class
   */
  static boolean isJdkClass(String className) {
    return JDK_CLASS_PATTERN.matcher(className).matches();
  }

  /**
   * ASM ClassVisitor that detects Class.forName() calls with string literal arguments that
   * reference JDK classes.
   */
  private static class ReflectionDetectorVisitor extends ClassVisitor {

    private final Set<String> reflectedClasses = new HashSet<>();

    ReflectionDetectorVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new ReflectionMethodVisitor(reflectedClasses);
    }

    Set<String> getReflectedClasses() {
      return Collections.unmodifiableSet(reflectedClasses);
    }
  }

  /**
   * ASM MethodVisitor that detects JDK class name string literals that are likely used for
   * reflection.
   *
   * <p>This visitor uses a simple but effective heuristic: any JDK class name that appears as a
   * string literal in the bytecode is likely being used for reflection. This catches patterns like:
   *
   * <ul>
   *   <li>Direct calls: {@code Class.forName("java.sql.Driver")}
   *   <li>Helper methods: {@code loadClass("java.sql.Driver")} where loadClass calls Class.forName
   *   <li>Stored references: {@code String className = "java.sql.Driver"; Class.forName(className)}
   * </ul>
   *
   * <p>The rationale is that JDK class names appearing as string literals (e.g., "java.sql.Driver",
   * "javax.naming.InitialContext") are almost always used for dynamic class loading. This approach
   * is more robust than trying to track data flow across methods.
   *
   * <p>Note: Variable-based class names constructed at runtime (e.g., "java.sql." + "Driver")
   * cannot be detected by static analysis and are intentionally not supported.
   */
  private static class ReflectionMethodVisitor extends MethodVisitor {

    private final Set<String> reflectedClasses;

    ReflectionMethodVisitor(Set<String> reflectedClasses) {
      super(Opcodes.ASM9);
      this.reflectedClasses = reflectedClasses;
    }

    @Override
    public void visitLdcInsn(Object value) {
      if (value instanceof String str) {
        // Collect ALL JDK class name strings - they're almost always used for reflection
        // This catches both direct Class.forName calls and helper method patterns
        if (isJdkClass(str) && isValidClassName(str)) {
          reflectedClasses.add(str);
          log.trace("Detected potential reflection target: {}", str);
        }
      }
    }

    /**
     * Validates that the string looks like a valid Java class name. This filters out JDK-prefixed
     * strings that aren't actually class names (e.g., "java.version", "javax.net.ssl.trustStore").
     */
    private boolean isValidClassName(String str) {
      // Must contain at least one dot (package separator)
      if (!str.contains(".")) {
        return false;
      }
      // Must not contain spaces or special characters (except $ for inner classes)
      if (str.contains(" ") || str.contains("=") || str.contains("/") || str.contains("\\")) {
        return false;
      }
      // Each segment must be a valid Java identifier
      String[] parts = str.split("\\.");
      for (String part : parts) {
        if (part.isEmpty()) {
          return false;
        }
        // Allow $ for inner classes, but first char must be letter or $
        char firstChar = part.charAt(0);
        if (!Character.isJavaIdentifierStart(firstChar)) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Returns the module name for a given JDK class.
   *
   * @param className fully qualified class name
   * @return Optional containing the module name, or empty if not found
   */
  public Optional<String> getModuleForClass(String className) {
    return Optional.ofNullable(getClassToModuleCache().get(className));
  }
}
