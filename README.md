# Slim JRE

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![Status](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow.svg)]()

> **Note**: This project is currently in **alpha development**. It has not been thoroughly tested on real-world production applications. Use with caution and please report any issues you encounter.

**A tool for automatically creating minimal custom JREs for Java applications using jdeps and jlink.**

Slim JRE aims to simplify the process of creating custom Java runtimes by analyzing your application's bytecode to determine which JDK modules are needed, then using jlink to create a smaller runtime.

## Project Goals

Creating custom JREs with jlink can be challenging:
- Requires understanding of jdeps analysis and module dependencies
- Non-modular applications need workarounds
- Service loaders and reflection dependencies can be missed
- Limited tooling for Maven/Gradle integration

**Slim JRE is being developed to address these challenges** through automated bytecode analysis and sensible defaults.

## Current Status

This project is in **early alpha** (v1.0.0-alpha.1). The following components are implemented but need real-world testing:

- [x] Core analysis library
- [x] Maven plugin
- [x] Gradle plugin (basic)
- [x] CLI tool
- [x] GUI application
- [ ] Production testing
- [ ] Performance benchmarking
- [ ] Edge case handling

## Getting Started

### Maven Plugin

Add to your `pom.xml`:

```xml
<plugin>
    <groupId>com.ghiloufi</groupId>
    <artifactId>slim-jre-maven-plugin</artifactId>
    <version>1.0.0-alpha.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>create-jre</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Run:
```bash
mvn package
```

The custom JRE will be created at `target/slim-jre/`.

### Command Line

```bash
# Basic usage
slim-jre myapp.jar

# With options
slim-jre myapp.jar -o custom-runtime --compress zip-9 --verbose

# Analysis only (dry-run)
slim-jre myapp.jar --analyze-only
```

### Gradle Plugin

```kotlin
plugins {
    id("com.ghiloufi.slim-jre") version "1.0.0-alpha.1"
}

slimJre {
    verbose.set(true)
    compression.set("zip-9")
}
```

### Programmatic API

```java
var result = SlimJre.builder()
    .jar(Path.of("myapp.jar"))
    .outputPath(Path.of("custom-jre"))
    .build()
    .create();

System.out.println(result.summary());
```

## Planned Features

### Analysis Capabilities

- **Bytecode scanning** - Detect reflection patterns like `Class.forName()`
- **Service loader detection** - Scan `META-INF/services` for runtime dependencies
- **API usage analysis** - Identify JDK APIs used in code
- **Transitive resolution** - Include module dependencies automatically

### Special Module Detection

The tool attempts to detect requirements for:

| Feature | Module |
|---------|--------|
| Cryptography | `jdk.crypto.ec`, `jdk.crypto.cryptoki` |
| Locale data | `jdk.localedata` |
| ZipFileSystem | `jdk.zipfs` |
| JMX Remote | `java.management.rmi` |
| GraalVM | Native-image metadata |

### Configuration Options

```xml
<configuration>
    <!-- Compression (zip-0 to zip-9, default: zip-6) -->
    <compression>zip-9</compression>

    <!-- Remove debug symbols (default: true) -->
    <stripDebug>true</stripDebug>

    <!-- Remove header files (default: true) -->
    <noHeaderFiles>true</noHeaderFiles>

    <!-- Remove man pages (default: true) -->
    <noManPages>true</noManPages>

    <!-- Force-include modules -->
    <includeModules>
        <module>java.management</module>
    </includeModules>

    <!-- Exclude modules -->
    <excludeModules>
        <module>java.desktop</module>
    </excludeModules>
</configuration>
```

## Requirements

- **JDK 21+** (for building and running)
- **Maven 3.9+** (for Maven plugin)
- **Gradle 8+** (for Gradle plugin)

## Project Structure

```
slim-jre/
├── slim-jre-core          # Core analysis & JRE creation library
├── slim-jre-cli           # Command-line interface
├── slim-jre-maven-plugin  # Maven plugin
├── slim-jre-gradle-plugin # Gradle plugin
├── slim-jre-gui           # Swing GUI application
└── slim-jre-examples/     # Example applications for testing
    ├── simple-app
    ├── spring-boot-hello
    ├── quarkus-hello
    ├── micronaut-hello
    └── ...
```

## CLI Options

```
Usage: slim-jre [OPTIONS] <jar-or-directory>

Options:
  -o, --output <dir>       Output directory (default: ./slim-jre)
  -cp, --classpath <path>  Additional classpath entries
  --add-modules <modules>  Force-include modules (comma-separated)
  --exclude-modules <m>    Exclude modules (comma-separated)
  --compress <level>       Compression (zip-0 to zip-9, default: zip-6)
  --no-strip               Don't strip debug information
  --no-service-scan        Don't scan for service loaders
  --analyze-only           Print required modules without creating JRE
  --verbose                Verbose output
  -h, --help               Show help
  -V, --version            Print version
```

## Maven Goals

| Goal | Description |
|------|-------------|
| `slim-jre:create-jre` | Creates custom JRE (default phase: package) |
| `slim-jre:analyze` | Dry-run analysis, prints required modules |

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ghiloufi/slim-jre.git
cd slim-jre

# Build all modules
mvn clean install

# Run tests
mvn test

# Build CLI executable
mvn package -pl slim-jre-cli -am

# Build GUI executable
mvn package -pl slim-jre-gui -am
```

## Example Applications

The `slim-jre-examples/` directory contains example applications for testing:

### Basic Examples
- `simple-app` - Minimal Java application
- `http-client-app` - HTTP client with SSL
- `logging-app` - Java util logging
- `file-processing-app` - NIO file operations

### Framework Examples
- `spring-boot-hello` - Spring Boot web application
- `spring-boot-enterprise` - Spring Boot with JPA, security
- `quarkus-hello` - Quarkus REST application
- `quarkus-enterprise` - Quarkus with Hibernate, security
- `micronaut-hello` - Micronaut web application
- `microprofile-hello` - MicroProfile REST application

### Feature Examples
- `reflection-app` - Dynamic class loading patterns
- `sql-app` - JDBC database access
- `locale-app` - Internationalization
- `jmx-app` - JMX remote monitoring
- `zipfs-app` - ZIP filesystem operations

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.