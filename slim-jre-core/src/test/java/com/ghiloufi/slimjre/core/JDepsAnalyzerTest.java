package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import com.ghiloufi.slimjre.exception.JDepsException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for JDepsAnalyzer. */
class JDepsAnalyzerTest {

  private JDepsAnalyzer analyzer;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    analyzer = new JDepsAnalyzer();
  }

  @Test
  void shouldBeConstructedSuccessfully() {
    assertThat(analyzer).isNotNull();
    assertThat(analyzer.getJavaVersion()).isGreaterThanOrEqualTo(9);
  }

  @Test
  void shouldThrowForNullJarPath() {
    assertThatThrownBy(() -> analyzer.analyzeRequiredModules(null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldThrowForEmptyJarList() {
    assertThatThrownBy(() -> analyzer.analyzeRequiredModules(List.of()))
        .isInstanceOf(JDepsException.class)
        .hasMessageContaining("At least one JAR file");
  }

  @Test
  void shouldAnalyzeSimpleJar() throws IOException {
    // Create a simple JAR with a class that uses java.base features
    Path jar = createSimpleJar(tempDir, "simple.jar");

    Set<String> modules = analyzer.analyzeRequiredModules(List.of(jar));

    assertThat(modules).isNotEmpty().contains("java.base");
  }

  @Test
  void shouldAnalyzeJarWithClasspath() throws IOException {
    Path mainJar = createSimpleJar(tempDir, "main.jar");
    Path libJar = createSimpleJar(tempDir, "lib.jar");

    Set<String> modules = analyzer.analyzeRequiredModules(mainJar, List.of(libJar));

    assertThat(modules).contains("java.base");
  }

  @Test
  void shouldAnalyzeMultipleJars() throws IOException {
    Path jar1 = createSimpleJar(tempDir, "app1.jar");
    Path jar2 = createSimpleJar(tempDir, "app2.jar");

    Set<String> modules = analyzer.analyzeRequiredModules(List.of(jar1, jar2));

    assertThat(modules).contains("java.base");
  }

  @Test
  void shouldAnalyzePerJar() throws IOException {
    Path jar1 = createSimpleJar(tempDir, "first.jar");
    Path jar2 = createSimpleJar(tempDir, "second.jar");

    var perJar = analyzer.analyzeRequiredModulesPerJar(List.of(jar1, jar2));

    assertThat(perJar).hasSize(2);
    assertThat(perJar.get(jar1)).contains("java.base");
    assertThat(perJar.get(jar2)).contains("java.base");
  }

  @Test
  void shouldHandleInvalidJar() {
    Path invalidJar = tempDir.resolve("invalid.jar");
    try {
      java.nio.file.Files.write(invalidJar, "not a jar".getBytes());
    } catch (IOException e) {
      fail("Failed to create test file");
    }

    // jdeps throws various exceptions for invalid JARs
    // This tests that we at least don't crash silently
    assertThatThrownBy(() -> analyzer.analyzeRequiredModules(List.of(invalidJar)))
        .isInstanceOfAny(JDepsException.class, RuntimeException.class);
  }

  @Test
  void shouldReturnJavaVersionCorrectly() {
    int version = analyzer.getJavaVersion();

    assertThat(version).isEqualTo(Runtime.version().feature());
  }

  /** Creates a simple JAR with a minimal class file. */
  private Path createSimpleJar(Path dir, String jarName) throws IOException {
    Path jarPath = dir.resolve(jarName);

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jos =
        new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
      // Add a simple class file (pre-compiled HelloWorld)
      // This is a minimal valid class file for a simple class
      JarEntry entry = new JarEntry("com/example/Simple.class");
      jos.putNextEntry(entry);

      // Minimal class file bytes for:
      // package com.example;
      // public class Simple { }
      byte[] classBytes = createMinimalClassBytes();
      jos.write(classBytes);
      jos.closeEntry();
    }

    return jarPath;
  }

  /** Creates minimal class file bytes. This is a valid class file for a simple empty class. */
  private byte[] createMinimalClassBytes() {
    // This is a pre-compiled class file for:
    // package com.example;
    // public class Simple { public Simple() { } }
    // Compiled with Java 8 target (52.0)
    return new byte[] {
      // Magic number
      (byte) 0xCA,
      (byte) 0xFE,
      (byte) 0xBA,
      (byte) 0xBE,
      // Minor version (0)
      0x00,
      0x00,
      // Major version (52 = Java 8, compatible with all modern JDKs)
      0x00,
      0x34,
      // Constant pool count (15 entries + 1)
      0x00,
      0x10,
      // Constant pool entries...
      // 1: CONSTANT_Methodref - Object.<init>
      0x0A,
      0x00,
      0x03,
      0x00,
      0x0D,
      // 2: CONSTANT_Class - com/example/Simple
      0x07,
      0x00,
      0x0E,
      // 3: CONSTANT_Class - java/lang/Object
      0x07,
      0x00,
      0x0F,
      // 4: CONSTANT_Utf8 - <init>
      0x01,
      0x00,
      0x06,
      '<',
      'i',
      'n',
      'i',
      't',
      '>',
      // 5: CONSTANT_Utf8 - ()V
      0x01,
      0x00,
      0x03,
      '(',
      ')',
      'V',
      // 6: CONSTANT_Utf8 - Code
      0x01,
      0x00,
      0x04,
      'C',
      'o',
      'd',
      'e',
      // 7: CONSTANT_Utf8 - LineNumberTable
      0x01,
      0x00,
      0x0F,
      'L',
      'i',
      'n',
      'e',
      'N',
      'u',
      'm',
      'b',
      'e',
      'r',
      'T',
      'a',
      'b',
      'l',
      'e',
      // 8: CONSTANT_Utf8 - LocalVariableTable
      0x01,
      0x00,
      0x12,
      'L',
      'o',
      'c',
      'a',
      'l',
      'V',
      'a',
      'r',
      'i',
      'a',
      'b',
      'l',
      'e',
      'T',
      'a',
      'b',
      'l',
      'e',
      // 9: CONSTANT_Utf8 - this
      0x01,
      0x00,
      0x04,
      't',
      'h',
      'i',
      's',
      // 10: CONSTANT_Utf8 - Lcom/example/Simple;
      0x01,
      0x00,
      0x14,
      'L',
      'c',
      'o',
      'm',
      '/',
      'e',
      'x',
      'a',
      'm',
      'p',
      'l',
      'e',
      '/',
      'S',
      'i',
      'm',
      'p',
      'l',
      'e',
      ';',
      // 11: CONSTANT_Utf8 - SourceFile
      0x01,
      0x00,
      0x0A,
      'S',
      'o',
      'u',
      'r',
      'c',
      'e',
      'F',
      'i',
      'l',
      'e',
      // 12: CONSTANT_Utf8 - Simple.java
      0x01,
      0x00,
      0x0B,
      'S',
      'i',
      'm',
      'p',
      'l',
      'e',
      '.',
      'j',
      'a',
      'v',
      'a',
      // 13: CONSTANT_NameAndType - <init>:()V
      0x0C,
      0x00,
      0x04,
      0x00,
      0x05,
      // 14: CONSTANT_Utf8 - com/example/Simple
      0x01,
      0x00,
      0x12,
      'c',
      'o',
      'm',
      '/',
      'e',
      'x',
      'a',
      'm',
      'p',
      'l',
      'e',
      '/',
      'S',
      'i',
      'm',
      'p',
      'l',
      'e',
      // 15: CONSTANT_Utf8 - java/lang/Object
      0x01,
      0x00,
      0x10,
      'j',
      'a',
      'v',
      'a',
      '/',
      'l',
      'a',
      'n',
      'g',
      '/',
      'O',
      'b',
      'j',
      'e',
      'c',
      't',
      // Access flags (public)
      0x00,
      0x21,
      // This class
      0x00,
      0x02,
      // Super class
      0x00,
      0x03,
      // Interfaces count
      0x00,
      0x00,
      // Fields count
      0x00,
      0x00,
      // Methods count (1 - constructor)
      0x00,
      0x01,
      // Constructor method
      0x00,
      0x01, // access flags (public)
      0x00,
      0x04, // name index (<init>)
      0x00,
      0x05, // descriptor index (()V)
      0x00,
      0x01, // attributes count
      // Code attribute
      0x00,
      0x06, // attribute name index (Code)
      0x00,
      0x00,
      0x00,
      0x1D, // attribute length
      0x00,
      0x01, // max stack
      0x00,
      0x01, // max locals
      0x00,
      0x00,
      0x00,
      0x05, // code length
      // Bytecode: aload_0, invokespecial Object.<init>, return
      0x2A,
      (byte) 0xB7,
      0x00,
      0x01,
      (byte) 0xB1,
      0x00,
      0x00, // exception table length
      0x00,
      0x00, // attributes count
      // Class attributes count
      0x00,
      0x01,
      // SourceFile attribute
      0x00,
      0x0B, // attribute name index (SourceFile)
      0x00,
      0x00,
      0x00,
      0x02, // attribute length
      0x00,
      0x0C // source file name index (Simple.java)
    };
  }
}
