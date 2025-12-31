package com.ghiloufi.slimjre.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * Scans bytecode to detect locale-related API usage patterns that may require the {@code
 * jdk.localedata} module.
 *
 * <p>This scanner addresses a fundamental limitation of jdeps: it cannot detect locale data module
 * requirements because locale data is loaded at runtime via SPI. When applications use non-English
 * locales like {@code Locale.FRENCH}, the actual locale data in {@code jdk.localedata} is loaded
 * dynamically, which jdeps cannot trace.
 *
 * <p>Detection is organized into three confidence tiers:
 *
 * <ul>
 *   <li><b>Tier 1 (Definite):</b> Explicit non-English locale constants - 100% reliable
 *   <li><b>Tier 2 (Strong):</b> Internationalization APIs - 80% confidence
 *   <li><b>Tier 3 (Possible):</b> Common locale APIs - 50% confidence
 * </ul>
 *
 * <p>Example detections:
 *
 * <pre>
 *   Locale.FRENCH                              → Tier 1 (Definite)
 *   DateTimeFormatter.ofLocalizedDate()        → Tier 2 (Strong)
 *   ResourceBundle.getBundle()                 → Tier 2 (Strong)
 *   Locale.getDefault()                        → Tier 3 (Possible)
 * </pre>
 */
public class LocaleModuleScanner {

  private static final Logger log = LoggerFactory.getLogger(LocaleModuleScanner.class);

  /**
   * Non-English locale field names from java.util.Locale, computed dynamically at class load time.
   * These are fields where locale.getLanguage() is not "en" and not empty (ROOT).
   */
  private static final Set<String> NON_ENGLISH_LOCALE_CONSTANTS =
      computeNonEnglishLocaleConstants();

  /**
   * English locale field names that don't require jdk.localedata. These work with java.base alone.
   */
  private static final Set<String> ENGLISH_LOCALE_CONSTANTS = computeEnglishLocaleConstants();

  /** Tier 2: Localized formatting method names on DateTimeFormatter. */
  private static final Set<String> LOCALIZED_FORMATTER_METHODS =
      Set.of("ofLocalizedDate", "ofLocalizedDateTime", "ofLocalizedTime");

  /** Tier 2: Classes whose presence strongly indicates i18n. */
  private static final Set<String> I18N_INDICATOR_CLASSES =
      Set.of(
          "java/util/ResourceBundle",
          "java/text/MessageFormat",
          "java/text/ChoiceFormat",
          "java/text/Collator",
          "java/text/RuleBasedCollator");

  /** Tier 3: Common locale APIs that might work with English only. */
  private static final Set<String> COMMON_LOCALE_METHODS =
      Set.of("getDefault", "setDefault", "getAvailableLocales");

  /** Creates a new LocaleModuleScanner. */
  public LocaleModuleScanner() {
    log.debug(
        "Initialized LocaleModuleScanner with {} non-English locale constants",
        NON_ENGLISH_LOCALE_CONSTANTS.size());
    log.trace("Non-English locales: {}", NON_ENGLISH_LOCALE_CONSTANTS);
  }

