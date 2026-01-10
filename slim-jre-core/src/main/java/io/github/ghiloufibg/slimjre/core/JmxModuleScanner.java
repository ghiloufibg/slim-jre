package io.github.ghiloufibg.slimjre.core;

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
 * Scans bytecode to detect remote JMX API usage patterns that require the {@code
 * java.management.rmi} module.
 *
 * <p>This scanner addresses a fundamental limitation of jdeps: it cannot detect JMX remote module
 * requirements because JMX remote connectors use service provider mechanisms that are resolved at
 * runtime. When applications use {@code javax.management.remote.JMXConnectorFactory} or {@code
 * javax.management.remote.JMXConnectorServerFactory}, the RMI-based implementation is loaded via
 * service providers, which jdeps cannot trace.
 *
 * <p>The scanner detects:
 *
 * <ul>
 *   <li>JMX Remote APIs: {@code javax.management.remote.*} (JMXConnectorFactory,
 *       JMXConnectorServerFactory, etc.)
 *   <li>RMI-specific JMX: {@code javax.management.remote.rmi.*} (RMIConnector, RMIConnectorServer)
 *   <li>JMX Service URLs: {@code JMXServiceURL} for remote connections
 * </ul>
 *
 * <p>Example detections:
 *
 * <pre>
 *   JMXConnectorFactory.connect(...)           → java.management.rmi
 *   JMXConnectorServerFactory.newJMXConnectorServer(...) → java.management.rmi
 *   new JMXServiceURL("service:jmx:rmi://...")  → java.management.rmi
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/jmx/">Java Management Extensions
 *     Guide</a>
 */
public class JmxModuleScanner {

  private static final Logger log = LoggerFactory.getLogger(JmxModuleScanner.class);

  /**
   * JMX remote API classes that indicate {@code java.management.rmi} is required. These patterns
   * are internal class names (using '/' separator) as used in bytecode.
   */
  private static final Set<String> JMX_REMOTE_CLASSES =
      Set.of(
          // Core JMX Remote APIs
          "javax/management/remote/JMXConnector",
          "javax/management/remote/JMXConnectorFactory",
          "javax/management/remote/JMXConnectorServer",
          "javax/management/remote/JMXConnectorServerFactory",
          "javax/management/remote/JMXServiceURL",
          "javax/management/remote/JMXConnectorProvider",
          "javax/management/remote/JMXConnectorServerProvider",
          "javax/management/remote/JMXAuthenticator",
          "javax/management/remote/JMXPrincipal",
          "javax/management/remote/JMXConnectionNotification",
          "javax/management/remote/MBeanServerForwarder",
          "javax/management/remote/NotificationResult",
          "javax/management/remote/SubjectDelegationPermission",
          "javax/management/remote/TargetedNotification",

          // RMI-specific JMX connectors
          "javax/management/remote/rmi/RMIConnector",
          "javax/management/remote/rmi/RMIConnectorServer",
          "javax/management/remote/rmi/RMIConnection",
          "javax/management/remote/rmi/RMIConnectionImpl",
          "javax/management/remote/rmi/RMIIIOPServerImpl",
          "javax/management/remote/rmi/RMIJRMPServerImpl",
          "javax/management/remote/rmi/RMIServer",
          "javax/management/remote/rmi/RMIServerImpl");

  /** Package prefix that indicates JMX remote usage (for broader detection). */
  private static final String JMX_REMOTE_PACKAGE = "javax/management/remote/";

  /** Creates a new JmxModuleScanner. */
  public JmxModuleScanner() {
    log.debug("Initialized JmxModuleScanner with {} patterns", JMX_REMOTE_CLASSES.size());
  }

  /**
   * Scans multiple JARs in parallel for remote JMX API usage.
   *
   * <p>This method scans ALL provided JARs, including application code and dependencies. This is
   * important because libraries like Spring Boot Actuator, Jolokia, and management frameworks
   * internally use JMX remote APIs, and their usage must be detected.
   *
   * @param jars list of JAR paths to scan (application + dependencies)
   * @return detection result with required modules and detected patterns
   */
  public JmxDetectionResult scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return JmxDetectionResult.empty();
    }

    log.debug("Scanning {} JAR(s) for JMX remote patterns...", jars.size());

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
          log.warn("Failed to get JMX scan result: {}", e.getMessage());
        }
      }
    }

    // Determine required modules based on detected patterns
    Set<String> requiredModules = new TreeSet<>();
    if (!detectedPatterns.isEmpty()) {
      requiredModules.add("java.management.rmi");
      log.info(
          "JMX detection: Found remote JMX patterns in {} JAR(s), adding java.management.rmi",
          detectedInJars.size());
      if (log.isDebugEnabled()) {
        log.debug("JARs with JMX remote usage: {}", detectedInJars);
      }
    } else {
      log.debug("JMX detection: No remote JMX patterns found");
    }

    return new JmxDetectionResult(
        requiredModules, new TreeSet<>(detectedPatterns), new LinkedHashSet<>(detectedInJars));
  }

  /**
   * Scans a single JAR file for remote JMX API usage.
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
   * Scans a single class from an input stream for remote JMX API usage.
   *
   * @param classInputStream input stream containing class bytes
   * @return set of detected JMX remote patterns
   */
  Set<String> scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    JmxPatternVisitor visitor = new JmxPatternVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return visitor.getDetectedPatterns();
  }

  /**
   * Checks if an internal class name matches any JMX remote pattern.
   *
   * @param internalName class name in internal format (e.g.,
   *     "javax/management/remote/JMXConnector")
   * @return true if this is a JMX remote API class
   */
  boolean matchesPattern(String internalName) {
    if (internalName == null || internalName.isEmpty()) {
      return false;
    }

    // Check exact matches first
    if (JMX_REMOTE_CLASSES.contains(internalName)) {
      return true;
    }

    // Check package prefix for broader coverage
    return internalName.startsWith(JMX_REMOTE_PACKAGE);
  }

  /** Internal record to hold per-JAR scan results. */
  record JarScanResult(String jarName, Set<String> patterns) {}

  /** ASM ClassVisitor that detects remote JMX API usage patterns. */
  private class JmxPatternVisitor extends ClassVisitor {

    private final Set<String> detectedPatterns = new LinkedHashSet<>();

    JmxPatternVisitor() {
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

      return new JmxMethodVisitor();
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

    /** Inner method visitor to check method body instructions. */
    private class JmxMethodVisitor extends MethodVisitor {

      JmxMethodVisitor() {
        super(Opcodes.ASM9);
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        checkTypeReference(owner);
        checkDescriptor(descriptor);
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
