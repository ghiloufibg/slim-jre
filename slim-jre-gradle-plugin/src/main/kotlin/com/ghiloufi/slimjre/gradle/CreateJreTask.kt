package com.ghiloufi.slimjre.gradle

import com.ghiloufi.slimjre.config.Result
import com.ghiloufi.slimjre.config.SlimJreConfig
import com.ghiloufi.slimjre.core.SlimJre
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.nio.file.Path

/**
 * Task that creates a minimal custom JRE for the project.
 *
 * This task analyzes the project's JAR and its dependencies to determine
 * the required JDK modules, then uses jlink to create a slim JRE.
 */
@CacheableTask
abstract class CreateJreTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val additionalModules: SetProperty<String>

    @get:Input
    abstract val excludeModules: SetProperty<String>

    @get:Input
    abstract val stripDebug: Property<Boolean>

    @get:Input
    abstract val compression: Property<String>

    @get:Input
    abstract val noHeaderFiles: Property<Boolean>

    @get:Input
    abstract val noManPages: Property<Boolean>

    @get:Input
    abstract val scanServiceLoaders: Property<Boolean>

    @get:Input
    abstract val verbose: Property<Boolean>

    init {
        group = "slim-jre"
        description = "Creates a minimal custom JRE for the project"
    }

    @TaskAction
    fun createJre() {
        val jars = inputJars.files
            .filter { it.exists() && it.name.endsWith(".jar") }
            .map { it.toPath() }

        if (jars.isEmpty()) {
            logger.warn("No JAR files found. Ensure the project has been built.")
            return
        }

        logger.lifecycle("Creating minimal JRE for ${jars.size} JAR(s)...")

        if (verbose.get()) {
            jars.forEach { jar ->
                logger.lifecycle("  - ${jar.fileName}")
            }
        }

        val config = buildConfig(jars)
        val slimJre = SlimJre()
        val result = slimJre.createMinimalJre(config)

        logResult(result)
    }

    private fun buildConfig(jars: List<Path>): SlimJreConfig {
        return SlimJreConfig.builder()
            .jars(jars)
            .outputPath(outputDirectory.get().asFile.toPath())
            .additionalModules(additionalModules.get())
            .excludeModules(excludeModules.get())
            .stripDebug(stripDebug.get())
            .compression(compression.get())
            .noHeaderFiles(noHeaderFiles.get())
            .noManPages(noManPages.get())
            .scanServiceLoaders(scanServiceLoaders.get())
            .verbose(verbose.get())
            .build()
    }

    private fun logResult(result: Result) {
        logger.lifecycle("")
        logger.lifecycle("Slim JRE created successfully!")
        logger.lifecycle("  Path: ${result.jrePath()}")
        logger.lifecycle("  Modules: ${result.includedModules().size}")
        logger.lifecycle("  Size: ${formatSize(result.slimJreSize())}")

        if (result.originalJreSize() > 0) {
            logger.lifecycle("  Reduction: ${String.format("%.0f%%", result.reductionPercentage())}")
        }

        logger.lifecycle("  Time: ${formatDuration(result.duration().toMillis())}")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        }
    }

    private fun formatDuration(millis: Long): String {
        return if (millis < 1000) {
            "${millis}ms"
        } else {
            String.format("%.1fs", millis / 1000.0)
        }
    }
}
