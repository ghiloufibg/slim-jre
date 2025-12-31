package com.ghiloufi.slimjre.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans bytecode to detect ZIP filesystem API usage patterns that require the {@code jdk.zipfs}
 * module.
 *
 * <p>This scanner addresses a fundamental limitation of jdeps: it cannot detect ZIP filesystem
 * module requirements because the ZIP filesystem provider is loaded via service provider mechanism
 * at runtime. When applications use {@code FileSystems.newFileSystem()} to access ZIP or JAR files
 * as filesystems, the actual implementation in {@code jdk.nio.zipfs} is loaded dynamically, which
 * jdeps cannot trace.
 *
 * <p>The scanner detects:
 *
 * <ul>
 *   <li>FileSystems API: {@code java.nio.file.FileSystems.newFileSystem()} calls
 *   <li>FileSystemProvider: {@code java.nio.file.spi.FileSystemProvider} usage
 *   <li>URI schemes: "jar:" URI string constants indicating JAR filesystem access
 *   <li>Archive patterns: String constants ending with ".zip" or ".jar" in filesystem contexts
 * </ul>
 *
 * <p>Example detections:
 *
 * <pre>
 *   FileSystems.newFileSystem(path)              → jdk.zipfs
 *   FileSystems.newFileSystem(URI.create("jar:...")) → jdk.zipfs
 *   FileSystemProvider.installedProviders()      → jdk.zipfs
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/jdk.zipfs/module-summary.html">
 *     jdk.zipfs module documentation</a>
 */
public class ZipFsModuleScanner {

  private static final Logger log = LoggerFactory.getLogger(ZipFsModuleScanner.class);

  /**
   * ZIP filesystem API patterns that indicate {@code jdk.zipfs} is required. These patterns are
   * internal class names (using '/' separator) as used in bytecode.
   */
  private static final Set<String> ZIPFS_CLASSES =
      Set.of(
          // Core filesystem factory
          "java/nio/file/FileSystems",

          // Service provider interface for filesystem implementations
          "java/nio/file/spi/FileSystemProvider");

  /**
   * Methods that specifically indicate ZIP filesystem usage when called on FileSystems class. These
   * are method names to check when the owner is FileSystems.
   */
  private static final Set<String> FILESYSTEM_FACTORY_METHODS =
      Set.of("newFileSystem", "getFileSystem");

  /** Creates a new ZipFsModuleScanner. */
  public ZipFsModuleScanner() {
    log.debug("Initialized ZipFsModuleScanner with {} class patterns", ZIPFS_CLASSES.size());
  }

