package com.ghiloufi.slimjre.gradle

import com.ghiloufi.slimjre.config.CryptoMode
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Extension for configuring the Slim JRE plugin.
 *
 * Example usage in build.gradle.kts:
 * ```kotlin
 * slimJre {
 *     outputDirectory.set(layout.buildDirectory.dir("slim-jre"))
 *     stripDebug.set(true)
 *     compression.set("zip-6")
 *     additionalModules.add("java.management")
 *     excludeModules.add("java.desktop")
 * }
 * ```
 */
abstract class SlimJreExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Output directory for the slim JRE.
     * Default: ${buildDir}/slim-jre
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Additional modules to include beyond those detected.
     * Default: empty
     */
    abstract val additionalModules: SetProperty<String>

    /**
     * Modules to exclude from the final JRE.
     * Default: empty
     */
    abstract val excludeModules: SetProperty<String>

    /**
     * Whether to strip debug information from the JRE.
     * Default: true
     */
    abstract val stripDebug: Property<Boolean>

    /**
     * Compression level: zip-0 to zip-9.
     * Default: zip-6
     */
    abstract val compression: Property<String>

    /**
     * Whether to exclude header files from the JRE.
     * Default: true
     */
    abstract val noHeaderFiles: Property<Boolean>

    /**
     * Whether to exclude man pages from the JRE.
     * Default: true
     */
    abstract val noManPages: Property<Boolean>

    /**
     * Whether to scan for service loader dependencies.
     * Default: true
     */
    abstract val scanServiceLoaders: Property<Boolean>

    /**
     * Whether to scan GraalVM native-image metadata for additional modules.
     * Default: true
     */
    abstract val scanGraalVmMetadata: Property<Boolean>

    /**
     * Controls how SSL/TLS and cryptographic module requirements are handled.
     * - AUTO (default): Automatically detect SSL/TLS usage and include crypto modules if needed
     * - ALWAYS: Always include crypto modules regardless of detection
     * - NEVER: Never include crypto modules, even if SSL/TLS usage is detected
     */
    abstract val cryptoMode: Property<CryptoMode>

    /**
     * Whether to output verbose logging.
     * Default: false
     */
    abstract val verbose: Property<Boolean>

    /**
     * Whether to skip execution of the plugin.
     * Default: false
     */
    abstract val skip: Property<Boolean>

    /**
     * Additional JAR files to include in the analysis.
     * By default, the plugin analyzes the project's JAR and runtime dependencies.
     */
    abstract val additionalJars: ListProperty<RegularFileProperty>

    init {
        // Set defaults
        stripDebug.convention(true)
        compression.convention("zip-6")
        noHeaderFiles.convention(true)
        noManPages.convention(true)
        scanServiceLoaders.convention(true)
        scanGraalVmMetadata.convention(true)
        cryptoMode.convention(CryptoMode.AUTO)
        verbose.convention(false)
        skip.convention(false)
        additionalModules.convention(emptySet())
        excludeModules.convention(emptySet())
    }
}