  /**
   * Dynamically computes the set of non-English locale constant field names from java.util.Locale.
   *
   * <p>This approach is future-proof: if Java adds new locale constants, they will be automatically
   * detected without code changes.
   *
   * @return set of field names for non-English locales (e.g., "FRENCH", "GERMANY")
   */
  private static Set<String> computeNonEnglishLocaleConstants() {
    Set<String> result = new HashSet<>();
    try {
      for (Field field : Locale.class.getDeclaredFields()) {
        if (isStaticFinalLocale(field)) {
          Locale locale = (Locale) field.get(null);
          String language = locale.getLanguage();
          // Non-English and not ROOT (empty language)
          if (!language.isEmpty() && !language.equals("en")) {
            result.add(field.getName());
          }
        }
      }
    } catch (IllegalAccessException e) {
      log.warn("Failed to compute non-English locale constants via reflection: {}", e.getMessage());
      // Fallback to known constants if reflection fails
      return Set.of(
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
    }
    return Set.copyOf(result);
  }

  /**
   * Dynamically computes the set of English locale constant field names from java.util.Locale.
   *
   * @return set of field names for English locales (e.g., "ENGLISH", "US", "UK")
   */
  private static Set<String> computeEnglishLocaleConstants() {
    Set<String> result = new HashSet<>();
    try {
      for (Field field : Locale.class.getDeclaredFields()) {
        if (isStaticFinalLocale(field)) {
          Locale locale = (Locale) field.get(null);
          String language = locale.getLanguage();
          // English or ROOT (empty language)
          if (language.isEmpty() || language.equals("en")) {
            result.add(field.getName());
          }
        }
      }
    } catch (IllegalAccessException e) {
      log.warn("Failed to compute English locale constants via reflection: {}", e.getMessage());
      return Set.of("ENGLISH", "US", "UK", "CANADA", "ROOT");
    }
    return Set.copyOf(result);
  }

  /**
   * Checks if a field is a static final Locale constant.
   *
   * @param field the field to check
   * @return true if the field is public static final Locale
   */
  private static boolean isStaticFinalLocale(Field field) {
    int mods = field.getModifiers();
    return Modifier.isStatic(mods)
        && Modifier.isFinal(mods)
        && Modifier.isPublic(mods)
        && field.getType() == Locale.class;
  }

  /**
   * Returns the dynamically computed set of non-English locale constants. Useful for testing and
   * documentation.
   *
   * @return unmodifiable set of non-English locale field names
   */
  public Set<String> getNonEnglishLocaleConstants() {
    return NON_ENGLISH_LOCALE_CONSTANTS;
  }

  /**
   * Returns the dynamically computed set of English locale constants. Useful for testing and
   * documentation.
   *
   * @return unmodifiable set of English locale field names
   */
  public Set<String> getEnglishLocaleConstants() {
    return ENGLISH_LOCALE_CONSTANTS;
  }

  /**
   * Scans multiple JARs in parallel for locale-related API usage.
   *
   * @param jars list of JAR paths to scan (application + dependencies)
   * @return detection result with confidence level and detected patterns
   */
  public LocaleDetectionResult scanJarsParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return LocaleDetectionResult.empty();
    }

    log.debug("Scanning {} JAR(s) for locale patterns...", jars.size());

    Set<String> tier1Patterns = ConcurrentHashMap.newKeySet();
    Set<String> tier2Patterns = ConcurrentHashMap.newKeySet();
    Set<String> tier3Patterns = ConcurrentHashMap.newKeySet();
    Set<String> detectedInJars = ConcurrentHashMap.newKeySet();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<JarScanResult>> futures =
          jars.stream().map(jar -> executor.submit(() -> scanJar(jar))).toList();

