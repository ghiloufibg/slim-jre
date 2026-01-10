package io.github.ghiloufibg.slimjre.core;

import static org.assertj.core.api.Assertions.*;

import io.github.ghiloufibg.slimjre.exception.ModuleResolutionException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for ModuleResolver. */
class ModuleResolverTest {

  private ModuleResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new ModuleResolver();
  }

  @Test
  void shouldDiscoverAvailableModules() {
    Set<String> modules = resolver.availableModules();

    assertThat(modules).isNotEmpty().contains("java.base", "java.logging", "java.sql");
  }

  @Test
  void shouldCheckModuleAvailability() {
    assertThat(resolver.isAvailable("java.base")).isTrue();
    assertThat(resolver.isAvailable("java.sql")).isTrue();
    assertThat(resolver.isAvailable("nonexistent.module")).isFalse();
  }

  @Test
  void shouldResolveTransitiveDependencies() {
    // java.sql requires java.base, java.xml, java.logging, java.transaction.xa
    Set<String> resolved = resolver.resolveWithTransitive(Set.of("java.sql"));

    assertThat(resolved).contains("java.sql", "java.base", "java.xml", "java.logging");
  }

  @Test
  void shouldAlwaysIncludeJavaBase() {
    Set<String> resolved = resolver.resolveWithTransitive(Set.of("java.logging"));

    assertThat(resolved).contains("java.base");
  }

  @Test
  void shouldHandleEmptyInput() {
    Set<String> resolved = resolver.resolveWithTransitive(Set.of());

    assertThat(resolved).containsExactly("java.base");
  }

  @Test
  void shouldSkipNonJdkModules() {
    // Application modules should be skipped, not cause errors
    Set<String> resolved = resolver.resolveWithTransitive(Set.of("java.base", "com.example.myapp"));

    assertThat(resolved).contains("java.base").doesNotContain("com.example.myapp");
  }

  @Test
  void shouldThrowForMissingJdkModule() {
    assertThatThrownBy(() -> resolver.resolveWithTransitive(Set.of("java.nonexistent")))
        .isInstanceOf(ModuleResolutionException.class)
        .hasMessageContaining("java.nonexistent");
  }

  @Test
  void shouldGetDirectDependencies() {
    Set<String> deps = resolver.getDirectDependencies("java.sql");

    assertThat(deps).isNotEmpty();
  }

  @Test
  void shouldReturnEmptyForUnknownModule() {
    Set<String> deps = resolver.getDirectDependencies("unknown.module");

    assertThat(deps).isEmpty();
  }

  @Test
  void shouldFilterToAvailableModules() {
    Set<String> input = Set.of("java.base", "java.sql", "com.example.app", "nonexistent");
    Set<String> filtered = resolver.filterToAvailable(input);

    assertThat(filtered)
        .contains("java.base", "java.sql")
        .doesNotContain("com.example.app", "nonexistent");
  }

  @Test
  void shouldProvideOptionalModulesList() {
    Set<String> optional = resolver.getOptionalModules();

    assertThat(optional).contains("java.desktop", "java.rmi", "java.compiler");
  }
}
