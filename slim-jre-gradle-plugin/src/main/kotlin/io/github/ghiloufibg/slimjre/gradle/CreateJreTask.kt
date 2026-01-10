package io.github.ghiloufibg.slimjre.gradle

import io.github.ghiloufibg.slimjre.config.CryptoMode
import io.github.ghiloufibg.slimjre.config.Result
import io.github.ghiloufibg.slimjre.config.SlimJreConfig
import io.github.ghiloufibg.slimjre.core.SlimJre
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.io.File
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

    /**
     * Optional custom input path (JAR file or directory) to analyze.
     * If set, this overrides the default inputJars.
     * Note: Using @Internal because this can be either a file or directory.
     * The actual inputs are tracked via inputJars in the default case,
     * or via the JAR files discovered from this path.
     */
    @get:Internal
    abstract val customInputPath: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val includeModules: SetProperty<String>

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
    abstract val scanGraalVmMetadata: Property<Boolean>

    @get:Input
    abstract val cryptoMode: Property<CryptoMode>

    @get:Input
    abstract val verbose: Property<Boolean>

    init {
        group = "slim-jre"
        description = "Creates a minimal custom JRE for the project"
    }

    @TaskAction
    fun createJre() {
        val jars = collectJars()

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

    private fun collectJars(): List<Path> {
        // If custom input path is specified, use that
        if (customInputPath.isPresent) {
            val inputFile = customInputPath.get().asFile
            if (!inputFile.exists()) {
                throw IllegalArgumentException("Specified input path not found: $inputFile")
            }

            return if (inputFile.isDirectory) {
                // Collect all JARs from the directory
                val jarFiles = inputFile.listFiles { _, name -> name.endsWith(".jar") }
                if (jarFiles.isNullOrEmpty()) {
                    throw IllegalArgumentException("No JAR files found in directory: $inputFile")
                }
                logger.lifecycle("Using custom input directory: $inputFile")
                jarFiles.map { it.toPath() }
            } else {
                // Single JAR file
                logger.lifecycle("Using custom input artifact: $inputFile")
                listOf(inputFile.toPath())
            }
        }

        // Default: use inputJars from project
        return inputJars.files
            .filter { it.exists() && it.name.endsWith(".jar") }
            .map { it.toPath() }
    }

    private fun buildConfig(jars: List<Path>): SlimJreConfig {
        return SlimJreConfig.builder()
            .jars(jars)
            .outputPath(outputDirectory.get().asFile.toPath())
            .includeModules(includeModules.get())
            .excludeModules(excludeModules.get())
            .stripDebug(stripDebug.get())
            .compression(compression.get())
            .noHeaderFiles(noHeaderFiles.get())
            .noManPages(noManPages.get())
            .scanServiceLoaders(scanServiceLoaders.get())
            .scanGraalVmMetadata(scanGraalVmMetadata.get())
            .cryptoMode(cryptoMode.get())
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
