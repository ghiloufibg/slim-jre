plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "com.ghiloufi"
version = "1.0.0-alpha.1"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.ghiloufi:slim-jre-core:1.0.0-alpha.1")
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    website.set("https://github.com/ghiloufi/slim-jre")
    vcsUrl.set("https://github.com/ghiloufi/slim-jre.git")

    plugins {
        create("slimJre") {
            id = "com.ghiloufi.slim-jre"
            displayName = "Slim JRE Plugin"
            description = "Creates minimal custom JREs for Java applications using jdeps and jlink"
            tags.set(listOf("java", "jlink", "jre", "optimization", "docker"))
            implementationClass = "com.ghiloufi.slimjre.gradle.SlimJrePlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Publishing is handled by com.gradle.plugin-publish plugin
// Use 'publishPlugins' task to publish to Gradle Plugin Portal
// Use 'publishToMavenLocal' for local testing
