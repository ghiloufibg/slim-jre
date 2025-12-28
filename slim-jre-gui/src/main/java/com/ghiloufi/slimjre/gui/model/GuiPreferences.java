package com.ghiloufi.slimjre.gui.model;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * Manages GUI preferences persistence using Java Preferences API.
 *
 * <p>Stores and retrieves user preferences such as:
 *
 * <ul>
 *   <li>Last used directories for file dialogs
 *   <li>Default configuration options
 *   <li>Window size and position
 * </ul>
 */
public final class GuiPreferences {

  private static final Preferences PREFS = Preferences.userNodeForPackage(GuiPreferences.class);

  // Preference keys
  private static final String LAST_JAR_DIRECTORY = "lastJarDirectory";
  private static final String OUTPUT_DIRECTORY = "outputDirectory";
  private static final String STRIP_DEBUG = "stripDebug";
  private static final String SCAN_SERVICE_LOADERS = "scanServiceLoaders";
  private static final String SCAN_GRAALVM_METADATA = "scanGraalVmMetadata";
  private static final String VERBOSE = "verbose";
  private static final String COMPRESSION = "compression";
  private static final String WINDOW_WIDTH = "windowWidth";
  private static final String WINDOW_HEIGHT = "windowHeight";
  private static final String WINDOW_X = "windowX";
  private static final String WINDOW_Y = "windowY";

  private Path lastJarDirectory;
  private Path outputDirectory;
  private boolean stripDebug;
  private boolean scanServiceLoaders;
  private boolean scanGraalVmMetadata;
  private boolean verbose;
  private String compression;
  private int windowWidth;
  private int windowHeight;
  private int windowX;
  private int windowY;

  private GuiPreferences() {
    // Private constructor - use load()
  }

  /**
   * Loads preferences from the system preference store.
   *
   * @return loaded preferences with defaults for missing values
   */
  public static GuiPreferences load() {
    GuiPreferences prefs = new GuiPreferences();

    String home = System.getProperty("user.home");
    prefs.lastJarDirectory = Path.of(PREFS.get(LAST_JAR_DIRECTORY, home));
    prefs.outputDirectory = Path.of(PREFS.get(OUTPUT_DIRECTORY, "./slim-jre"));
    prefs.stripDebug = PREFS.getBoolean(STRIP_DEBUG, true);
    prefs.scanServiceLoaders = PREFS.getBoolean(SCAN_SERVICE_LOADERS, true);
    prefs.scanGraalVmMetadata = PREFS.getBoolean(SCAN_GRAALVM_METADATA, true);
    prefs.verbose = PREFS.getBoolean(VERBOSE, false);
    prefs.compression = PREFS.get(COMPRESSION, "zip-6");
    prefs.windowWidth = PREFS.getInt(WINDOW_WIDTH, 900);
    prefs.windowHeight = PREFS.getInt(WINDOW_HEIGHT, 700);
    prefs.windowX = PREFS.getInt(WINDOW_X, -1);
    prefs.windowY = PREFS.getInt(WINDOW_Y, -1);

    return prefs;
  }

  /** Saves current preferences to the system preference store. */
  public void save() {
    PREFS.put(LAST_JAR_DIRECTORY, lastJarDirectory.toString());
    PREFS.put(OUTPUT_DIRECTORY, outputDirectory.toString());
    PREFS.putBoolean(STRIP_DEBUG, stripDebug);
    PREFS.putBoolean(SCAN_SERVICE_LOADERS, scanServiceLoaders);
    PREFS.putBoolean(SCAN_GRAALVM_METADATA, scanGraalVmMetadata);
    PREFS.putBoolean(VERBOSE, verbose);
    PREFS.put(COMPRESSION, compression);
    PREFS.putInt(WINDOW_WIDTH, windowWidth);
    PREFS.putInt(WINDOW_HEIGHT, windowHeight);
    PREFS.putInt(WINDOW_X, windowX);
    PREFS.putInt(WINDOW_Y, windowY);
  }

  /** Resets all preferences to default values. */
  public static void resetToDefaults() {
    try {
      PREFS.clear();
    } catch (Exception e) {
      // Ignore errors during reset
    }
  }

  // Getters and setters

  public Path getLastJarDirectory() {
    return lastJarDirectory;
  }

  public void setLastJarDirectory(Path lastJarDirectory) {
    this.lastJarDirectory = lastJarDirectory;
  }

  public Path getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  public boolean isStripDebug() {
    return stripDebug;
  }

  public void setStripDebug(boolean stripDebug) {
    this.stripDebug = stripDebug;
  }

  public boolean isScanServiceLoaders() {
    return scanServiceLoaders;
  }

  public void setScanServiceLoaders(boolean scanServiceLoaders) {
    this.scanServiceLoaders = scanServiceLoaders;
  }

  public boolean isScanGraalVmMetadata() {
    return scanGraalVmMetadata;
  }

  public void setScanGraalVmMetadata(boolean scanGraalVmMetadata) {
    this.scanGraalVmMetadata = scanGraalVmMetadata;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public String getCompression() {
    return compression;
  }

  public void setCompression(String compression) {
    this.compression = compression;
  }

  public int getWindowWidth() {
    return windowWidth;
  }

  public void setWindowWidth(int windowWidth) {
    this.windowWidth = windowWidth;
  }

  public int getWindowHeight() {
    return windowHeight;
  }

  public void setWindowHeight(int windowHeight) {
    this.windowHeight = windowHeight;
  }

  public int getWindowX() {
    return windowX;
  }

  public void setWindowX(int windowX) {
    this.windowX = windowX;
  }

  public int getWindowY() {
    return windowY;
  }

  public void setWindowY(int windowY) {
    this.windowY = windowY;
  }
}
