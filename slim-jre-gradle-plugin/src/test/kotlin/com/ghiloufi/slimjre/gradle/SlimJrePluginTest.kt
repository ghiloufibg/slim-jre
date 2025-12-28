package com.ghiloufi.slimjre.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class SlimJrePluginTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `plugin registers slimJre extension`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val extension = project.extensions.findByName("slimJre")
        assertThat(extension).isNotNull
        assertThat(extension).isInstanceOf(SlimJreExtension::class.java)
    }

    @Test
    fun `plugin registers slimJre task`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val task = project.tasks.findByName("slimJre")
        assertThat(task).isNotNull
        assertThat(task).isInstanceOf(CreateJreTask::class.java)
    }

    @Test
    fun `plugin registers slimJreAnalyze task`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val task = project.tasks.findByName("slimJreAnalyze")
        assertThat(task).isNotNull
        assertThat(task).isInstanceOf(AnalyzeTask::class.java)
    }

    @Test
    fun `extension has correct default values`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val extension = project.extensions.getByType(SlimJreExtension::class.java)

        assertThat(extension.stripDebug.get()).isTrue()
        assertThat(extension.compression.get()).isEqualTo("zip-6")
        assertThat(extension.noHeaderFiles.get()).isTrue()
        assertThat(extension.noManPages.get()).isTrue()
        assertThat(extension.scanServiceLoaders.get()).isTrue()
        assertThat(extension.verbose.get()).isFalse()
        assertThat(extension.skip.get()).isFalse()
        assertThat(extension.additionalModules.get()).isEmpty()
        assertThat(extension.excludeModules.get()).isEmpty()
    }

    @Test
    fun `extension values can be configured`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val extension = project.extensions.getByType(SlimJreExtension::class.java)

        extension.stripDebug.set(false)
        extension.compression.set("zip-9")
        extension.additionalModules.add("java.management")
        extension.excludeModules.add("java.desktop")
        extension.verbose.set(true)

        assertThat(extension.stripDebug.get()).isFalse()
        assertThat(extension.compression.get()).isEqualTo("zip-9")
        assertThat(extension.additionalModules.get()).containsExactly("java.management")
        assertThat(extension.excludeModules.get()).containsExactly("java.desktop")
        assertThat(extension.verbose.get()).isTrue()
    }

    @Test
    fun `slimJre task has correct group`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val task = project.tasks.getByName("slimJre")
        assertThat(task.group).isEqualTo("slim-jre")
    }

    @Test
    fun `slimJreAnalyze task has correct group`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val task = project.tasks.getByName("slimJreAnalyze")
        assertThat(task.group).isEqualTo("slim-jre")
    }

    @Test
    fun `slimJre task depends on jar task`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val slimJreTask = project.tasks.getByName("slimJre")

        // Check that slimJre depends on jar task (can be via TaskProvider)
        val dependsOnNames = slimJreTask.dependsOn.map { dep ->
            when (dep) {
                is org.gradle.api.tasks.TaskProvider<*> -> dep.name
                is org.gradle.api.Task -> dep.name
                else -> dep.toString()
            }
        }
        assertThat(dependsOnNames).contains("jar")
    }

    @Test
    fun `default output directory is build slash slim-jre`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("java")
        project.plugins.apply("com.ghiloufi.slim-jre")

        val extension = project.extensions.getByType(SlimJreExtension::class.java)
        val outputDir = extension.outputDirectory.get().asFile

        assertThat(outputDir.path).endsWith("build${File.separator}slim-jre")
    }

    @Test
    fun `plugin does not apply without java plugin`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("com.ghiloufi.slim-jre")

        // Extension should not be registered without Java plugin
        val extension = project.extensions.findByName("slimJre")
        assertThat(extension).isNull()
    }

    @Test
    fun `plugin applies when java plugin is applied later`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(testProjectDir)
            .build()

        project.plugins.apply("com.ghiloufi.slim-jre")
        project.plugins.apply("java")

        val extension = project.extensions.findByName("slimJre")
        assertThat(extension).isNotNull
    }
}
