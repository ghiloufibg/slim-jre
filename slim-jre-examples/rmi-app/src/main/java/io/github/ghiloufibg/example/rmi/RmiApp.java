package io.github.ghiloufibg.example.rmi;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.MarshalledObject;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

/**
 * Tests java.rmi module detection with RMI patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.rmi - Remote, RemoteException, Registry, UnicastRemoteObject, Naming, LocateRegistry,
 *       etc.
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect java.rmi.* usage
 *   <li>jdeps: MUST detect java.rmi.* imports
 * </ul>
 */
public class RmiApp {

  public static void main(String[] args) {
    System.out.println("=== RMI Pattern Test ===\n");

    // Test 1: Registry operations
    testRegistry();

    // Test 2: Remote interface
    testRemoteInterface();

    // Test 3: UnicastRemoteObject
    testUnicastRemoteObject();

    // Test 4: Naming operations
    testNaming();

    // Test 5: Exception types
    testExceptionTypes();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testRegistry() {
    System.out.println("--- Registry Pattern ---");

    try {
      // Try to get or create registry
      Registry registry = LocateRegistry.getRegistry();
      System.out.println("[OK] Got registry reference");

      String[] names = registry.list();
      System.out.println("[OK] Registry has " + names.length + " bindings");

    } catch (RemoteException e) {
      System.out.println("[INFO] Registry test (expected without RMI server): " + e.getMessage());
    }

    // Type references
    System.out.println("[INFO] Registry type: " + Registry.class.getName());
    System.out.println("[INFO] LocateRegistry type: " + LocateRegistry.class.getName());
  }

  private static void testRemoteInterface() {
    System.out.println("\n--- Remote Interface Pattern ---");

    // Type references
    System.out.println("[INFO] Remote type: " + Remote.class.getName());
    System.out.println("[INFO] RemoteObject type: " + RemoteObject.class.getName());
  }

  private static void testUnicastRemoteObject() {
    System.out.println("\n--- UnicastRemoteObject Pattern ---");

    // Type reference
    System.out.println("[INFO] UnicastRemoteObject type: " + UnicastRemoteObject.class.getName());

    // MarshalledObject for serialization
    try {
      MarshalledObject<String> mo = new MarshalledObject<>("test");
      System.out.println("[OK] MarshalledObject created: " + mo.get());
    } catch (Exception e) {
      System.out.println("[INFO] MarshalledObject: " + e.getMessage());
    }
    System.out.println("[INFO] MarshalledObject type: " + MarshalledObject.class.getName());
  }

  private static void testNaming() {
    System.out.println("\n--- Naming Pattern ---");

    try {
      // Try to lookup (will fail without RMI server)
      Remote remote = Naming.lookup("rmi://localhost/TestService");
      System.out.println("[OK] Naming lookup succeeded");
    } catch (NotBoundException e) {
      System.out.println("[INFO] Naming lookup: not bound");
    } catch (RemoteException e) {
      System.out.println("[INFO] Naming lookup: " + e.getMessage());
    } catch (java.net.MalformedURLException e) {
      System.out.println("[INFO] Naming lookup: malformed URL");
    }

    // Type reference
    System.out.println("[INFO] Naming type: " + Naming.class.getName());
  }

  private static void testExceptionTypes() {
    System.out.println("\n--- Exception Type References ---");

    // Exception type references
    System.out.println("[INFO] RemoteException type: " + RemoteException.class.getName());
    System.out.println("[INFO] NotBoundException type: " + NotBoundException.class.getName());
    System.out.println(
        "[INFO] AlreadyBoundException type: " + AlreadyBoundException.class.getName());
    System.out.println("[INFO] AccessException type: " + AccessException.class.getName());
  }
}
