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
 * Scans bytecode to detect SSL/TLS and cryptographic API usage patterns that require the {@code
 * jdk.crypto.ec} module.
 *
 * <p>This scanner addresses a fundamental limitation of jdeps: it cannot detect crypto module
 * requirements because crypto providers use internal, non-exported packages. When applications use
 * {@code javax.net.ssl.SSLContext} or {@code java.net.http.HttpClient}, the actual crypto
 * implementation in {@code sun.security.ec.*} is loaded via service providers at runtime, which
 * jdeps cannot trace.
 *
 * <p>The scanner detects:
 *
 * <ul>
 *   <li>SSL/TLS APIs: {@code javax.net.ssl.*} (SSLContext, SSLSocket, HttpsURLConnection, etc.)
 *   <li>HTTP Client: {@code java.net.http.HttpClient} (requires crypto for HTTPS)
 *   <li>Security APIs: {@code java.security.KeyStore}, certificate handling
 *   <li>Crypto APIs: {@code javax.crypto.*} (Cipher, KeyGenerator, Mac, etc.)
 * </ul>
 *
 * <p>Example detections:
 *
 * <pre>
 *   SSLContext.getInstance("TLS")           → jdk.crypto.ec
 *   HttpClient.newHttpClient()              → jdk.crypto.ec
 *   new HttpsURLConnection(...)             → jdk.crypto.ec
 * </pre>
 *
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8245027">JDK-8245027: SunEC Provider not
 *     available with jlink</a>
 */
public class CryptoModuleScanner {

  private static final Logger log = LoggerFactory.getLogger(CryptoModuleScanner.class);

  /**
   * SSL/TLS and crypto API patterns that indicate {@code jdk.crypto.ec} is required. These patterns
   * are internal class names (using '/' separator) as used in bytecode.
   */
  private static final Set<String> SSL_CRYPTO_PATTERNS =
      Set.of(
          // Core SSL/TLS APIs
          "javax/net/ssl/SSLContext",
          "javax/net/ssl/SSLSocket",
          "javax/net/ssl/SSLSocketFactory",
          "javax/net/ssl/SSLServerSocket",
          "javax/net/ssl/SSLServerSocketFactory",
          "javax/net/ssl/SSLEngine",
          "javax/net/ssl/HttpsURLConnection",
          "javax/net/ssl/TrustManager",
          "javax/net/ssl/X509TrustManager",
          "javax/net/ssl/KeyManager",
          "javax/net/ssl/X509KeyManager",
          "javax/net/ssl/TrustManagerFactory",
          "javax/net/ssl/KeyManagerFactory",
          "javax/net/ssl/SSLSession",
          "javax/net/ssl/SSLParameters",

          // Java HTTP Client (Java 11+) - requires crypto for HTTPS
          "java/net/http/HttpClient",
          "java/net/http/HttpRequest",
          "java/net/http/HttpResponse",

          // Security/Certificate APIs commonly used with SSL
          "java/security/KeyStore",
          "java/security/cert/Certificate",
          "java/security/cert/X509Certificate",
          "java/security/cert/CertificateFactory",
          "java/security/cert/CertPath",
          "java/security/cert/CertPathValidator",

          // Crypto APIs
          "javax/crypto/Cipher",
          "javax/crypto/KeyGenerator",
          "javax/crypto/SecretKey",
          "javax/crypto/SecretKeyFactory",
          "javax/crypto/Mac",
          "javax/crypto/KeyAgreement",
          "javax/crypto/spec/SecretKeySpec");

  /** Package prefixes that indicate SSL/TLS usage (for broader detection). */
  private static final Set<String> SSL_PACKAGE_PREFIXES =
      Set.of("javax/net/ssl/", "java/net/http/", "javax/crypto/");

  /** Creates a new CryptoModuleScanner. */
  public CryptoModuleScanner() {
    log.debug("Initialized CryptoModuleScanner with {} patterns", SSL_CRYPTO_PATTERNS.size());
  }

  /**
   * Scans multiple JARs in parallel for SSL/TLS and crypto API usage.
   *
   * <p>This method scans ALL provided JARs, including application code and dependencies. This is
   * important because libraries like OkHttp, Apache HttpClient, and Spring Web internally use
   * SSL/TLS APIs, and their usage must be detected.
   *
   * @param jars list of JAR paths to scan (application + dependencies)
   * @return detection result with required modules and detected patterns
   */
  public CryptoDetectionResult scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return CryptoDetectionResult.empty();
    }

    log.debug("Scanning {} JAR(s) for SSL/TLS patterns...", jars.size());

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
          log.warn("Failed to get crypto scan result: {}", e.getMessage());
        }
      }
    }

    // Determine required modules based on detected patterns
    Set<String> requiredModules = new TreeSet<>();
    if (!detectedPatterns.isEmpty()) {
      requiredModules.add("jdk.crypto.ec");
      log.info(
          "Crypto detection: Found SSL/TLS patterns in {} JAR(s), adding jdk.crypto.ec",
          detectedInJars.size());
      if (log.isDebugEnabled()) {
        log.debug("JARs with SSL/TLS usage: {}", detectedInJars);
      }
    } else {
      log.debug("Crypto detection: No SSL/TLS patterns found");
    }

    return new CryptoDetectionResult(
        requiredModules, new TreeSet<>(detectedPatterns), new LinkedHashSet<>(detectedInJars));
  }

  /**
   * Scans a single JAR file for SSL/TLS and crypto API usage.
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
   * Scans a single class from an input stream for SSL/TLS and crypto API usage.
   *
   * @param classInputStream input stream containing class bytes
   * @return set of detected SSL/TLS patterns
   */
  Set<String> scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    CryptoPatternVisitor visitor = new CryptoPatternVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return visitor.getDetectedPatterns();
  }

  /**
   * Checks if an internal class name matches any SSL/TLS or crypto pattern.
   *
   * @param internalName class name in internal format (e.g., "javax/net/ssl/SSLContext")
   * @return true if this is an SSL/TLS or crypto API class
   */
  boolean matchesPattern(String internalName) {
    if (internalName == null || internalName.isEmpty()) {
      return false;
    }

    // Check exact matches first
    if (SSL_CRYPTO_PATTERNS.contains(internalName)) {
      return true;
    }

    // Check package prefixes for broader coverage
    for (String prefix : SSL_PACKAGE_PREFIXES) {
      if (internalName.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }

  /** Internal record to hold per-JAR scan results. */
  record JarScanResult(String jarName, Set<String> patterns) {}

  /** ASM ClassVisitor that detects SSL/TLS and crypto API usage patterns. */
  private class CryptoPatternVisitor extends ClassVisitor {

    private final Set<String> detectedPatterns = new LinkedHashSet<>();

    CryptoPatternVisitor() {
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

      return new CryptoMethodVisitor();
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
    private class CryptoMethodVisitor extends MethodVisitor {

      CryptoMethodVisitor() {
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
