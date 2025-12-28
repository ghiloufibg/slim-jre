package com.ghiloufi.slimjre.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans JARs for META-INF/services declarations to detect implicit module dependencies. Service
 * loaders often cause runtime failures because jdeps can't detect them statically.
 */
public class ServiceLoaderScanner {

  private static final Logger log = LoggerFactory.getLogger(ServiceLoaderScanner.class);
  private static final String SERVICES_PREFIX = "META-INF/services/";

  /** Mapping of known service interfaces to their required JDK modules. */
  private static final Map<String, String> SERVICE_TO_MODULE = createServiceMappings();

  private static Map<String, String> createServiceMappings() {
    Map<String, String> map = new HashMap<>();

    // java.sql module
    map.put("java.sql.Driver", "java.sql");
    map.put("javax.sql.DataSource", "java.sql");

    // java.xml module
    map.put("javax.xml.parsers.DocumentBuilderFactory", "java.xml");
    map.put("javax.xml.parsers.SAXParserFactory", "java.xml");
    map.put("javax.xml.stream.XMLInputFactory", "java.xml");
    map.put("javax.xml.stream.XMLOutputFactory", "java.xml");
    map.put("javax.xml.stream.XMLEventFactory", "java.xml");
    map.put("javax.xml.transform.TransformerFactory", "java.xml");
    map.put("javax.xml.validation.SchemaFactory", "java.xml");
    map.put("javax.xml.xpath.XPathFactory", "java.xml");
    map.put("org.xml.sax.driver", "java.xml");

    // java.logging module
    map.put("java.util.logging.Handler", "java.logging");

    // java.naming module
    map.put("javax.naming.spi.InitialContextFactory", "java.naming");
    map.put("javax.naming.ldap.StartTlsResponse", "java.naming");

    // java.scripting module
    map.put("javax.script.ScriptEngineFactory", "java.scripting");

    // java.management module
    map.put("javax.management.MBeanServer", "java.management");
    map.put("java.lang.management.PlatformManagedObject", "java.management");

    // java.security.jgss module
    map.put("org.ietf.jgss.GSSManager", "java.security.jgss");

    // java.security.sasl module
    map.put("javax.security.sasl.SaslClientFactory", "java.security.sasl");
    map.put("javax.security.sasl.SaslServerFactory", "java.security.sasl");

    // java.smartcardio module
    map.put("javax.smartcardio.TerminalFactory", "java.smartcardio");

    // java.compiler module
    map.put("javax.tools.JavaCompiler", "java.compiler");
    map.put("javax.annotation.processing.Processor", "java.compiler");

    // java.net.http module
    map.put("java.net.http.HttpClient", "java.net.http");

    // java.prefs module
    map.put("java.util.prefs.PreferencesFactory", "java.prefs");

    // java.rmi module
    map.put("java.rmi.server.RMIClassLoaderSpi", "java.rmi");

    // jdk.jsobject module
    map.put("netscape.javascript.JSObject", "jdk.jsobject");

    // jdk.httpserver module
    map.put("com.sun.net.httpserver.HttpHandler", "jdk.httpserver");

    // Charset providers (java.base)
    map.put("java.nio.charset.spi.CharsetProvider", "java.base");

    // File system providers (java.base)
    map.put("java.nio.file.spi.FileSystemProvider", "java.base");

    // Security providers (java.base)
    map.put("java.security.Provider", "java.base");

    // Sound (java.desktop)
    map.put("javax.sound.sampled.spi.AudioFileReader", "java.desktop");
    map.put("javax.sound.sampled.spi.AudioFileWriter", "java.desktop");
    map.put("javax.sound.midi.spi.MidiDeviceProvider", "java.desktop");

    // Image I/O (java.desktop)
    map.put("javax.imageio.spi.ImageReaderSpi", "java.desktop");
    map.put("javax.imageio.spi.ImageWriterSpi", "java.desktop");

    // Print service (java.desktop)
    map.put("javax.print.PrintServiceLookup", "java.desktop");

    return Collections.unmodifiableMap(map);
  }

  /**
   * Scans JARs for service loader declarations and maps them to required modules.
   *
   * @param jars JARs to scan
   * @return set of additional module names required by service loaders
   */
  public Set<String> scanForServiceModules(List<Path> jars) {
    Set<String> modules = new TreeSet<>();
    Set<String> unknownServices = new TreeSet<>();

    for (Path jar : jars) {
      Set<String> services = scanJarForServices(jar);

      for (String service : services) {
        String module = SERVICE_TO_MODULE.get(service);
        if (module != null) {
          modules.add(module);
          log.debug("Service {} in {} requires module {}", service, jar.getFileName(), module);
        } else {
          // Check if it's a package prefix match
          String moduleFromPackage = findModuleByPackage(service);
          if (moduleFromPackage != null) {
            modules.add(moduleFromPackage);
            log.debug(
                "Service {} (by package) in {} requires module {}",
                service,
                jar.getFileName(),
                moduleFromPackage);
          } else {
            unknownServices.add(service);
          }
        }
      }
    }

    if (!unknownServices.isEmpty()) {
      log.debug("Unknown service interfaces (may be application-defined): {}", unknownServices);
    }

    return modules;
  }

