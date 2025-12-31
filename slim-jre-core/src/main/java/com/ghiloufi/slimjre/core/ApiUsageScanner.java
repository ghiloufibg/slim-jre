package com.ghiloufi.slimjre.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Scans bytecode to detect JDK API usage by analyzing type references in method calls, field
 * accesses, and type declarations. Maps detected JDK packages to their containing modules.
 *
 * <p>This scanner detects module requirements by analyzing:
 *
 * <ul>
 *   <li>Method invocations on JDK classes
 *   <li>Field accesses to JDK classes
 *   <li>Type references in method signatures (parameters, return types, exceptions)
 *   <li>Type instantiation (NEW instructions)
 *   <li>Type casts and instanceof checks
 * </ul>
 *
 * <p>Example detections:
 *
 * <pre>
 *   java.util.logging.Logger.getLogger("name")  → java.logging
 *   new java.awt.Frame()                        → java.desktop
 *   javax.naming.InitialContext ctx = ...       → java.naming
 * </pre>
 */
public class ApiUsageScanner {

  private static final Logger log = LoggerFactory.getLogger(ApiUsageScanner.class);

  /**
   * Package → Module mapping for JDK packages that are NOT in java.base. Packages in java.base are
   * excluded since that module is always included. This mapping is built from JDK module
   * definitions.
   */
  private static final Map<String, String> PACKAGE_TO_MODULE =
      Map.ofEntries(
          // java.sql module
          Map.entry("java/sql", "java.sql"),
          Map.entry("javax/sql", "java.sql"),

          // java.logging module
          Map.entry("java/util/logging", "java.logging"),

          // java.naming module
          Map.entry("javax/naming", "java.naming"),

          // java.xml module
          Map.entry("javax/xml", "java.xml"),
          Map.entry("org/xml/sax", "java.xml"),
          Map.entry("org/w3c/dom", "java.xml"),

          // java.desktop module
          Map.entry("java/awt", "java.desktop"),
          Map.entry("javax/swing", "java.desktop"),
          Map.entry("javax/imageio", "java.desktop"),
          Map.entry("javax/print", "java.desktop"),
          Map.entry("javax/sound", "java.desktop"),
          Map.entry("java/beans", "java.desktop"),
          Map.entry("javax/accessibility", "java.desktop"),

          // java.net.http module
          Map.entry("java/net/http", "java.net.http"),

          // java.compiler module
          Map.entry("javax/annotation/processing", "java.compiler"),
          Map.entry("javax/lang/model", "java.compiler"),
          Map.entry("javax/tools", "java.compiler"),

          // java.instrument module
          Map.entry("java/lang/instrument", "java.instrument"),

          // java.management module
          Map.entry("java/lang/management", "java.management"),
          Map.entry("javax/management", "java.management"),

          // java.rmi module
          Map.entry("java/rmi", "java.rmi"),
          Map.entry("javax/rmi", "java.rmi"),

          // java.scripting module
          Map.entry("javax/script", "java.scripting"),

          // java.security.jgss module
          Map.entry("javax/security/auth/kerberos", "java.security.jgss"),
          Map.entry("org/ietf/jgss", "java.security.jgss"),

          // java.security.sasl module
          Map.entry("javax/security/sasl", "java.security.sasl"),

          // java.smartcardio module
          Map.entry("javax/smartcardio", "java.smartcardio"),

          // java.prefs module
          Map.entry("java/util/prefs", "java.prefs"),

          // java.transaction.xa module
          Map.entry("javax/transaction/xa", "java.transaction.xa"),

          // jdk.httpserver module
          Map.entry("com/sun/net/httpserver", "jdk.httpserver"),

          // jdk.jsobject module
          Map.entry("netscape/javascript", "jdk.jsobject"),

          // jdk.unsupported module
          Map.entry("sun/misc", "jdk.unsupported"),
          Map.entry("sun/reflect", "jdk.unsupported"),

          // jdk.crypto.ec module
          Map.entry("sun/security/ec", "jdk.crypto.ec"),

          // jdk.crypto.cryptoki module
          Map.entry("sun/security/pkcs11", "jdk.crypto.cryptoki"),

          // jdk.attach module
          Map.entry("com/sun/tools/attach", "jdk.attach"),

          // jdk.jdi module
          Map.entry("com/sun/jdi", "jdk.jdi"),

          // jdk.jconsole module
          Map.entry("com/sun/tools/jconsole", "jdk.jconsole"),

          // jdk.management module
          Map.entry("com/sun/management", "jdk.management"),

          // jdk.security.auth module
          Map.entry("com/sun/security/auth", "jdk.security.auth"),

          // jdk.security.jgss module
          Map.entry("com/sun/security/jgss", "jdk.security.jgss"),

          // jdk.xml.dom module
          Map.entry("org/w3c/dom/css", "jdk.xml.dom"),
          Map.entry("org/w3c/dom/html", "jdk.xml.dom"),
          Map.entry("org/w3c/dom/stylesheets", "jdk.xml.dom"),
          Map.entry("org/w3c/dom/xpath", "jdk.xml.dom"));

