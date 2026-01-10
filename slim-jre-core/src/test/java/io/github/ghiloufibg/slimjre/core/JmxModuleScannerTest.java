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

/** Tests for JmxModuleScanner. */
class JmxModuleScannerTest {

  private JmxModuleScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new JmxModuleScanner();
  }

  // ==================== Pattern Matching Tests ====================

  @Test
  void shouldMatchJmxConnectorFactoryPattern() {
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnectorFactory")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnector")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnectorServer")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnectorServerFactory"))
        .isTrue();
  }

  @Test
  void shouldMatchJmxServiceUrlPattern() {
    assertThat(scanner.matchesPattern("javax/management/remote/JMXServiceURL")).isTrue();
  }

  @Test
  void shouldMatchJmxProviderPatterns() {
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnectorProvider")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/JMXConnectorServerProvider"))
        .isTrue();
  }

  @Test
  void shouldMatchJmxAuthenticationPatterns() {
    assertThat(scanner.matchesPattern("javax/management/remote/JMXAuthenticator")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/JMXPrincipal")).isTrue();
  }

  @Test
  void shouldMatchJmxRmiPatterns() {
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/RMIConnector")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/RMIConnectorServer")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/RMIConnection")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/RMIServer")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/RMIServerImpl")).isTrue();
  }

  @Test
  void shouldMatchJmxRemotePackagePrefix() {
    // Should match any class in javax/management/remote/ package
    assertThat(scanner.matchesPattern("javax/management/remote/CustomRemoteClass")).isTrue();
    assertThat(scanner.matchesPattern("javax/management/remote/rmi/CustomRmiClass")).isTrue();
  }

  @Test
  void shouldNotMatchLocalJmxPatterns() {
    // Local JMX (javax.management) does not require java.management.rmi
    assertThat(scanner.matchesPattern("javax/management/MBeanServer")).isFalse();
    assertThat(scanner.matchesPattern("javax/management/ObjectName")).isFalse();
    assertThat(scanner.matchesPattern("javax/management/MBeanInfo")).isFalse();
    assertThat(scanner.matchesPattern("javax/management/DynamicMBean")).isFalse();
  }

  @Test
  void shouldNotMatchNonJmxPatterns() {
    assertThat(scanner.matchesPattern("java/lang/String")).isFalse();
    assertThat(scanner.matchesPattern("java/util/List")).isFalse();
    assertThat(scanner.matchesPattern("com/example/MyClass")).isFalse();
    assertThat(scanner.matchesPattern("java/io/InputStream")).isFalse();
    assertThat(scanner.matchesPattern("javax/net/ssl/SSLContext")).isFalse();
  }

  @Test
  void shouldHandleNullAndEmptyPatterns() {
    assertThat(scanner.matchesPattern(null)).isFalse();
    assertThat(scanner.matchesPattern("")).isFalse();
  }

  // ==================== Bytecode Scanning Tests ====================

  @Test
  void shouldDetectJmxConnectorFactoryUsage() throws IOException {
    byte[] classBytes = createClassWithJmxConnectorFactoryUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/management/remote/JMXConnectorFactory");
  }

  @Test
  void shouldDetectJmxServiceUrlUsage() throws IOException {
    byte[] classBytes = createClassWithJmxServiceUrlUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/management/remote/JMXServiceURL");
  }

  @Test
  void shouldDetectJmxConnectorServerFactoryUsage() throws IOException {
    byte[] classBytes = createClassWithJmxConnectorServerFactoryUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/management/remote/JMXConnectorServerFactory");
  }

  @Test
  void shouldDetectJmxConnectorField() throws IOException {
    byte[] classBytes = createClassWithJmxConnectorField();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/management/remote/JMXConnector");
  }

  @Test
  void shouldDetectRmiConnectorUsage() throws IOException {
    byte[] classBytes = createClassWithRmiConnectorUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("javax/management/remote/rmi/RMIConnector");
  }

  @Test
  void shouldDetectMultipleJmxPatterns() throws IOException {
    byte[] classBytes = createClassWithMultipleJmxUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns)
        .contains("javax/management/remote/JMXConnector", "javax/management/remote/JMXServiceURL");
  }

  @Test
  void shouldReturnEmptyForNonJmxClass() throws IOException {
    byte[] classBytes = createSimpleClass();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).isEmpty();
  }

  @Test
  void shouldReturnEmptyForLocalJmxOnlyClass() throws IOException {
    byte[] classBytes = createClassWithLocalJmxOnly();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).isEmpty();
  }

  // ==================== JAR Scanning Tests ====================

  @Test
  void shouldScanJarForJmxPatterns() throws IOException {
    byte[] classBytes = createClassWithJmxConnectorFactoryUsage();
    Path jar = createJarWithClass(tempDir, "jmx.jar", "com/example/JmxUser", classBytes);

    JmxModuleScanner.JarScanResult result = scanner.scanJar(jar);

    assertThat(result.jarName()).isEqualTo("jmx.jar");
    assertThat(result.patterns()).contains("javax/management/remote/JMXConnectorFactory");
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    byte[] class1 = createClassWithJmxConnectorFactoryUsage();
    byte[] class2 = createClassWithJmxServiceUrlUsage();

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);

    JmxDetectionResult result = scanner.scanJarsParallel(List.of(jar1, jar2));

    assertThat(result.isRequired()).isTrue();
    assertThat(result.requiredModules()).contains("java.management.rmi");
    assertThat(result.detectedInJars()).containsExactlyInAnyOrder("jar1.jar", "jar2.jar");
    assertThat(result.detectedPatterns())
        .contains(
            "javax/management/remote/JMXConnectorFactory", "javax/management/remote/JMXServiceURL");
  }

  @Test
  void shouldReturnEmptyResultForNonJmxJars() throws IOException {
    byte[] classBytes = createSimpleClass();
    Path jar = createJarWithClass(tempDir, "simple.jar", "com/example/Simple", classBytes);

    JmxDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldReturnEmptyResultForLocalJmxOnlyJars() throws IOException {
    byte[] classBytes = createClassWithLocalJmxOnly();
    Path jar = createJarWithClass(tempDir, "local-jmx.jar", "com/example/LocalJmx", classBytes);

    JmxDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
  }

  @Test
  void shouldHandleEmptyJarList() {
    JmxDetectionResult result = scanner.scanJarsParallel(List.of());

    assertThat(result).isEqualTo(JmxDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNullJarList() {
    JmxDetectionResult result = scanner.scanJarsParallel(null);

    assertThat(result).isEqualTo(JmxDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    JmxModuleScanner.JarScanResult result = scanner.scanJar(nonExistent);

    assertThat(result.patterns()).isEmpty();
  }

  @Test
  void shouldSkipModuleInfoClass() throws IOException {
    // module-info.class shouldn't be scanned as it doesn't contain API usage
    Path jar = createJarWithModuleInfo(tempDir, "modular.jar");

    JmxModuleScanner.JarScanResult result = scanner.scanJar(jar);

    // Should complete without errors and have no patterns from module-info
    assertThat(result.patterns()).isEmpty();
  }

  // ==================== JmxDetectionResult Tests ====================

  @Test
  void shouldCreateEmptyResult() {
    JmxDetectionResult result = JmxDetectionResult.empty();

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldGenerateCorrectSummary() throws IOException {
    byte[] classBytes = createClassWithJmxConnectorFactoryUsage();
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/App", classBytes);

    JmxDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    String summary = result.summary();
    assertThat(summary).contains("java.management.rmi");
    assertThat(summary).contains("app.jar");
  }

  @Test
  void shouldGenerateNoJmxSummaryForEmptyResult() {
    JmxDetectionResult result = JmxDetectionResult.empty();

    String summary = result.summary();
    assertThat(summary).contains("No remote JMX patterns detected");
  }

  // ==================== Helper Methods ====================

  /** Creates bytecode that uses JMXConnectorFactory.connect(). */
  private byte[] createClassWithJmxConnectorFactoryUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/JmxConnectorFactoryTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "connect",
            "(Ljavax/management/remote/JMXServiceURL;)Ljavax/management/remote/JMXConnector;",
            null,
            new String[] {"java/io/IOException"});
    mv.visitCode();

    // JMXConnectorFactory.connect(url)
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "javax/management/remote/JMXConnectorFactory",
        "connect",
        "(Ljavax/management/remote/JMXServiceURL;)Ljavax/management/remote/JMXConnector;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses new JMXServiceURL(). */
  private byte[] createClassWithJmxServiceUrlUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/JmxServiceUrlTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createUrl",
            "(Ljava/lang/String;)Ljavax/management/remote/JMXServiceURL;",
            null,
            new String[] {"java/net/MalformedURLException"});
    mv.visitCode();

    // new JMXServiceURL(urlString)
    mv.visitTypeInsn(Opcodes.NEW, "javax/management/remote/JMXServiceURL");
    mv.visitInsn(Opcodes.DUP);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "javax/management/remote/JMXServiceURL",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(3, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses JMXConnectorServerFactory.newJMXConnectorServer(). */
  private byte[] createClassWithJmxConnectorServerFactoryUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/JmxConnectorServerFactoryTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createServer",
            "()Ljavax/management/remote/JMXConnectorServer;",
            null,
            new String[] {"java/io/IOException"});
    mv.visitCode();

    // JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbeanServer)
    mv.visitInsn(Opcodes.ACONST_NULL); // url
    mv.visitInsn(Opcodes.ACONST_NULL); // env
    mv.visitInsn(Opcodes.ACONST_NULL); // mbeanServer
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "javax/management/remote/JMXConnectorServerFactory",
        "newJMXConnectorServer",
        "(Ljavax/management/remote/JMXServiceURL;Ljava/util/Map;Ljavax/management/MBeanServer;)Ljavax/management/remote/JMXConnectorServer;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(3, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a JMXConnector field. */
  private byte[] createClassWithJmxConnectorField() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/JmxConnectorFieldTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private JMXConnector connector;
    FieldVisitor fv =
        cw.visitField(
            Opcodes.ACC_PRIVATE, "connector", "Ljavax/management/remote/JMXConnector;", null, null);
    fv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses RMIConnector. */
  private byte[] createClassWithRmiConnectorUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/RmiConnectorTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createConnector",
            "()Ljavax/management/remote/rmi/RMIConnector;",
            null,
            null);
    mv.visitCode();

    // new RMIConnector(rmiServer, env)
    mv.visitTypeInsn(Opcodes.NEW, "javax/management/remote/rmi/RMIConnector");
    mv.visitInsn(Opcodes.DUP);
    mv.visitInsn(Opcodes.ACONST_NULL); // rmiServer
    mv.visitInsn(Opcodes.ACONST_NULL); // env
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "javax/management/remote/rmi/RMIConnector",
        "<init>",
        "(Ljavax/management/remote/rmi/RMIServer;Ljava/util/Map;)V",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(4, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses multiple JMX remote APIs. */
  private byte[] createClassWithMultipleJmxUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/MultiJmxTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private JMXConnector connector;
    cw.visitField(
            Opcodes.ACC_PRIVATE, "connector", "Ljavax/management/remote/JMXConnector;", null, null)
        .visitEnd();

    // Method with JMXServiceURL return type
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "getServiceUrl",
            "()Ljavax/management/remote/JMXServiceURL;",
            null,
            null);
    mv.visitCode();
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates a simple class with no JMX usage. */
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

  /** Creates a class that uses only local JMX (not remote). */
  private byte[] createClassWithLocalJmxOnly() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/LocalJmxTestClass",
        null,
        "java/lang/Object",
        null);

    // Method that uses MBeanServer (local JMX, not remote)
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getMBeanServer",
            "()Ljavax/management/MBeanServer;",
            null,
            null);
    mv.visitCode();

    // ManagementFactory.getPlatformMBeanServer()
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/management/ManagementFactory",
        "getPlatformMBeanServer",
        "()Ljavax/management/MBeanServer;",
        false);
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
