package com.ghiloufi.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Tests for LocaleModuleScanner with dynamic locale detection. */
class LocaleModuleScannerTest {

  private LocaleModuleScanner scanner;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new LocaleModuleScanner();
  }

  // ==================== Dynamic Locale Constant Tests ====================

  @Test
  void shouldDynamicallyDetectNonEnglishLocaleConstants() {
    Set<String> nonEnglish = scanner.getNonEnglishLocaleConstants();

    // Verify known non-English locales are detected
    assertThat(nonEnglish)
        .contains(
            "FRENCH",
            "GERMAN",
            "ITALIAN",
            "JAPANESE",
            "KOREAN",
            "CHINESE",
            "SIMPLIFIED_CHINESE",
            "TRADITIONAL_CHINESE",
            "FRANCE",
            "GERMANY",
            "ITALY",
            "JAPAN",
            "KOREA",
            "CHINA",
            "PRC",
            "TAIWAN",
            "CANADA_FRENCH");

    // Verify English locales are NOT in the non-English set
    assertThat(nonEnglish).doesNotContain("ENGLISH", "US", "UK", "CANADA", "ROOT");
  }

  @Test
  void shouldDynamicallyDetectEnglishLocaleConstants() {
    Set<String> english = scanner.getEnglishLocaleConstants();

    // Verify known English locales are detected
    assertThat(english).contains("ENGLISH", "US", "UK", "CANADA", "ROOT");

    // Verify non-English locales are NOT in the English set
    assertThat(english).doesNotContain("FRENCH", "GERMAN", "JAPANESE");
  }

  @Test
  void shouldVerifyDynamicDetectionMatchesActualLocaleValues() {
    // Verify our dynamic detection matches actual Locale values
    Set<String> nonEnglish = scanner.getNonEnglishLocaleConstants();

    // FRENCH should have language "fr"
    assertThat(Locale.FRENCH.getLanguage()).isEqualTo("fr");
    assertThat(nonEnglish).contains("FRENCH");

    // US should have language "en"
    assertThat(Locale.US.getLanguage()).isEqualTo("en");
    assertThat(nonEnglish).doesNotContain("US");

    // ROOT should have empty language
    assertThat(Locale.ROOT.getLanguage()).isEmpty();
    assertThat(nonEnglish).doesNotContain("ROOT");
  }

  // ==================== Tier 1: Non-English Locale Detection ====================

  @Test
  void shouldDetectFrenchLocaleConstant() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("FRENCH");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).contains("Locale.FRENCH");
    assertThat(result.tier2()).isEmpty();
  }

  @Test
  void shouldDetectGermanLocaleConstant() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("GERMAN");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).contains("Locale.GERMAN");
  }

  @Test
  void shouldDetectJapaneseLocaleConstant() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("JAPAN");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).contains("Locale.JAPAN");
  }

  @Test
  void shouldDetectChineseLocaleConstant() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("SIMPLIFIED_CHINESE");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).contains("Locale.SIMPLIFIED_CHINESE");
  }

  @Test
  void shouldNotDetectEnglishLocaleAsNonEnglish() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("ENGLISH");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    // English should not be in any tier
    assertThat(result.tier1()).isEmpty();
    assertThat(result.tier2()).isEmpty();
    assertThat(result.tier3()).isEmpty();
  }

  @Test
  void shouldNotDetectUsLocaleAsNonEnglish() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("US");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).isEmpty();
  }

  @Test
  void shouldNotDetectRootLocaleAsNonEnglish() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("ROOT");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier1()).isEmpty();
  }

  // ==================== Tier 2: i18n API Detection ====================

  @Test
  void shouldDetectDateTimeFormatterOfLocalizedDate() throws IOException {
    byte[] classBytes = createClassWithLocalizedFormatter("ofLocalizedDate");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier2()).contains("DateTimeFormatter.ofLocalizedDate()");
  }

  @Test
  void shouldDetectDateTimeFormatterOfLocalizedDateTime() throws IOException {
    byte[] classBytes = createClassWithLocalizedFormatter("ofLocalizedDateTime");

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier2()).contains("DateTimeFormatter.ofLocalizedDateTime()");
  }

  @Test
  void shouldDetectResourceBundleGetBundle() throws IOException {
    byte[] classBytes = createClassWithResourceBundle();

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier2()).contains("ResourceBundle.getBundle()");
  }

  // ==================== Tier 3: Common Locale APIs ====================

  @Test
  void shouldDetectLocaleGetDefault() throws IOException {
    byte[] classBytes = createClassWithLocaleGetDefault();

    LocaleModuleScanner.ClassScanResult result =
        scanner.scanClass(new ByteArrayInputStream(classBytes));

    assertThat(result.tier3()).contains("Locale.getDefault()");
  }

  // ==================== JAR Scanning Tests ====================

  @Test
  void shouldScanJarAndReturnDefiniteConfidence() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("FRENCH");
    Path jar = createJarWithClass(tempDir, "i18n.jar", "com/example/I18nClass", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.confidence()).isEqualTo(LocaleConfidence.DEFINITE);
    assertThat(result.isRequired()).isTrue();
    assertThat(result.requiredModules()).contains("jdk.localedata");
    assertThat(result.tier1Patterns()).contains("Locale.FRENCH");
    assertThat(result.detectedInJars()).contains("i18n.jar");
  }

  @Test
  void shouldScanJarAndReturnStrongConfidence() throws IOException {
    byte[] classBytes = createClassWithResourceBundle();
    Path jar = createJarWithClass(tempDir, "i18n.jar", "com/example/I18nClass", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.confidence()).isEqualTo(LocaleConfidence.STRONG);
    assertThat(result.isRequired()).isFalse(); // Strong doesn't auto-add module
    assertThat(result.requiredModules()).isEmpty();
    assertThat(result.tier2Patterns()).contains("ResourceBundle.getBundle()");
  }

  @Test
  void shouldScanJarAndReturnPossibleConfidence() throws IOException {
    byte[] classBytes = createClassWithLocaleGetDefault();
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/AppClass", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.confidence()).isEqualTo(LocaleConfidence.POSSIBLE);
    assertThat(result.isRequired()).isFalse();
    assertThat(result.tier3Patterns()).contains("Locale.getDefault()");
  }

  @Test
  void shouldScanJarAndReturnNoneConfidence() throws IOException {
    byte[] classBytes = createSimpleClass();
    Path jar = createJarWithClass(tempDir, "simple.jar", "com/example/SimpleClass", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    assertThat(result.confidence()).isEqualTo(LocaleConfidence.NONE);
    assertThat(result.isRequired()).isFalse();
    assertThat(result.tier1Patterns()).isEmpty();
    assertThat(result.tier2Patterns()).isEmpty();
    assertThat(result.tier3Patterns()).isEmpty();
  }

  @Test
  void shouldScanMultipleJarsInParallel() throws IOException {
    byte[] class1 = createClassWithLocaleConstant("GERMAN");
    byte[] class2 = createClassWithResourceBundle();
    byte[] class3 = createClassWithLocaleGetDefault();

    Path jar1 = createJarWithClass(tempDir, "jar1.jar", "com/example/Class1", class1);
    Path jar2 = createJarWithClass(tempDir, "jar2.jar", "com/example/Class2", class2);
    Path jar3 = createJarWithClass(tempDir, "jar3.jar", "com/example/Class3", class3);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar1, jar2, jar3));

    // Tier 1 takes precedence
    assertThat(result.confidence()).isEqualTo(LocaleConfidence.DEFINITE);
    assertThat(result.isRequired()).isTrue();
    assertThat(result.requiredModules()).contains("jdk.localedata");

    // All patterns detected
    assertThat(result.tier1Patterns()).contains("Locale.GERMAN");
    assertThat(result.tier2Patterns()).contains("ResourceBundle.getBundle()");
    assertThat(result.tier3Patterns()).contains("Locale.getDefault()");

    // All JARs recorded
    assertThat(result.detectedInJars())
        .containsExactlyInAnyOrder("jar1.jar", "jar2.jar", "jar3.jar");
  }

  @Test
  void shouldHandleEmptyJarList() {
    LocaleDetectionResult result = scanner.scanJarsParallel(List.of());

    assertThat(result).isEqualTo(LocaleDetectionResult.empty());
    assertThat(result.confidence()).isEqualTo(LocaleConfidence.NONE);
  }

  @Test
  void shouldHandleNullJarList() {
    LocaleDetectionResult result = scanner.scanJarsParallel(null);

    assertThat(result).isEqualTo(LocaleDetectionResult.empty());
  }

  @Test
  void shouldHandleNonExistentJar() {
    Path nonExistent = tempDir.resolve("nonexistent.jar");

    LocaleModuleScanner.JarScanResult result = scanner.scanJar(nonExistent);

    assertThat(result.hasPatterns()).isFalse();
  }

  // ==================== Result Summary Tests ====================

  @Test
  void shouldGenerateDefiniteSummary() throws IOException {
    byte[] classBytes = createClassWithLocaleConstant("FRENCH");
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/App", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    String summary = result.summary();
    assertThat(summary).contains("Non-English locale detected");
    assertThat(summary).contains("jdk.localedata required");
    assertThat(summary).contains("Locale.FRENCH");
  }

  @Test
  void shouldGenerateStrongSummary() throws IOException {
    byte[] classBytes = createClassWithLocalizedFormatter("ofLocalizedDate");
    Path jar = createJarWithClass(tempDir, "app.jar", "com/example/App", classBytes);

    LocaleDetectionResult result = scanner.scanJarsParallel(List.of(jar));

    String summary = result.summary();
    assertThat(summary).contains("Internationalization APIs detected");
    assertThat(summary).contains("Consider --add-modules jdk.localedata");
  }

  @Test
  void shouldGenerateNoneSummary() {
    LocaleDetectionResult result = LocaleDetectionResult.empty();

    String summary = result.summary();
    assertThat(summary).contains("No locale-related patterns detected");
  }

  // ==================== Helper Methods ====================

  /** Creates bytecode that accesses a Locale constant via GETSTATIC. */
  private byte[] createClassWithLocaleConstant(String constantName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/LocaleTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getLocale",
            "()Ljava/util/Locale;",
            null,
            null);
    mv.visitCode();

    // GETSTATIC java/util/Locale.CONSTANT : Ljava/util/Locale;
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/util/Locale", constantName, "Ljava/util/Locale;");
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that calls DateTimeFormatter.ofLocalized*(). */
  private byte[] createClassWithLocalizedFormatter(String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/FormatterTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getFormatter",
            "()Ljava/time/format/DateTimeFormatter;",
            null,
            null);
    mv.visitCode();

    // Get FormatStyle.MEDIUM
    mv.visitFieldInsn(
        Opcodes.GETSTATIC,
        "java/time/format/FormatStyle",
        "MEDIUM",
        "Ljava/time/format/FormatStyle;");

    // DateTimeFormatter.ofLocalizedDate(FormatStyle)
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/time/format/DateTimeFormatter",
        methodName,
        "(Ljava/time/format/FormatStyle;)Ljava/time/format/DateTimeFormatter;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that calls ResourceBundle.getBundle(). */
  private byte[] createClassWithResourceBundle() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/ResourceBundleTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getBundle",
            "()Ljava/util/ResourceBundle;",
            null,
            null);
    mv.visitCode();

    // ResourceBundle.getBundle("messages")
    mv.visitLdcInsn("messages");
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/ResourceBundle",
        "getBundle",
        "(Ljava/lang/String;)Ljava/util/ResourceBundle;",
        false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates bytecode that calls Locale.getDefault(). */
  private byte[] createClassWithLocaleGetDefault() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "com/example/LocaleDefaultTestClass",
        null,
        "java/lang/Object",
        null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "getDefaultLocale",
            "()Ljava/util/Locale;",
            null,
            null);
    mv.visitCode();

    // Locale.getDefault()
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/util/Locale", "getDefault", "()Ljava/util/Locale;", false);
    mv.visitInsn(Opcodes.ARETURN);

    mv.visitMaxs(1, 0);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Creates a simple class with no locale usage. */
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
}
