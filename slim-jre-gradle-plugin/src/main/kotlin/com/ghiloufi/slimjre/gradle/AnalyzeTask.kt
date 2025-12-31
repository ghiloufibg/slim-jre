package com.ghiloufi.slimjre.gradle

import com.ghiloufi.slimjre.config.AnalysisResult
import com.ghiloufi.slimjre.core.SlimJre
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.nio.file.Path
import java.util.TreeSet

/**
 * Task that analyzes the project to determine required JDK modules.
 *
 * This is a dry-run task useful for debugging and understanding
 * what modules your application requires.
 */
abstract class AnalyzeTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ConfigurableFileCollection

    @get:Input
    abstract val additionalModules: SetProperty<String>

    @get:Input
    abstract val excludeModules: SetProperty<String>

    @get:Input
    abstract val scanServiceLoaders: Property<Boolean>

    @get:Input
    abstract val scanGraalVmMetadata: Property<Boolean>

    @get:Input
    abstract val verbose: Property<Boolean>

    init {
        group = "slim-jre"
        description = "Analyzes the project to determine required JDK modules (dry-run)"

        // This task should never be cached as it's meant for inspection
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun analyze() {
        val jars = inputJars.files
            .filter { it.exists() && it.name.endsWith(".jar") }
            .map { it.toPath() }

        if (jars.isEmpty()) {
            logger.warn("No JAR files found. Ensure the project has been built.")
            return
        }

        logger.lifecycle("Analyzing ${jars.size} JAR(s) for module dependencies...")

        if (verbose.get()) {
            jars.forEach { jar ->
                logger.lifecycle("  - ${jar.fileName}")
            }
            logger.lifecycle("")
        }

        val slimJre = SlimJre()
        val result = slimJre.analyzeOnly(jars, scanServiceLoaders.get(), scanGraalVmMetadata.get())

        logAnalysis(result, jars)
    }

    private fun logAnalysis(result: AnalysisResult, jars: List<Path>) {
        // Combine with additional/excluded modules
        val allModules = TreeSet(result.allModules())
        allModules.addAll(additionalModules.get())
        allModules.removeAll(excludeModules.get())

        logger.lifecycle("")
        logger.lifecycle("=== Module Analysis ===")
        logger.lifecycle("")

        logger.lifecycle("Required Modules (jdeps): ${result.requiredModules().size}")
        result.requiredModules().sorted().forEach { module ->
            logger.lifecycle("  - $module")
        }

        if (result.serviceLoaderModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Service Loader Modules: ${result.serviceLoaderModules().size}")
            result.serviceLoaderModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.reflectionModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Reflection Modules: ${result.reflectionModules().size}")
            result.reflectionModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.apiUsageModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("API Usage Modules: ${result.apiUsageModules().size}")
            result.apiUsageModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.graalVmMetadataModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("GraalVM Metadata Modules: ${result.graalVmMetadataModules().size}")
            result.graalVmMetadataModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.cryptoModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Crypto Modules (SSL/TLS): ${result.cryptoModules().size}")
            result.cryptoModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.localeModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Locale Modules (i18n): ${result.localeModules().size}")
            result.localeModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.zipFsModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("ZipFS Modules (ZIP filesystem): ${result.zipFsModules().size}")
            result.zipFsModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (result.jmxModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("JMX Modules (remote management): ${result.jmxModules().size}")
            result.jmxModules().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        if (additionalModules.get().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Additional Modules (configured): ${additionalModules.get().size}")
            additionalModules.get().sorted().forEach { module ->
                logger.lifecycle("  + $module")
            }
        }

        if (excludeModules.get().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Excluded Modules (configured): ${excludeModules.get().size}")
            excludeModules.get().sorted().forEach { module ->
                logger.lifecycle("  - $module")
            }
        }

        logger.lifecycle("")
        logger.lifecycle("Total Modules: ${allModules.size}")
        logger.lifecycle("  ${allModules.joinToString(",")}")

        if (verbose.get() && result.perJarModules().isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("=== Per-JAR Breakdown ===")
            result.perJarModules().forEach { (jar, modules) ->
                logger.lifecycle("")
                logger.lifecycle("${jar.fileName}:")
                modules.sorted().forEach { module ->
                    logger.lifecycle("  - $module")
                }
            }
        }

        // Print jlink command
        logger.lifecycle("")
        logger.lifecycle("=== jlink Command ===")
        logger.lifecycle(
            "jlink --add-modules ${allModules.joinToString(",")} " +
                "--strip-debug --compress zip-6 --no-header-files --no-man-pages --output slim-jre"
        )
    }
}
