package io.github.ghiloufibg.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Tests for CryptoModuleScanner. */
class CryptoModuleScannerTest {

  private CryptoModuleScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new CryptoModuleScanner();
  }

  // ==================== Pattern Matching Tests ====================

  @Test
  void shouldMatchSslContextPattern() {
    assertThat(scanner.matchesPattern("javax/net/ssl/SSLContext")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/SSLSocket")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/SSLSocketFactory")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/SSLEngine")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/HttpsURLConnection")).isTrue();
  }

  @Test
  void shouldMatchTrustManagerPatterns() {
    assertThat(scanner.matchesPattern("javax/net/ssl/TrustManager")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/X509TrustManager")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/TrustManagerFactory")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/KeyManager")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/X509KeyManager")).isTrue();
    assertThat(scanner.matchesPattern("javax/net/ssl/KeyManagerFactory")).isTrue();
  }

  @Test
  void shouldMatchHttpClientPatterns() {
    assertThat(scanner.matchesPattern("java/net/http/HttpClient")).isTrue();
    assertThat(scanner.matchesPattern("java/net/http/HttpRequest")).isTrue();
    assertThat(scanner.matchesPattern("java/net/http/HttpResponse")).isTrue();
  }

  @Test
  void shouldMatchSecurityPatterns() {
    assertThat(scanner.matchesPattern("java/security/KeyStore")).isTrue();
    assertThat(scanner.matchesPattern("java/security/cert/Certificate")).isTrue();
    assertThat(scanner.matchesPattern("java/security/cert/X509Certificate")).isTrue();
    assertThat(scanner.matchesPattern("java/security/cert/CertificateFactory")).isTrue();
  }

  @Test
  void shouldMatchCryptoPatterns() {
    assertThat(scanner.matchesPattern("javax/crypto/Cipher")).isTrue();
    assertThat(scanner.matchesPattern("javax/crypto/KeyGenerator")).isTrue();
    assertThat(scanner.matchesPattern("javax/crypto/SecretKey")).isTrue();
    assertThat(scanner.matchesPattern("javax/crypto/Mac")).isTrue();
    assertThat(scanner.matchesPattern("javax/crypto/KeyAgreement")).isTrue();
  }

  @Test
  void shouldMatchSslPackagePrefix() {
    // Should match any class in javax/net/ssl/ package
    assertThat(scanner.matchesPattern("javax/net/ssl/CustomSSLClass")).isTrue();
    assertThat(scanner.matchesPattern("javax/crypto/CustomCryptoClass")).isTrue();
    assertThat(scanner.matchesPattern("java/net/http/SomeOtherClass")).isTrue();
  }

  @Test
  void shouldNotMatchNonCryptoPatterns() {
    assertThat(scanner.matchesPattern("java/lang/String")).isFalse();
    assertThat(scanner.matchesPattern("java/util/List")).isFalse();
    assertThat(scanner.matchesPattern("com/example/MyClass")).isFalse();
    assertThat(scanner.matchesPattern("java/io/InputStream")).isFalse();
  }

  @Test
  void shouldHandleNullAndEmptyPatterns() {
    assertThat(scanner.matchesPattern(null)).isFalse();
    assertThat(scanner.matchesPattern("")).isFalse();
  }

  // ==================== Bytecode Scanning Tests ====================

  @Test
  void shouldDetectSslContextUsage() throws IOException {
    byte[] classBytes = createClassWithSslContextUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/net/ssl/SSLContext");
  }

  @Test
  void shouldDetectHttpClientUsage() throws IOException {
    byte[] classBytes = createClassWithHttpClientUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("java/net/http/HttpClient");
  }

  @Test
  void shouldDetectSslFieldType() throws IOException {
    byte[] classBytes = createClassWithSslField();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/net/ssl/SSLSocketFactory");
  }

  @Test
  void shouldDetectCipherUsage() throws IOException {
    byte[] classBytes = createClassWithCipherUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/crypto/Cipher");
  }

  @Test
  void shouldDetectKeyStoreUsage() throws IOException {
    byte[] classBytes = createClassWithKeyStoreUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("java/security/KeyStore");
  }

  @Test
  void shouldDetectMultipleCryptoPatterns() throws IOException {
    byte[] classBytes = createClassWithMultipleCryptoUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/net/ssl/SSLContext", "java/net/http/HttpClient");
  }

  @Test
  void shouldReturnEmptyForNonCryptoClass() throws IOException {
    byte[] classBytes = createSimpleClass();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).isEmpty();
  }

  // ==================== JAR Scanning Tests ====================

  @Test
  void shouldScanJarForCryptoPatterns() throws IOException {
    byte[] classBytes = createClassWithSslContextUsage();
    Path jar = createJarWithClass(tempDir, "ssl.jar", "com/example/SslUser", classBytes);

    CryptoModuleScanner.JarScanResult result = scanner.scanJar(jar);

    assertThat(result.jarName()).isEqualTo("ssl.jar");
    assertThat(result.patterns()).contains("javax/net/ssl/SSLContext");
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    byte[] class1 = createClassWithSslContextUsage();
    byte[] class2 = createClassWithHttpClientUsage();

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);

    CryptoDetectionResult result = scanner.scanJarsParallel(List.of(jar1, jar2));

    assertThat(result.isRequired()).isTrue();
    assertThat(result.requiredModules()).contains("jdk.crypto.ec");
    assertThat(result.detectedInJars()).containsExactlyInAnyOrder("jar1.jar", "jar2.jar");
    assertThat(result.detectedPatterns())
        .contains("javax/net/ssl/SSLContext", "java/net/http/HttpClient");
  }

  @Test
  void shouldReturnEmptyResultForNonCryptoJars() throws IOException {
    byte[] classBytes = createSimpleClass();
    Path jar = createJarWithClass(tempDir, "simple.jar", "com/example/Simple", classBytes);

    CryptoDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldHandleEmptyJarList() {
    CryptoDetectionResult result = scanner.scanJarsParallel(List.of());

    assertThat(result).isEqualTo(CryptoDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNullJarList() {
    CryptoDetectionResult result = scanner.scanJarsParallel(null);

    assertThat(result).isEqualTo(CryptoDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    CryptoModuleScanner.JarScanResult result = scanner.scanJar(nonExistent);

    assertThat(result.patterns()).isEmpty();
  }

  @Test
  void shouldSkipModuleInfoClass() throws IOException {
    // module-info.class shouldn't be scanned as it doesn't contain API usage
    Path jar = createJarWithModuleInfo(tempDir, "modular.jar");

    CryptoModuleScanner.JarScanResult result = scanner.scanJar(jar);

    // Should complete without errors and have no patterns from module-info
    assertThat(result.patterns()).isEmpty();
  }

  // ==================== CryptoDetectionResult Tests ====================

  @Test
  void shouldCreateEmptyResult() {
    CryptoDetectionResult result = CryptoDetectionResult.empty();

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldGenerateCorrectSummary() throws IOException {
    byte[] classBytes = createClassWithSslContextUsage();
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/App", classBytes);

    CryptoDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    String summary = result.summary();
    assertThat(summary).contains("jdk.crypto.ec");
    assertThat(summary).contains("app.jar");
  }

  @Test
  void shouldGenerateNoSslSummaryForEmptyResult() {
    CryptoDetectionResult result = CryptoDetectionResult.empty();

    String summary = result.summary();
    assertThat(summary).contains("No SSL/TLS patterns detected");
  }

  // ==================== Helper Methods ====================

  /** Creates bytecode that uses SSLContext.getInstance(). */
  private byte[] createClassWithSslContextUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/SslContextTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getContext",
            "()Ljavax/net/ssl/SSLContext;",
            null,
            new String[] {"java/security/NoSuchAlgorithmException"});
    mv.visitCode();

    // SSLContext.getInstance("TLS")
    mv.visitLdcInsn("TLS");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "javax/net/ssl/SSLContext",
        "getInstance",
        "(Ljava/lang/String;)Ljavax/net/ssl/SSLContext;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses HttpClient.newHttpClient(). */
  private byte[] createClassWithHttpClientUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/HttpClientTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getClient",
            "()Ljava/net/http/HttpClient;",
            null,
            null);
    mv.visitCode();

    // HttpClient.newHttpClient()
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/net/http/HttpClient",
        "newHttpClient",
        "()Ljava/net/http/HttpClient;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a SSLSocketFactory field. */
  private byte[] createClassWithSslField() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/SslFieldTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private SSLSocketFactory factory;
    FieldVisitor fv =
        cw.visitField(
            Opcodes.ACC_PRIVATE, "factory", "Ljavax/net/ssl/SSLSocketFactory;", null, null);
    fv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses Cipher.getInstance(). */
  private byte[] createClassWithCipherUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/CipherTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getCipher",
            "()Ljavax/crypto/Cipher;",
            null,
            new String[] {"java/security/GeneralSecurityException"});
    mv.visitCode();

    // Cipher.getInstance("AES")
    mv.visitLdcInsn("AES");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "javax/crypto/Cipher",
        "getInstance",
        "(Ljava/lang/String;)Ljavax/crypto/Cipher;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses KeyStore.getInstance(). */
  private byte[] createClassWithKeyStoreUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/KeyStoreTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getKeyStore",
            "()Ljava/security/KeyStore;",
            null,
            new String[] {"java/security/KeyStoreException"});
    mv.visitCode();

    // KeyStore.getInstance("JKS")
    mv.visitLdcInsn("JKS");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/security/KeyStore",
        "getInstance",
        "(Ljava/lang/String;)Ljava/security/KeyStore;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses multiple crypto APIs. */
  private byte[] createClassWithMultipleCryptoUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/MultiCryptoTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private SSLContext context;
    cw.visitField(Opcodes.ACC_PRIVATE, "context", "Ljavax/net/ssl/SSLContext;", null, null)
        .visitEnd();

    // Method with HttpClient return type
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC, "getClient", "()Ljava/net/http/HttpClient;", null, null);
    mv.visitCode();
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates a simple class with no crypto usage. */
  private byte[] createSimpleClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/SimpleTestClass",
        null,
        "java/lang/Object",
        null);

    // Simple method using only java.lang
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hello", "()Ljava/lang/String;", null, null);
    mv.visitCode();
    mv.visitLdcInsn("Hello World");
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates a JAR file containing a single class. */
  private Path createJarWithClass(Path dir, String jarName, String className, byte[] classBytes)
      throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      JarEntry entry = new JarEntry(className + ".class");
      jos.putNextEntry(entry);
      jos.write(classBytes);
      jos.closeEntry();
    }

    return jarPath;
  }

  /** Creates a JAR file with a module-info.class. */
  private Path createJarWithModuleInfo(Path dir, String jarName) throws IOException {
    Path jarPath = dir.resolve(jarName);

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      // Add empty module-info.class
      JarEntry entry = new JarEntry("module-info.class");
      jos.putNextEntry(entry);
      // Write minimal class file bytes (won't be parsed anyway as we skip module-info)
      jos.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 55});
      jos.closeEntry();
    }

    return jarPath;
  }
}