  /** Creates a new ApiUsageScanner. */
  public ApiUsageScanner() {
    log.debug("Initialized ApiUsageScanner with {} package mappings", PACKAGE_TO_MODULE.size());
  }

  /**
   * Scans a JAR file for JDK API usage and returns required modules.
   *
   * @param jarPath path to the JAR file to scan
   * @return set of JDK module names required by API usage
   */
  public Set<String> scanJar(Path jarPath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    if (!Files.exists(jarPath)) {
      log.warn("JAR file does not exist: {}", jarPath);
      return Set.of();
    }

    Set<String> detectedModules = new HashSet<>();

    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
          try (InputStream is = jar.getInputStream(entry);
              BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
            detectedModules.addAll(scanClass(bis));
          } catch (IOException e) {
            log.trace("Failed to scan class {}: {}", entry.getName(), e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to scan JAR {}: {}", jarPath, e.getMessage());
      return Set.of();
    }

    return detectedModules;
  }

  /**
   * Scans multiple JARs in parallel using virtual threads and returns combined required modules.
   *
   * @param jars list of JAR paths to scan
   * @return set of JDK module names required by API usage across all JARs
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
   * Scans a single class from an input stream for JDK API usage.
   *
   * @param classInputStream input stream containing class bytes
   * @return set of JDK module names detected via API usage
   */
  Set<String> scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    ApiUsageVisitor visitor = new ApiUsageVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return visitor.getDetectedModules();
  }

  /**
   * Maps an internal class name to its containing JDK module.
   *
   * @param internalName class name in internal format (e.g., "java/sql/Driver")
   * @return module name or null if not a mapped JDK package
   */
  String mapToModule(String internalName) {
    if (internalName == null || internalName.isEmpty()) {
      return null;
    }

    // Try exact package matches first, then progressively shorter prefixes
    for (Map.Entry<String, String> entry : PACKAGE_TO_MODULE.entrySet()) {
      if (internalName.startsWith(entry.getKey() + "/") || internalName.equals(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** ASM ClassVisitor that detects JDK API usage patterns. */
  private class ApiUsageVisitor extends ClassVisitor {

    private final Set<String> detectedModules = new HashSet<>();

    ApiUsageVisitor() {
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
      // Check field type
      checkDescriptor(descriptor);
      return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      // Check method parameter and return types
      checkDescriptor(descriptor);

      // Check declared exceptions
      if (exceptions != null) {
        for (String exception : exceptions) {
          checkTypeReference(exception);
        }
      }

      return new ApiUsageMethodVisitor();
    }

    Set<String> getDetectedModules() {
      return new TreeSet<>(detectedModules);
    }

    private void checkTypeReference(String internalName) {
      String module = mapToModule(internalName);
      if (module != null) {
        detectedModules.add(module);
      }
    }

    private void checkDescriptor(String descriptor) {
      if (descriptor == null) {
        return;
      }

      // Parse type descriptors to extract class references
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
    private class ApiUsageMethodVisitor extends MethodVisitor {

      ApiUsageMethodVisitor() {
        super(Opcodes.ASM9);
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Check the class being called
        checkTypeReference(owner);

        // Check parameter/return types
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
