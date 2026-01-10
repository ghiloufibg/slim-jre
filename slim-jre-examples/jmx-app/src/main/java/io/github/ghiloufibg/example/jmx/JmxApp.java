package io.github.ghiloufibg.example.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Demonstrates remote JMX features that require java.management.rmi module.
 *
 * <p>This application uses the remote JMX connector to connect to JMX servers. The JmxModuleScanner
 * should detect these patterns:
 *
 * <ul>
 *   <li>JMXConnectorFactory - Factory for creating JMX connections
 *   <li>JMXServiceURL - URL for JMX service endpoints
 *   <li>JMXConnector - Interface for remote JMX connections
 * </ul>
 *
 * <p>Note: Local JMX (javax.management.* without remote package) does NOT require
 * java.management.rmi. The scanner correctly distinguishes between local and remote JMX usage.
 *
 * <p>Without java.management.rmi, remote JMX connections will fail.
 */
public class JmxApp {

  public static void main(String[] args) {
    System.out.println("=== Remote JMX Application Demo ===\n");

    // First, demonstrate local JMX (doesn't need java.management.rmi)
    demonstrateLocalJmx();

    // Then, demonstrate remote JMX patterns (requires java.management.rmi)
    demonstrateRemoteJmxPatterns();

    System.out.println("\n=== Demo Complete ===");
  }

  /**
   * Demonstrates local JMX usage.
   *
   * <p>This uses javax.management (NOT javax.management.remote) and does NOT require
   * java.management.rmi module.
   */
  private static void demonstrateLocalJmx() {
    System.out.println("--- Local JMX Demo (no extra module needed) ---");

    // Get local MBean server (built into java.management)
    var mbeanServer = ManagementFactory.getPlatformMBeanServer();
    System.out.println("Local MBean Server: " + mbeanServer);

    // Access runtime info via local MXBean
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    System.out.println("JVM Name: " + runtimeMxBean.getVmName());
    System.out.println("JVM Uptime: " + runtimeMxBean.getUptime() + " ms");

    // Access memory info via local MXBean
    MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
    System.out.println(
        "Heap Memory Used: " + memoryMxBean.getHeapMemoryUsage().getUsed() / 1024 / 1024 + " MB");

    System.out.println();
  }

  /**
   * Demonstrates remote JMX patterns.
   *
   * <p>These patterns use javax.management.remote.* and require java.management.rmi module.
   */
  private static void demonstrateRemoteJmxPatterns() {
    System.out.println("--- Remote JMX Demo (requires java.management.rmi) ---");

    // Demonstrate JMXServiceURL construction
    // This pattern is detected by JmxModuleScanner
    try {
      JMXServiceURL serviceUrl =
          new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi");
      System.out.println("JMX Service URL: " + serviceUrl);
      System.out.println("  Protocol: " + serviceUrl.getProtocol());
      System.out.println("  Host: " + serviceUrl.getHost());
      System.out.println("  Port: " + serviceUrl.getPort());

      // Demonstrate JMXConnectorFactory usage
      // This would connect to a remote JMX server
      System.out.println("\nAttempting remote connection (will fail if no server running)...");
      try {
        JMXConnector connector = JMXConnectorFactory.connect(serviceUrl);
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        System.out.println("Connected! MBean count: " + connection.getMBeanCount());

        // Query some MBeans
        var names = connection.queryNames(new ObjectName("java.lang:type=Runtime"), null);
        System.out.println("Runtime MBeans found: " + names.size());

        connector.close();
      } catch (IOException e) {
        System.out.println("  Connection failed (expected if no JMX server): " + e.getMessage());
        System.out.println(
            "  This is normal - the patterns are still detected for module inclusion.");
      }
    } catch (Exception e) {
      System.out.println("JMX URL parsing error: " + e.getMessage());
    }

    System.out.println("\nRemote JMX patterns demonstrated:");
    System.out.println("  - JMXServiceURL: Creates JMX service endpoint URLs");
    System.out.println("  - JMXConnectorFactory: Factory for creating remote JMX connections");
    System.out.println("  - JMXConnector: Interface for managing remote connections");
  }
}
