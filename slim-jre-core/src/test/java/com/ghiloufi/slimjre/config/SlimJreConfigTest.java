package com.ghiloufi.slimjre.config;

import static org.assertj.core.api.Assertions.*;

import com.ghiloufi.slimjre.exception.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for SlimJreConfig. */
class SlimJreConfigTest {

  @TempDir Path tempDir;

  @Test
  void shouldBuildConfigWithDefaults() throws IOException {
    Path jar = createTempJar("test.jar");

    SlimJreConfig config =
        SlimJreConfig.builder().jar(jar).outputPath(tempDir.resolve("output")).build();

    assertThat(config.jars()).hasSize(1);
    assertThat(config.stripDebug()).isTrue();
    assertThat(config.compression()).isEqualTo("zip-6");
    assertThat(config.noHeaderFiles()).isTrue();
    assertThat(config.noManPages()).isTrue();
    assertThat(config.scanServiceLoaders()).isTrue();
    assertThat(config.verbose()).isFalse();
  }

  @Test
  void shouldBuildConfigWithCustomValues() throws IOException {
    Path jar = createTempJar("test.jar");

    SlimJreConfig config =
        SlimJreConfig.builder()
            .jar(jar)
            .outputPath(tempDir.resolve("output"))
            .stripDebug(false)
            .compression("zip-9")
            .noHeaderFiles(false)
            .noManPages(false)
            .scanServiceLoaders(false)
            .verbose(true)
            .addModule("java.management")
            .excludeModule("java.desktop")
            .build();

    assertThat(config.stripDebug()).isFalse();
    assertThat(config.compression()).isEqualTo("zip-9");
    assertThat(config.noHeaderFiles()).isFalse();
    assertThat(config.noManPages()).isFalse();
    assertThat(config.scanServiceLoaders()).isFalse();
    assertThat(config.verbose()).isTrue();
    assertThat(config.additionalModules()).contains("java.management");
    assertThat(config.excludeModules()).contains("java.desktop");
  }

  @Test
  void shouldValidateExistingJar() throws IOException {
    Path jar = createTempJar("valid.jar");

    SlimJreConfig config =
        SlimJreConfig.builder().jar(jar).outputPath(tempDir.resolve("output")).build();

    assertThatCode(config::validate).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectEmptyJarList() {
    SlimJreConfig config = SlimJreConfig.builder().outputPath(tempDir.resolve("output")).build();

    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("At least one JAR file");
  }

  @Test
  void shouldRejectNonExistentJar() {
    SlimJreConfig config =
        SlimJreConfig.builder()
            .jar(tempDir.resolve("nonexistent.jar"))
            .outputPath(tempDir.resolve("output"))
            .build();

    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldRejectNullOutputPath() throws IOException {
    Path jar = createTempJar("test.jar");

    SlimJreConfig config =
        new SlimJreConfig(
            List.of(jar), null, Set.of(), Set.of(), true, "zip-6", true, true, true, true, false);

    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("Output path must be specified");
  }

  @Test
  void shouldRejectInvalidCompression() throws IOException {
    Path jar = createTempJar("test.jar");

    SlimJreConfig config =
        SlimJreConfig.builder()
            .jar(jar)
            .outputPath(tempDir.resolve("output"))
            .compression("invalid")
            .build();

    assertThatThrownBy(config::validate)
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("Invalid compression level");
  }

  @Test
  void shouldAcceptValidCompressionLevels() throws IOException {
    Path jar = createTempJar("test.jar");

    for (int i = 0; i <= 9; i++) {
      SlimJreConfig config =
          SlimJreConfig.builder()
              .jar(jar)
              .outputPath(tempDir.resolve("output"))
              .compression("zip-" + i)
              .build();

      assertThatCode(config::validate).doesNotThrowAnyException();
    }
  }

  @Test
  void shouldMakeDefensiveCopies() throws IOException {
    Path jar = createTempJar("test.jar");

    var jars = new java.util.ArrayList<>(List.of(jar));
    var additionalModules = new java.util.HashSet<>(Set.of("java.sql"));
    var excludeModules = new java.util.HashSet<>(Set.of("java.desktop"));

    SlimJreConfig config =
        new SlimJreConfig(
            jars,
            tempDir.resolve("output"),
            additionalModules,
            excludeModules,
            true,
            "zip-6",
            true,
            true,
            true,
            true,
            false);

    // Modify original collections
    jars.clear();
    additionalModules.clear();
    excludeModules.clear();

    // Config should retain original values
    assertThat(config.jars()).hasSize(1);
    assertThat(config.additionalModules()).contains("java.sql");
    assertThat(config.excludeModules()).contains("java.desktop");
  }

  private Path createTempJar(String name) throws IOException {
    Path jar = tempDir.resolve(name);
    Files.write(jar, new byte[0]); // Create empty file
    return jar;
  }
}
