package com.ghiloufi.slimjre.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class SlimJrePluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")

        settingsFile.writeText("""
            rootProject.name = "test-project"
        """.trimIndent())
    }

    @Test
    fun `can apply plugin`() {
        buildFile.writeText("""
            plugins {
                java
                id("com.ghiloufi.slim-jre")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--group=slim-jre")
            .withPluginClasspath()
            .build()

        assertThat(result.output).contains("slimJre")
        assertThat(result.output).contains("slimJreAnalyze")
    }

    @Test
    fun `can configure extension`() {
        buildFile.writeText("""
            plugins {
                java
                id("com.ghiloufi.slim-jre")
            }

            slimJre {
                compression.set("zip-9")
                includeModules.add("java.management")
                verbose.set(true)
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--group=slim-jre")
            .withPluginClasspath()
            .build()

        assertThat(result.output).contains("slimJre")
    }

    @Test
    fun `slimJreAnalyze task runs successfully with simple java project`() {
        // Create a simple Java source file
        val srcDir = File(testProjectDir, "src/main/java/com/example")
        srcDir.mkdirs()

        File(srcDir, "App.java").writeText("""
            package com.example;

            public class App {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        """.trimIndent())

        buildFile.writeText("""
            plugins {
                java
                id("com.ghiloufi.slim-jre")
            }

            slimJre {
                verbose.set(true)
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("slimJreAnalyze", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertThat(result.task(":slimJreAnalyze")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Module Analysis")
        assertThat(result.output).contains("java.base")
    }

    @Test
    fun `skip property prevents task execution`() {
        // Create a simple Java source file
        val srcDir = File(testProjectDir, "src/main/java/com/example")
        srcDir.mkdirs()

        File(srcDir, "App.java").writeText("""
            package com.example;
            public class App { }
        """.trimIndent())

        buildFile.writeText("""
            plugins {
                java
                id("com.ghiloufi.slim-jre")
            }

            slimJre {
                skip.set(true)
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("slimJreAnalyze")
            .withPluginClasspath()
            .build()

        assertThat(result.task(":slimJreAnalyze")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
    }

    @Test
    fun `custom output directory is used`() {
        // Create a simple Java source file
        val srcDir = File(testProjectDir, "src/main/java/com/example")
        srcDir.mkdirs()

        File(srcDir, "App.java").writeText("""
            package com.example;
            public class App {
                public static void main(String[] args) { }
            }
        """.trimIndent())

        buildFile.writeText("""
            plugins {
                java
                id("com.ghiloufi.slim-jre")
            }

            slimJre {
                outputDirectory.set(layout.buildDirectory.dir("custom-jre"))
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("slimJre", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertThat(result.task(":slimJre")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val customJreDir = File(testProjectDir, "build/custom-jre")
        assertThat(customJreDir).exists()
    }
}
