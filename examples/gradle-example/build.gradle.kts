plugins {
    java
    id("com.ghiloufi.slim-jre") version "1.0.0-alpha.1"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

slimJre {
    verbose.set(true)
    compression.set("zip-9")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.example.App"
        )
    }
}
