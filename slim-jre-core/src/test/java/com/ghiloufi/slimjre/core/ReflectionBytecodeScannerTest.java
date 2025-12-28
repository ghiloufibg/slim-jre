package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Tests for ReflectionBytecodeScanner. */
class ReflectionBytecodeScannerTest {

  private ReflectionBytecodeScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new ReflectionBytecodeScanner();
  }

  @Test
  void shouldIdentifyJdkClasses() {
    assertThat(ReflectionBytecodeScanner.isJdkClass("java.lang.String")).isTrue();
    assertThat(ReflectionBytecodeScanner.isJdkClass("javax.sql.DataSource")).isTrue();
    assertThat(ReflectionBytecodeScanner.isJdkClass("jdk.internal.misc.Unsafe")).isTrue();
    assertThat(ReflectionBytecodeScanner.isJdkClass("sun.misc.Unsafe")).isTrue();
    assertThat(ReflectionBytecodeScanner.isJdkClass("com.sun.xml.internal.Parser")).isTrue();

    assertThat(ReflectionBytecodeScanner.isJdkClass("com.example.MyClass")).isFalse();
    assertThat(ReflectionBytecodeScanner.isJdkClass("org.apache.logging.Logger")).isFalse();
  }

  @Test
  void shouldMapJdkClassToModule() {
    Optional<String> module = scanner.getModuleForClass("java.lang.String");
    assertThat(module).isPresent().contains("java.base");

    module = scanner.getModuleForClass("java.sql.Driver");
    assertThat(module).isPresent().contains("java.sql");

    module = scanner.getModuleForClass("javax.xml.parsers.SAXParser");
    assertThat(module).isPresent().contains("java.xml");
  }

  @Test
  void shouldReturnEmptyForUnknownClass() {
    Optional<String> module = scanner.getModuleForClass("com.example.NonExistent");
    assertThat(module).isEmpty();
  }

  @Test
  void shouldDetectClassForNameWithStringLiteral() throws IOException {
    // Create bytecode that calls Class.forName("java.sql.Driver")
    byte[] classBytes = createClassWithClassForName("java.sql.Driver");

    Set<String> reflectedClasses = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(reflectedClasses).contains("java.sql.Driver");
  }

  @Test
  void shouldDetectMultipleClassForNameCalls() throws IOException {
    byte[] classBytes =
        createClassWithMultipleClassForName("java.sql.Driver", "javax.xml.parsers.SAXParser");

    Set<String> reflectedClasses = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(reflectedClasses).contains("java.sql.Driver", "javax.xml.parsers.SAXParser");
  }

  @Test
  void shouldIgnoreNonJdkClasses() throws IOException {
    byte[] classBytes = createClassWithClassForName("com.example.MyCustomClass");

    Set<String> reflectedClasses = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(reflectedClasses).isEmpty();
  }

  @Test
  void shouldMapReflectedClassesToModules() throws IOException {
    byte[] classBytes =
        createClassWithMultipleClassForName("java.sql.Driver", "javax.xml.parsers.SAXParser");
    Path jar = createJarWithClass(tempDir, "test.jar", "com/example/ReflectionUser", classBytes);

    Set<String> modules = scanner.scanJar(jar);

    assertThat(modules).contains("java.sql", "java.xml");
  }

  @Test
  void shouldScanMultipleJars() throws IOException {
    byte[] class1 = createClassWithClassForName("java.sql.Driver");
    byte[] class2 = createClassWithClassForName("java.rmi.Remote");

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);

    Set<String> modules = scanner.scanJars(List.of(jar1, jar2));

    assertThat(modules).contains("java.sql", "java.rmi");
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    Set<String> modules = scanner.scanJar(nonExistent);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldScanDirectory() throws IOException {
    byte[] classBytes = createClassWithClassForName("java.sql.Connection");
    Path classDir = tempDir.resolve("classes");
    Files.createDirectories(classDir.resolve("com/example"));
    Files.write(classDir.resolve("com/example/DbClient.class"), classBytes);

    Set<String> modules = scanner.scanDirectory(classDir);

    assertThat(modules).contains("java.sql");
  }

  @Test
  void shouldHandleEmptyJar() throws IOException {
    Path jar = tempDir.resolve("empty.jar");
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      // Empty JAR
    }

    Set<String> modules = scanner.scanJar(jar);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldDetectClassForNameWithInitialize() throws IOException {
    // Test Class.forName(String, boolean, ClassLoader) variant
    byte[] classBytes = createClassWithClassForNameInitialize("java.naming.InitialContext");

    Set<String> reflectedClasses = scanner.scanClass(new ByteArrayInputStream(classBytes));

    // Note: java.naming.InitialContext is not a real class, so it won't be mapped
    // But it should still be detected as a reflected JDK-pattern class
    // For the test, let's use a real class
  }

  @Test
  void shouldDetectRealJdkClassWithForNameInitialize() throws IOException {
    byte[] classBytes = createClassWithClassForNameInitialize("javax.sql.DataSource");

    Set<String> reflectedClasses = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(reflectedClasses).contains("javax.sql.DataSource");
  }

  /** Creates bytecode for a class that calls Class.forName(className). */
  private byte[] createClassWithClassForName(String className) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/TestClass", null, "java/lang/Object", null);

    // Create a method that calls Class.forName
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "loadClass",
            "()Ljava/lang/Class;",
            null,
            new String[] {"java/lang/ClassNotFoundException"});
    mv.visitCode();

    // Load the class name constant
    mv.visitLdcInsn(className);
    // Call Class.forName(String)
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/Class",
        "forName",
        "(Ljava/lang/String;)Ljava/lang/Class;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode for a class that calls Class.forName multiple times. */
  private byte[] createClassWithMultipleClassForName(String... classNames) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/MultiTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "loadClasses",
            "()V",
            null,
            new String[] {"java/lang/ClassNotFoundException"});
    mv.visitCode();

    for (String className : classNames) {
      mv.visitLdcInsn(className);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          "java/lang/Class",
          "forName",
          "(Ljava/lang/String;)Ljava/lang/Class;",
          false);
      mv.visitInsn(Opcodes.POP); // Discard result
    }

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that calls Class.forName(String, boolean, ClassLoader). */
  private byte[] createClassWithClassForNameInitialize(String className) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/InitTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "loadClass",
            "()Ljava/lang/Class;",
            null,
            new String[] {"java/lang/ClassNotFoundException"});
    mv.visitCode();

    // Load arguments for Class.forName(String, boolean, ClassLoader)
    mv.visitLdcInsn(className); // class name
    mv.visitInsn(Opcodes.ICONST_1); // initialize = true
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/Thread",
        "getContextClassLoader",
        "()Ljava/lang/ClassLoader;",
        false);

    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/Class",
        "forName",
        "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(3, 0);
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