  /**
   * Scans multiple JARs in parallel for ZIP filesystem API usage.
   *
   * <p>This method scans ALL provided JARs, including application code and dependencies. This is
   * important because libraries may internally use ZIP filesystem APIs, and their usage must be
   * detected.
   *
   * @param jars list of JAR paths to scan (application + dependencies)
   * @return detection result with required modules and detected patterns
   */
  public ZipFsDetectionResult scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return ZipFsDetectionResult.empty();
    }

    log.debug("Scanning {} JAR(s) for ZIP filesystem patterns...", jars.size());

    Set<String> detectedInJars = ConcurrentHashMap.newKeySet();
    Set<String> detectedPatterns = ConcurrentHashMap.newKeySet();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<JarScanResult>> futures =
          jars.stream().map(jar -> executor.submit(() -> scanJar(jar))).toList();

      for (Future<JarScanResult> future : futures) {
        try {
          JarScanResult result = future.get();
          if (!result.patterns.isEmpty()) {
            detectedInJars.add(result.jarName);
            detectedPatterns.addAll(result.patterns);
          }
        } catch (Exception e) {
          log.warn("Failed to get ZIP filesystem scan result: {}", e.getMessage());
        }
      }
    }

    // Determine required modules based on detected patterns
    Set<String> requiredModules = new TreeSet<>();
    if (!detectedPatterns.isEmpty()) {
      requiredModules.add("jdk.zipfs");
      log.info(
          "ZipFs detection: Found ZIP filesystem patterns in {} JAR(s), adding jdk.zipfs",
          detectedInJars.size());
      if (log.isDebugEnabled()) {
        log.debug("JARs with ZIP filesystem usage: {}", detectedInJars);
      }
    } else {
      log.debug("ZipFs detection: No ZIP filesystem patterns found");
    }

    return new ZipFsDetectionResult(
        requiredModules, new TreeSet<>(detectedPatterns), new LinkedHashSet<>(detectedInJars));
  }

  /**
   * Scans a single JAR file for ZIP filesystem API usage.
   *
   * @param jarPath path to the JAR file to scan
   * @return scan result with JAR name and detected patterns
   */
  JarScanResult scanJar(Path jarPath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    String jarName = jarPath.getFileName().toString();

    if (!Files.exists(jarPath)) {
      log.warn("JAR file does not exist: {}", jarPath);
      return new JarScanResult(jarName, Set.of());
    }

    Set<String> detectedPatterns = new LinkedHashSet<>();

    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
          // Skip module-info.class as it doesn't contain API usage
          if (entry.getName().equals("module-info.class")
              || entry.getName().endsWith("/module-info.class")) {
            continue;
          }
          try (InputStream is = jar.getInputStream(entry);
              BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
            detectedPatterns.addAll(scanClass(bis));
          } catch (IOException e) {
            log.trace("Failed to scan class {}: {}", entry.getName(), e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to scan JAR {}: {}", jarPath, e.getMessage());
      return new JarScanResult(jarName, Set.of());
    }

    return new JarScanResult(jarName, detectedPatterns);
  }

  /**
   * Scans a single class from an input stream for ZIP filesystem API usage.
   *
   * @param classInputStream input stream containing class bytes
   * @return set of detected ZIP filesystem patterns
   */
  Set<String> scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    ZipFsPatternVisitor visitor = new ZipFsPatternVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return visitor.getDetectedPatterns();
  }

  /**
   * Checks if an internal class name matches any ZIP filesystem pattern.
   *
   * @param internalName class name in internal format (e.g., "java/nio/file/FileSystems")
   * @return true if this is a ZIP filesystem API class
   */
  boolean matchesPattern(String internalName) {
    if (internalName == null || internalName.isEmpty()) {
      return false;
    }

    return ZIPFS_CLASSES.contains(internalName);
  }

  /**
   * Checks if a string constant indicates ZIP filesystem usage.
   *
   * @param value the string constant value
   * @return true if this string suggests ZIP/JAR filesystem access
   */
  boolean isZipFsStringConstant(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }

    // Check for "jar:" URI scheme
    if (value.startsWith("jar:")) {
      return true;
    }

    // Check for ZIP filesystem type identifiers
    if (value.equals("jar") || value.equals("zip")) {
      return true;
    }

    return false;
  }

  /** Internal record to hold per-JAR scan results. */
  record JarScanResult(String jarName, Set<String> patterns) {}

  /** ASM ClassVisitor that detects ZIP filesystem API usage patterns. */
  private class ZipFsPatternVisitor extends ClassVisitor {

    private final Set<String> detectedPatterns = new LinkedHashSet<>();

    ZipFsPatternVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      // Check superclass
      checkTypeReference(superName);

      // Check implemented interfaces
      if (interfaces != null) {
        for (String iface : interfaces) {
          checkTypeReference(iface);
        }
      }
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      checkDescriptor(descriptor);

      // Check static final String fields for ZIP filesystem constants
      if (value instanceof String stringValue) {
        checkStringConstant(stringValue, "field:" + name);
      }

      return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      checkDescriptor(descriptor);

      if (exceptions != null) {
        for (String exception : exceptions) {
          checkTypeReference(exception);
        }
      }

      return new ZipFsMethodVisitor();
    }

    Set<String> getDetectedPatterns() {
      return new TreeSet<>(detectedPatterns);
    }

    private void checkTypeReference(String internalName) {
      if (matchesPattern(internalName)) {
        detectedPatterns.add(internalName);
      }
    }

    private void checkDescriptor(String descriptor) {
      if (descriptor == null) {
        return;
      }

      Type type = Type.getType(descriptor);
      checkType(type);
    }

    private void checkType(Type type) {
      if (type == null) {
        return;
      }

      switch (type.getSort()) {
        case Type.ARRAY -> checkType(type.getElementType());
        case Type.OBJECT -> checkTypeReference(type.getInternalName());
        case Type.METHOD -> {
          checkType(type.getReturnType());
          for (Type argType : type.getArgumentTypes()) {
            checkType(argType);
          }
        }
      }
    }

    private void checkStringConstant(String value, String context) {
      if (isZipFsStringConstant(value)) {
        detectedPatterns.add("string:" + value + " (" + context + ")");
      }
    }

    /** Inner method visitor to check method body instructions. */
    private class ZipFsMethodVisitor extends MethodVisitor {

      ZipFsMethodVisitor() {
        super(Opcodes.ASM9);
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        checkTypeReference(owner);
        checkDescriptor(descriptor);

        // Check specifically for FileSystems.newFileSystem() calls
        if ("java/nio/file/FileSystems".equals(owner)
            && FILESYSTEM_FACTORY_METHODS.contains(name)) {
          detectedPatterns.add("FileSystems." + name + "()");
        }

        // Check for FileSystemProvider methods
        if ("java/nio/file/spi/FileSystemProvider".equals(owner)) {
          detectedPatterns.add("FileSystemProvider." + name + "()");
        }
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        checkTypeReference(owner);
        checkDescriptor(descriptor);
      }

      @Override
      public void visitTypeInsn(int opcode, String type) {
        // NEW, ANEWARRAY, CHECKCAST, INSTANCEOF
        checkTypeReference(type);
      }

      @Override
      public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        checkDescriptor(descriptor);
      }

      @Override
      public void visitLdcInsn(Object value) {
        // Check string constants loaded onto the stack
        if (value instanceof String stringValue) {
          checkStringConstant(stringValue, "ldc");
        }
      }

      @Override
      public void visitTryCatchBlock(
          org.objectweb.asm.Label start,
          org.objectweb.asm.Label end,
          org.objectweb.asm.Label handler,
          String type) {
        if (type != null) {
          checkTypeReference(type);
        }
      }

      @Override
      public void visitLocalVariable(
          String name,
          String descriptor,
          String signature,
          org.objectweb.asm.Label start,
          org.objectweb.asm.Label end,
          int index) {
        checkDescriptor(descriptor);
      }
    }
  }
}
