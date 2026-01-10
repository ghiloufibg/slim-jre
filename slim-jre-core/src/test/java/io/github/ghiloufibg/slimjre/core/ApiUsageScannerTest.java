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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Tests for ApiUsageScanner. */
class ApiUsageScannerTest {

  private ApiUsageScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new ApiUsageScanner();
  }

  @Test
  void shouldMapJavaSqlPackage() {
    assertThat(scanner.mapToModule("java/sql/Driver")).isEqualTo("java.sql");
    assertThat(scanner.mapToModule("java/sql/Connection")).isEqualTo("java.sql");
    assertThat(scanner.mapToModule("javax/sql/DataSource")).isEqualTo("java.sql");
  }

  @Test
  void shouldMapJavaLoggingPackage() {
    assertThat(scanner.mapToModule("java/util/logging/Logger")).isEqualTo("java.logging");
    assertThat(scanner.mapToModule("java/util/logging/Handler")).isEqualTo("java.logging");
  }

  @Test
  void shouldMapJavaNamingPackage() {
    assertThat(scanner.mapToModule("javax/naming/InitialContext")).isEqualTo("java.naming");
    assertThat(scanner.mapToModule("javax/naming/Context")).isEqualTo("java.naming");
  }

  @Test
  void shouldMapJavaXmlPackage() {
    assertThat(scanner.mapToModule("javax/xml/parsers/SAXParser")).isEqualTo("java.xml");
    assertThat(scanner.mapToModule("org/xml/sax/SAXException")).isEqualTo("java.xml");
    assertThat(scanner.mapToModule("org/w3c/dom/Document")).isEqualTo("java.xml");
  }

  @Test
  void shouldMapJavaDesktopPackage() {
    assertThat(scanner.mapToModule("java/awt/Frame")).isEqualTo("java.desktop");
    assertThat(scanner.mapToModule("javax/swing/JFrame")).isEqualTo("java.desktop");
    assertThat(scanner.mapToModule("javax/imageio/ImageIO")).isEqualTo("java.desktop");
  }

  @Test
  void shouldMapJavaNetHttpPackage() {
    assertThat(scanner.mapToModule("java/net/http/HttpClient")).isEqualTo("java.net.http");
    assertThat(scanner.mapToModule("java/net/http/HttpRequest")).isEqualTo("java.net.http");
  }

  @Test
  void shouldMapJavaManagementPackage() {
    assertThat(scanner.mapToModule("java/lang/management/ManagementFactory"))
        .isEqualTo("java.management");
    assertThat(scanner.mapToModule("javax/management/MBeanServer")).isEqualTo("java.management");
  }

  @Test
  void shouldMapJavaRmiPackage() {
    assertThat(scanner.mapToModule("java/rmi/Remote")).isEqualTo("java.rmi");
    assertThat(scanner.mapToModule("javax/rmi/ssl/SslRMIClientSocketFactory"))
        .isEqualTo("java.rmi");
  }

  @Test
  void shouldMapJavaScriptingPackage() {
    assertThat(scanner.mapToModule("javax/script/ScriptEngine")).isEqualTo("java.scripting");
    assertThat(scanner.mapToModule("javax/script/ScriptEngineManager")).isEqualTo("java.scripting");
  }

  @Test
  void shouldMapJavaPrefsPackage() {
    assertThat(scanner.mapToModule("java/util/prefs/Preferences")).isEqualTo("java.prefs");
  }

  @Test
  void shouldMapJdkUnsupportedPackage() {
    assertThat(scanner.mapToModule("sun/misc/Unsafe")).isEqualTo("jdk.unsupported");
    assertThat(scanner.mapToModule("sun/reflect/ReflectionFactory")).isEqualTo("jdk.unsupported");
  }

  @Test
  void shouldReturnNullForUnknownPackage() {
    assertThat(scanner.mapToModule("com/example/MyClass")).isNull();
    assertThat(scanner.mapToModule("org/apache/logging/Logger")).isNull();
  }

  @Test
  void shouldReturnNullForJavaBasePackages() {
    // java.base packages should not be in the mapping since java.base is always included
    assertThat(scanner.mapToModule("java/lang/String")).isNull();
    assertThat(scanner.mapToModule("java/util/List")).isNull();
    assertThat(scanner.mapToModule("java/io/InputStream")).isNull();
  }

  @Test
  void shouldDetectMethodCallToLoggingApi() throws IOException {
    byte[] classBytes = createClassWithLoggingUsage();

    Set<String> modules = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(modules).contains("java.logging");
  }

  @Test
  void shouldDetectFieldTypeFromDesktopApi() throws IOException {
    byte[] classBytes = createClassWithAwtField();

    Set<String> modules = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(modules).contains("java.desktop");
  }

  @Test
  void shouldDetectNewInstanceOfDesktopClass() throws IOException {
    byte[] classBytes = createClassWithNewAwtInstance();

    Set<String> modules = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(modules).contains("java.desktop");
  }

  @Test
  void shouldDetectMethodReturnType() throws IOException {
    byte[] classBytes = createClassWithSqlReturnType();

    Set<String> modules = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(modules).contains("java.sql");
  }

  @Test
  void shouldDetectMultipleModules() throws IOException {
    byte[] classBytes = createClassWithMultipleApiUsage();

    Set<String> modules = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(modules).contains("java.logging", "java.sql");
  }

  @Test
  void shouldScanJar() throws IOException {
    byte[] classBytes = createClassWithLoggingUsage();
    Path jar = createJarWithClass(tempDir, "logging.jar", "com/example/LoggingUser", classBytes);

    Set<String> modules = scanner.scanJar(jar);

    assertThat(modules).contains("java.logging");
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    byte[] class1 = createClassWithLoggingUsage();
    byte[] class2 = createClassWithNewAwtInstance();

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);

    Set<String> modules = scanner.scanJarsParallel(List.of(jar1, jar2));

    assertThat(modules).contains("java.logging", "java.desktop");
  }

  @Test
  void shouldHandleEmptyJarList() {
    Set<String> modules = scanner.scanJarsParallel(List.of());
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldHandleNullJarList() {
    Set<String> modules = scanner.scanJarsParallel(null);
    assertThat(modules).isEmpty();
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    Set<String> modules = scanner.scanJar(nonExistent);

    assertThat(modules).isEmpty();
  }

  /** Creates bytecode that uses java.util.logging.Logger. */
  private byte[] createClassWithLoggingUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/LoggingTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "log", "()V", null, null);
    mv.visitCode();

    // Logger.getLogger("test")
    mv.visitLdcInsn("test");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/logging/Logger",
        "getLogger",
        "(Ljava/lang/String;)Ljava/util/logging/Logger;",
        false);
    mv.visitInsn(Opcodes.POP);
    mv.visitInsn(Opcodes.RETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a java.awt.Frame field. */
  private byte[] createClassWithAwtField() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/AwtFieldTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private Frame frame;
    cw.visitField(Opcodes.ACC_PRIVATE, "frame", "Ljava/awt/Frame;", null, null).visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that creates a new java.awt.Frame instance. */
  private byte[] createClassWithNewAwtInstance() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/NewAwtTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createFrame",
            "()Ljava/lang/Object;",
            null,
            null);
    mv.visitCode();

    // new Frame()
    mv.visitTypeInsn(Opcodes.NEW, "java/awt/Frame");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/awt/Frame", "<init>", "()V", false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(2, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a method returning java.sql.Connection. */
  private byte[] createClassWithSqlReturnType() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/SqlReturnTypeTestClass",
        null,
        "java/lang/Object",
        null);

    // Method: public Connection getConnection()
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC, "getConnection", "()Ljava/sql/Connection;", null, null);
    mv.visitCode();
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses both logging and SQL APIs. */
  private byte[] createClassWithMultipleApiUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/MultiApiTestClass",
        null,
        "java/lang/Object",
        null);

    // Add field: private Logger logger;
    cw.visitField(Opcodes.ACC_PRIVATE, "logger", "Ljava/util/logging/Logger;", null, null)
        .visitEnd();

    // Method: public Connection getConnection()
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC, "getConnection", "()Ljava/sql/Connection;", null, null);
    mv.visitCode();
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(1, 1);
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
}
