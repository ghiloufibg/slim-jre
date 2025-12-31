package com.ghiloufi.slimjre.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.tasks.Jar as JvmJar

/**
 * Gradle plugin for creating minimal custom JREs.
 *
 * This plugin provides two tasks:
 * - `slimJre`: Creates a minimal custom JRE for the project
 * - `slimJreAnalyze`: Analyzes the project to determine required modules (dry-run)
 *
 * Example usage in build.gradle.kts:
 * ```kotlin
 * plugins {
 *     id("com.ghiloufi.slim-jre") version "1.0.0"
 * }
 *
 * slimJre {
 *     outputDirectory.set(layout.buildDirectory.dir("custom-jre"))
 *     additionalModules.add("java.management")
 *     compression.set("zip-9")
 * }
 * ```
 *
 * The plugin automatically:
 * - Detects the project's main JAR artifact
 * - Includes runtime dependencies in the analysis
 * - Wires tasks to run after the `jar` task
 */
class SlimJrePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // React to Java plugin - either already applied or when it gets applied
        project.plugins.withType(JavaPlugin::class.java) {
            configurePlugin(project)
        }
    }

    private fun configurePlugin(project: Project) {
        // Guard against double configuration
        if (project.extensions.findByName(EXTENSION_NAME) != null) {
            return
        }

        // Create extension
        val extension = project.extensions.create(
            EXTENSION_NAME,
            SlimJreExtension::class.java
        )

        // Set default output directory
        extension.outputDirectory.convention(
            project.layout.buildDirectory.dir("slim-jre")
        )

        // Register tasks
        registerCreateJreTask(project, extension)
        registerAnalyzeTask(project, extension)
    }

    private fun registerCreateJreTask(project: Project, extension: SlimJreExtension) {
        project.tasks.register(CREATE_JRE_TASK_NAME, CreateJreTask::class.java) {
            group = "slim-jre"
            description = "Creates a minimal custom JRE for the project"

            onlyIf { !extension.skip.get() }

            // Wire inputs
            inputJars.from(getProjectJars(project))
            outputDirectory.set(extension.outputDirectory)
            additionalModules.set(extension.additionalModules)
            excludeModules.set(extension.excludeModules)
            stripDebug.set(extension.stripDebug)
            compression.set(extension.compression)
            noHeaderFiles.set(extension.noHeaderFiles)
            noManPages.set(extension.noManPages)
            scanServiceLoaders.set(extension.scanServiceLoaders)
            scanGraalVmMetadata.set(extension.scanGraalVmMetadata)
            cryptoMode.set(extension.cryptoMode)
            verbose.set(extension.verbose)

            // Depend on jar task
            dependsOn(project.tasks.named("jar"))
        }
    }

    private fun registerAnalyzeTask(project: Project, extension: SlimJreExtension) {
        project.tasks.register(ANALYZE_TASK_NAME, AnalyzeTask::class.java) {
            group = "slim-jre"
            description = "Analyzes the project to determine required JDK modules (dry-run)"

            onlyIf { !extension.skip.get() }

            // Wire inputs
            inputJars.from(getProjectJars(project))
            additionalModules.set(extension.additionalModules)
            excludeModules.set(extension.excludeModules)
            scanServiceLoaders.set(extension.scanServiceLoaders)
            scanGraalVmMetadata.set(extension.scanGraalVmMetadata)
            verbose.set(extension.verbose)

            // Depend on jar task
            dependsOn(project.tasks.named("jar"))
        }
    }

    private fun getProjectJars(project: Project): List<Any> {
        val jars = mutableListOf<Any>()

        // Add the project's main JAR (lazily resolved)
        jars.add(project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })

        // Add runtime dependencies
        project.configurations.findByName("runtimeClasspath")?.let { config ->
            jars.add(config)
        }

        return jars
    }

    companion object {
        const val EXTENSION_NAME = "slimJre"
        const val CREATE_JRE_TASK_NAME = "slimJre"
        const val ANALYZE_TASK_NAME = "slimJreAnalyze"
    }
}