      for (Future<JarScanResult> future : futures) {
        try {
          JarScanResult result = future.get();
          if (result.hasPatterns()) {
            detectedInJars.add(result.jarName);
            tier1Patterns.addAll(result.tier1);
            tier2Patterns.addAll(result.tier2);
            tier3Patterns.addAll(result.tier3);
          }
        } catch (Exception e) {
          log.warn("Failed to get locale scan result: {}", e.getMessage());
        }
      }
    }

    // Determine confidence level and required modules
    LocaleConfidence confidence;
    Set<String> requiredModules = new TreeSet<>();

    if (!tier1Patterns.isEmpty()) {
      confidence = LocaleConfidence.DEFINITE;
      requiredModules.add("jdk.localedata");
      log.info(
          "Locale detection: Non-English locales found in {} JAR(s), adding jdk.localedata",
          detectedInJars.size());
    } else if (!tier2Patterns.isEmpty()) {
      confidence = LocaleConfidence.STRONG;
      log.info(
          "Locale detection: i18n APIs found in {} JAR(s), consider adding jdk.localedata",
          detectedInJars.size());
    } else if (!tier3Patterns.isEmpty()) {
      confidence = LocaleConfidence.POSSIBLE;
      log.debug("Locale detection: Common locale APIs found (may work with English only)");
    } else {
      confidence = LocaleConfidence.NONE;
      log.debug("Locale detection: No locale patterns found");
    }

    return new LocaleDetectionResult(
        requiredModules,
        new TreeSet<>(tier1Patterns),
        new TreeSet<>(tier2Patterns),
        new TreeSet<>(tier3Patterns),
        new LinkedHashSet<>(detectedInJars),
        confidence);
  }

  /**
   * Scans a single JAR file for locale-related API usage.
   *
   * @param jarPath path to the JAR file to scan
   * @return scan result with JAR name and detected patterns by tier
   */
  JarScanResult scanJar(Path jarPath) {
    Objects.requireNonNull(jarPath, "jarPath must not be null");

    String jarName = jarPath.getFileName().toString();

    if (!Files.exists(jarPath)) {
      log.warn("JAR file does not exist: {}", jarPath);
      return new JarScanResult(jarName, Set.of(), Set.of(), Set.of());
    }

    Set<String> tier1 = new LinkedHashSet<>();
    Set<String> tier2 = new LinkedHashSet<>();
    Set<String> tier3 = new LinkedHashSet<>();

    try (JarFile jar = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
          // Skip module-info.class
          if (entry.getName().equals("module-info.class")
              || entry.getName().endsWith("/module-info.class")) {
            continue;
          }
          try (InputStream is = jar.getInputStream(entry);
              BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
            ClassScanResult classResult = scanClass(bis);
            tier1.addAll(classResult.tier1);
            tier2.addAll(classResult.tier2);
            tier3.addAll(classResult.tier3);
          } catch (IOException e) {
            log.trace("Failed to scan class {}: {}", entry.getName(), e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to scan JAR {}: {}", jarPath, e.getMessage());
      return new JarScanResult(jarName, Set.of(), Set.of(), Set.of());
    }

    return new JarScanResult(jarName, tier1, tier2, tier3);
  }

  /**
   * Scans a single class from an input stream for locale-related API usage.
   *
   * @param classInputStream input stream containing class bytes
   * @return scan result with patterns by tier
   */
  ClassScanResult scanClass(InputStream classInputStream) throws IOException {
    ClassReader reader = new ClassReader(classInputStream);
    LocalePatternVisitor visitor = new LocalePatternVisitor();
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return new ClassScanResult(
        visitor.getTier1Patterns(), visitor.getTier2Patterns(), visitor.getTier3Patterns());
  }

  /** Internal record to hold per-JAR scan results. */
  record JarScanResult(String jarName, Set<String> tier1, Set<String> tier2, Set<String> tier3) {
    boolean hasPatterns() {
      return !tier1.isEmpty() || !tier2.isEmpty() || !tier3.isEmpty();
    }
  }

  /** Internal record to hold per-class scan results. */
  record ClassScanResult(Set<String> tier1, Set<String> tier2, Set<String> tier3) {}

  /** ASM ClassVisitor that detects locale-related API usage patterns. */
  private class LocalePatternVisitor extends ClassVisitor {

    private final Set<String> tier1Patterns = new LinkedHashSet<>();
    private final Set<String> tier2Patterns = new LinkedHashSet<>();
    private final Set<String> tier3Patterns = new LinkedHashSet<>();

    LocalePatternVisitor() {
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
      // Check superclass and interfaces for i18n indicator classes
      checkI18nClass(superName);
      if (interfaces != null) {
        for (String iface : interfaces) {
          checkI18nClass(iface);
        }
      }
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      checkDescriptorForI18n(descriptor);
      return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      checkDescriptorForI18n(descriptor);
      return new LocaleMethodVisitor();
    }

    Set<String> getTier1Patterns() {
      return new TreeSet<>(tier1Patterns);
    }

    Set<String> getTier2Patterns() {
      return new TreeSet<>(tier2Patterns);
    }

    Set<String> getTier3Patterns() {
      return new TreeSet<>(tier3Patterns);
    }

    private void checkI18nClass(String internalName) {
      if (internalName != null && I18N_INDICATOR_CLASSES.contains(internalName)) {
        tier2Patterns.add(internalName.replace('/', '.'));
      }
    }

    private void checkDescriptorForI18n(String descriptor) {
      if (descriptor == null) {
        return;
      }
      Type type = Type.getType(descriptor);
      checkTypeForI18n(type);
    }

    private void checkTypeForI18n(Type type) {
      if (type == null) {
        return;
      }
      switch (type.getSort()) {
        case Type.ARRAY -> checkTypeForI18n(type.getElementType());
        case Type.OBJECT -> checkI18nClass(type.getInternalName());
        case Type.METHOD -> {
          checkTypeForI18n(type.getReturnType());
          for (Type argType : type.getArgumentTypes()) {
            checkTypeForI18n(argType);
          }
        }
      }
    }

    /** Inner method visitor to check method body instructions. */
    private class LocaleMethodVisitor extends MethodVisitor {

      LocaleMethodVisitor() {
        super(Opcodes.ASM9);
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        // Tier 1: Detect non-English locale constants via GETSTATIC
        if (opcode == Opcodes.GETSTATIC && "java/util/Locale".equals(owner)) {
          if (NON_ENGLISH_LOCALE_CONSTANTS.contains(name)) {
            tier1Patterns.add("Locale." + name);
            log.trace("Tier 1: Found non-English locale constant: Locale.{}", name);
          } else if (ENGLISH_LOCALE_CONSTANTS.contains(name)) {
            // English locales don't need jdk.localedata, don't add to any tier
            log.trace("Ignoring English locale constant: Locale.{}", name);
          }
        }
      }

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {

        // Tier 2: DateTimeFormatter.ofLocalized*()
        if ("java/time/format/DateTimeFormatter".equals(owner)
            && LOCALIZED_FORMATTER_METHODS.contains(name)) {
          tier2Patterns.add("DateTimeFormatter." + name + "()");
          log.trace("Tier 2: Found localized formatter: DateTimeFormatter.{}", name);
        }

        // Tier 2: ResourceBundle.getBundle()
        if ("java/util/ResourceBundle".equals(owner) && "getBundle".equals(name)) {
          tier2Patterns.add("ResourceBundle.getBundle()");
          log.trace("Tier 2: Found ResourceBundle.getBundle()");
        }

        // Tier 2: NumberFormat/DateFormat with Locale parameter
        if (("java/text/NumberFormat".equals(owner) || "java/text/DateFormat".equals(owner))
            && descriptor.contains("Ljava/util/Locale;")) {
          tier2Patterns.add(owner.substring(owner.lastIndexOf('/') + 1) + "." + name + "(Locale)");
          log.trace("Tier 2: Found locale-aware formatting: {}.{}", owner, name);
        }

        // Tier 3: Locale.getDefault(), setDefault(), getAvailableLocales()
        if ("java/util/Locale".equals(owner) && COMMON_LOCALE_METHODS.contains(name)) {
          tier3Patterns.add("Locale." + name + "()");
          log.trace("Tier 3: Found common locale method: Locale.{}", name);
        }

        // Check for i18n indicator classes in method calls
        checkI18nClass(owner);
        checkDescriptorForI18n(descriptor);
      }

      @Override
      public void visitTypeInsn(int opcode, String type) {
        // Check for i18n classes being instantiated
        checkI18nClass(type);
      }

      @Override
      public void visitTryCatchBlock(
          org.objectweb.asm.Label start,
          org.objectweb.asm.Label end,
          org.objectweb.asm.Label handler,
          String type) {
        if (type != null) {
          checkI18nClass(type);
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
        checkDescriptorForI18n(descriptor);
      }
    }
  }
}