  /**
   * Scans a single JAR for service declarations.
   *
   * @param jar JAR file to scan
   * @return set of service interface names found
   */
  private Set<String> scanJarForServices(Path jar) {
    Set<String> services = new TreeSet<>();

    try (JarFile jarFile = new JarFile(jar.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();

      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        if (name.startsWith(SERVICES_PREFIX) && !name.equals(SERVICES_PREFIX)) {
          String serviceName = name.substring(SERVICES_PREFIX.length());
          // Ignore nested directories
          if (!serviceName.contains("/")) {
            services.add(serviceName);
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to scan JAR for services: {}: {}", jar.getFileName(), e.getMessage());
    }

    return services;
  }

  /** Attempts to find a module based on package prefix patterns. */
  private String findModuleByPackage(String serviceInterface) {
    // Common package to module mappings
    if (serviceInterface.startsWith("java.sql.") || serviceInterface.startsWith("javax.sql.")) {
      return "java.sql";
    }
    if (serviceInterface.startsWith("javax.xml.")) {
      return "java.xml";
    }
    if (serviceInterface.startsWith("javax.naming.")) {
      return "java.naming";
    }
    if (serviceInterface.startsWith("javax.script.")) {
      return "java.scripting";
    }
    if (serviceInterface.startsWith("javax.management.")) {
      return "java.management";
    }
    if (serviceInterface.startsWith("java.util.logging.")) {
      return "java.logging";
    }
    if (serviceInterface.startsWith("javax.security.")) {
      return "java.base";
    }
    if (serviceInterface.startsWith("java.net.http.")) {
      return "java.net.http";
    }
    if (serviceInterface.startsWith("java.util.prefs.")) {
      return "java.prefs";
    }
    if (serviceInterface.startsWith("java.rmi.")) {
      return "java.rmi";
    }
    if (serviceInterface.startsWith("javax.sound.")
        || serviceInterface.startsWith("javax.imageio.")
        || serviceInterface.startsWith("javax.print.")
        || serviceInterface.startsWith("javax.swing.")
        || serviceInterface.startsWith("java.awt.")) {
      return "java.desktop";
    }

    return null;
  }

  /** Returns all known service-to-module mappings. Useful for debugging and documentation. */
  public Map<String, String> getKnownServiceMappings() {
    return SERVICE_TO_MODULE;
  }

  /**
   * Scans all JARs and returns the raw service interface names found.
   *
   * @param jars JARs to scan
   * @return set of service interface names
   */
  public Set<String> scanForServiceInterfaces(List<Path> jars) {
    Set<String> services = new TreeSet<>();
    for (Path jar : jars) {
      services.addAll(scanJarForServices(jar));
    }
    return services;
  }

  /**
   * Scans JARs for service loader declarations in parallel using virtual threads. This method
   * leverages Java 21's virtual threads for efficient parallel I/O operations.
   *
   * @param jars JARs to scan
   * @return set of additional module names required by service loaders
   */
  public Set<String> scanForServiceModulesParallel(List<Path> jars) {
    if (jars == null || jars.isEmpty()) {
      return Set.of();
    }

    // For small numbers of JARs, sequential is fine
    if (jars.size() <= 2) {
      return scanForServiceModules(jars);
    }

    Set<String> modules = ConcurrentHashMap.newKeySet();
    Set<String> unknownServices = ConcurrentHashMap.newKeySet();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // Scan all JARs in parallel
      List<Future<Set<String>>> futures =
          jars.stream().map(jar -> executor.submit(() -> scanJarForServices(jar))).toList();

      // Collect all service interfaces
      Set<String> allServices = ConcurrentHashMap.newKeySet();
      for (Future<Set<String>> future : futures) {
        try {
          allServices.addAll(future.get());
        } catch (Exception e) {
          log.warn("Failed to get service scan result: {}", e.getMessage());
        }
      }

      // Map services to modules (this is fast, no need to parallelize)
      for (String service : allServices) {
        String module = SERVICE_TO_MODULE.get(service);
        if (module != null) {
          modules.add(module);
          log.debug("Service {} requires module {}", service, module);
        } else {
          String moduleFromPackage = findModuleByPackage(service);
          if (moduleFromPackage != null) {
            modules.add(moduleFromPackage);
            log.debug("Service {} (by package) requires module {}", service, moduleFromPackage);
          } else {
            unknownServices.add(service);
          }
        }
      }
    }

    if (!unknownServices.isEmpty()) {
      log.debug("Unknown service interfaces (may be application-defined): {}", unknownServices);
    }

    return new TreeSet<>(modules);
  }
}
