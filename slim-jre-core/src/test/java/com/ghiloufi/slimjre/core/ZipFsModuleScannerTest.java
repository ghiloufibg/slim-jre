package com.ghiloufi.slimjre.core;

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

/** Tests for ZipFsModuleScanner. */
class ZipFsModuleScannerTest {

  private ZipFsModuleScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new ZipFsModuleScanner();
  }

  // ==================== Pattern Matching Tests ====================

  @Test
  void shouldMatchFileSystemsPattern() {
    assertThat(scanner.matchesPattern("java/nio/file/FileSystems")).isTrue();
  }

  @Test
  void shouldMatchFileSystemProviderPattern() {
    assertThat(scanner.matchesPattern("java/nio/file/spi/FileSystemProvider")).isTrue();
  }

  @Test
  void shouldNotMatchRegularNioPatterns() {
    assertThat(scanner.matchesPattern("java/nio/file/Files")).isFalse();
    assertThat(scanner.matchesPattern("java/nio/file/Path")).isFalse();
    assertThat(scanner.matchesPattern("java/nio/file/Paths")).isFalse();
    assertThat(scanner.matchesPattern("java/nio/file/FileSystem")).isFalse();
  }

  @Test
  void shouldNotMatchNonNioPatterns() {
    assertThat(scanner.matchesPattern("java/lang/String")).isFalse();
    assertThat(scanner.matchesPattern("java/util/List")).isFalse();
    assertThat(scanner.matchesPattern("com/example/MyClass")).isFalse();
  }

  @Test
  void shouldHandleNullAndEmptyPatterns() {
    assertThat(scanner.matchesPattern(null)).isFalse();
    assertThat(scanner.matchesPattern("")).isFalse();
  }

  // ==================== String Constant Tests ====================

  @Test
  void shouldDetectJarUriScheme() {
    assertThat(scanner.isZipFsStringConstant("jar:file:/path/to/file.jar!/entry")).isTrue();
    assertThat(scanner.isZipFsStringConstant("jar:")).isTrue();
  }

  @Test
  void shouldDetectFileSystemTypeIdentifiers() {
    assertThat(scanner.isZipFsStringConstant("jar")).isTrue();
    assertThat(scanner.isZipFsStringConstant("zip")).isTrue();
  }

  @Test
  void shouldNotDetectRegularStrings() {
    assertThat(scanner.isZipFsStringConstant("hello")).isFalse();
    assertThat(scanner.isZipFsStringConstant("file.txt")).isFalse();
    assertThat(scanner.isZipFsStringConstant("http://example.com")).isFalse();
  }

  @Test
  void shouldHandleNullAndEmptyStrings() {
    assertThat(scanner.isZipFsStringConstant(null)).isFalse();
    assertThat(scanner.isZipFsStringConstant("")).isFalse();
  }

  // ==================== Bytecode Scanning Tests ====================

  @Test
  void shouldDetectFileSystemsNewFileSystemUsage() throws IOException {
    byte[] classBytes = createClassWithNewFileSystemUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("FileSystems.newFileSystem()");
  }

  @Test
  void shouldDetectFileSystemsGetFileSystemUsage() throws IOException {
    byte[] classBytes = createClassWithGetFileSystemUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("FileSystems.getFileSystem()");
  }

  @Test
  void shouldDetectFileSystemProviderUsage() throws IOException {
    byte[] classBytes = createClassWithFileSystemProviderUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).contains("java/nio/file/spi/FileSystemProvider");
  }

  @Test
  void shouldDetectJarUriStringConstant() throws IOException {
    byte[] classBytes = createClassWithJarUriConstant();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).anyMatch(p -> p.contains("jar:"));
  }

  @Test
  void shouldReturnEmptyForRegularFileUsage() throws IOException {
    byte[] classBytes = createClassWithRegularFileUsage();

    Set<String> patterns = scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(patterns).isEmpty();
  }

  // ==================== JAR Scanning Tests ====================

  @Test
  void shouldScanJarForZipFsPatterns() throws IOException {
    byte[] classBytes = createClassWithNewFileSystemUsage();
    Path jar = createJarWithClass(tempDir, "zipfs.jar", "com/example/ZipFsUser", classBytes);

    ZipFsModuleScanner.JarScanResult result = scanner.scanJar(jar);

    assertThat(result.jarName()).isEqualTo("zipfs.jar");
    assertThat(result.patterns()).isNotEmpty();
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    byte[] class1 = createClassWithNewFileSystemUsage();
    byte[] class2 = createClassWithJarUriConstant();

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);

    ZipFsDetectionResult result = scanner.scanJarsParallel(List.of(jar1, jar2));

    assertThat(result.isRequired()).isTrue();
    assertThat(result.requiredModules()).contains("jdk.zipfs");
    assertThat(result.detectedInJars()).containsExactlyInAnyOrder("jar1.jar", "jar2.jar");
  }

  @Test
  void shouldReturnEmptyResultForNonZipFsJars() throws IOException {
    byte[] classBytes = createSimpleClass();
    Path jar = createJarWithClass(tempDir, "simple.jar", "com/example/Simple", classBytes);

    ZipFsDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldHandleEmptyJarList() {
    ZipFsDetectionResult result = scanner.scanJarsParallel(List.of());

    assertThat(result).isEqualTo(ZipFsDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNullJarList() {
    ZipFsDetectionResult result = scanner.scanJarsParallel(null);

    assertThat(result).isEqualTo(ZipFsDetectionResult.empty());
    assertThat(result.isRequired()).isFalse();
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    ZipFsModuleScanner.JarScanResult result = scanner.scanJar(nonExistent);

    assertThat(result.patterns()).isEmpty();
  }

  @Test
  void shouldSkipModuleInfoClass() throws IOException {
    Path jar = createJarWithModuleInfo(tempDir, "modular.jar");

    ZipFsModuleScanner.JarScanResult result = scanner.scanJar(jar);

    assertThat(result.patterns()).isEmpty();
  }

  // ==================== ZipFsDetectionResult Tests ====================

  @Test
  void shouldCreateEmptyResult() {
    ZipFsDetectionResult result = ZipFsDetectionResult.empty();

    assertThat(result.isRequired()).isFalse();
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.detectedPatterns()).isEmpty();
    assertThat(result.detectedInJars()).isEmpty();
  }

  @Test
  void shouldGenerateCorrectSummary() throws IOException {
    byte[] classBytes = createClassWithNewFileSystemUsage();
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/App", classBytes);

    ZipFsDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    String summary = result.summary();
    assertThat(summary).contains("jdk.zipfs");
    assertThat(summary).contains("app.jar");
  }

  @Test
  void shouldGenerateNoZipFsSummaryForEmptyResult() {
    ZipFsDetectionResult result = ZipFsDetectionResult.empty();

    String summary = result.summary();
    assertThat(summary).contains("No ZIP filesystem patterns detected");
  }

  // ==================== Helper Methods ====================

  /** Creates bytecode that uses FileSystems.newFileSystem(). */
  private byte[] createClassWithNewFileSystemUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/NewFileSystemTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "openZip",
            "(Ljava/nio/file/Path;)Ljava/nio/file/FileSystem;",
            null,
            new String[] {"java/io/IOException"});
    mv.visitCode();

    // FileSystems.newFileSystem(path)
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/nio/file/FileSystems",
        "newFileSystem",
        "(Ljava/nio/file/Path;)Ljava/nio/file/FileSystem;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses FileSystems.getFileSystem(). */
  private byte[] createClassWithGetFileSystemUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/GetFileSystemTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getFs",
            "(Ljava/net/URI;)Ljava/nio/file/FileSystem;",
            null,
            null);
    mv.visitCode();

    // FileSystems.getFileSystem(uri)
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/nio/file/FileSystems",
        "getFileSystem",
        "(Ljava/net/URI;)Ljava/nio/file/FileSystem;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses FileSystemProvider. */
  private byte[] createClassWithFileSystemProviderUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/FileSystemProviderTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getProviders",
            "()Ljava/util/List;",
            null,
            null);
    mv.visitCode();

    // FileSystemProvider.installedProviders()
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/nio/file/spi/FileSystemProvider",
        "installedProviders",
        "()Ljava/util/List;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode with a "jar:" URI string constant. */
  private byte[] createClassWithJarUriConstant() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/JarUriTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getJarUri",
            "()Ljava/lang/String;",
            null,
            null);
    mv.visitCode();

    // Load "jar:file:/path/to/archive.jar!/entry" string constant
    mv.visitLdcInsn("jar:file:/path/to/archive.jar!/entry");
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that uses regular Files API (not ZipFs). */
  private byte[] createClassWithRegularFileUsage() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/RegularFileTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "exists",
            "(Ljava/nio/file/Path;)Z",
            null,
            null);
    mv.visitCode();

    // Files.exists(path)
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/nio/file/LinkOption");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/nio/file/Files",
        "exists",
        "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
        false);
    mv.visitInsn(Opcodes.IRETURN);

    mv.visitMaxs(2, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates a simple class with no ZipFs usage. */
  private byte[] createSimpleClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/SimpleTestClass",
        null,
        "java/lang/Object",
        null);

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
      JarEntry entry = new JarEntry("module-info.class");
      jos.putNextEntry(entry);
      jos.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 55});
      jos.closeEntry();
    }

    return jarPath;
  }
}
