package com.ghiloufi.example.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 * Tests java.prefs module detection with Preferences API.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.prefs - Preferences, BackingStoreException, listeners
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect java.util.prefs.* usage
 *   <li>jdeps: MUST detect java.util.prefs.* imports
 * </ul>
 */
public class PrefsApp {

  public static void main(String[] args) {
    System.out.println("=== Preferences API Pattern Test ===\n");

    // Test 1: User preferences
    testUserPreferences();

    // Test 2: System preferences
    testSystemPreferences();

    // Test 3: Preference listeners
    testPreferenceListeners();

    // Test 4: Node operations
    testNodeOperations();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testUserPreferences() {
    System.out.println("--- User Preferences Pattern ---");

    Preferences userPrefs = Preferences.userNodeForPackage(PrefsApp.class);

    // Write preferences
    userPrefs.put("username", "testuser");
    userPrefs.putInt("windowWidth", 800);
    userPrefs.putInt("windowHeight", 600);
    userPrefs.putBoolean("darkMode", true);
    userPrefs.putDouble("zoomLevel", 1.5);

    System.out.println("[OK] User preferences written");

    // Read preferences
    String username = userPrefs.get("username", "default");
    int width = userPrefs.getInt("windowWidth", 640);
    boolean dark = userPrefs.getBoolean("darkMode", false);

    System.out.println(
        "[OK] Read preferences: username=" + username + ", width=" + width + ", darkMode=" + dark);
  }

  private static void testSystemPreferences() {
    System.out.println("\n--- System Preferences Pattern ---");

    try {
      Preferences systemPrefs = Preferences.systemNodeForPackage(PrefsApp.class);
      System.out.println("[INFO] System prefs node: " + systemPrefs.absolutePath());

      // May fail without admin privileges
      systemPrefs.put("appVersion", "1.0.0");
      System.out.println("[OK] System preferences accessible");
    } catch (SecurityException e) {
      System.out.println("[INFO] System preferences require admin: " + e.getMessage());
    }
  }

  private static void testPreferenceListeners() {
    System.out.println("\n--- Preference Listeners Pattern ---");

    Preferences prefs = Preferences.userNodeForPackage(PrefsApp.class);

    // Preference change listener
    PreferenceChangeListener prefListener =
        new PreferenceChangeListener() {
          @Override
          public void preferenceChange(PreferenceChangeEvent evt) {
            System.out.println(
                "  [EVENT] Preference changed: " + evt.getKey() + " = " + evt.getNewValue());
          }
        };

    // Node change listener
    NodeChangeListener nodeListener =
        new NodeChangeListener() {
          @Override
          public void childAdded(NodeChangeEvent evt) {
            System.out.println("  [EVENT] Node added: " + evt.getChild().name());
          }

          @Override
          public void childRemoved(NodeChangeEvent evt) {
            System.out.println("  [EVENT] Node removed: " + evt.getChild().name());
          }
        };

    prefs.addPreferenceChangeListener(prefListener);
    prefs.addNodeChangeListener(nodeListener);

    System.out.println("[OK] Listeners registered");
    System.out.println(
        "[INFO] PreferenceChangeListener type: " + PreferenceChangeListener.class.getName());
    System.out.println("[INFO] NodeChangeListener type: " + NodeChangeListener.class.getName());
  }

  private static void testNodeOperations() {
    System.out.println("\n--- Node Operations Pattern ---");

    Preferences root = Preferences.userNodeForPackage(PrefsApp.class);

    try {
      // Create child nodes
      Preferences settings = root.node("settings");
      Preferences appearance = settings.node("appearance");

      System.out.println("[OK] Created node hierarchy: " + appearance.absolutePath());

      // List children
      String[] children = root.childrenNames();
      System.out.println("[OK] Child nodes: " + String.join(", ", children));

      // Flush and sync
      root.flush();
      System.out.println("[OK] Preferences flushed");

      // Clean up
      appearance.removeNode();
      settings.removeNode();
      System.out.println("[OK] Test nodes removed");

    } catch (BackingStoreException e) {
      System.out.println("[INFO] BackingStoreException: " + e.getMessage());
      System.out.println(
          "[INFO] BackingStoreException type: " + BackingStoreException.class.getName());
    }
  }
}
