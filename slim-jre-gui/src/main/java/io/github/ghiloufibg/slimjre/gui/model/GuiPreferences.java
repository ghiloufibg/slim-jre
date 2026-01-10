package io.github.ghiloufibg.slimjre.gui.model;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * Manages GUI preferences persistence using Java Preferences API.
 *
 * <p>Stores and retrieves user preferences such as:
 *
 * <ul>
 *   <li>Last used directories for file dialogs
 *   <li>Output folder and JRE name
 *   <li>Compression and strip options
 *   <li>Window size and position
 * </ul>
 */
public final class GuiPreferences {

  private static final Preferences PREFS = Preferences.userNodeForPackage(GuiPreferences.class);

  // Preference keys
  private static final String LAST_JAR_DIRECTORY = "lastJarDirectory";
  private static final String OUTPUT_FOLDER = "outputFolder";
  private static final String JRE_NAME = "jreName";
  private static final String COMPRESSION = "compression";
  private static final String STRIP_DEBUG = "stripDebug";
  private static final String STRIP_HEADERS = "stripHeaders";
  private static final String STRIP_MAN_PAGES = "stripManPages";
  private static final String WINDOW_WIDTH = "windowWidth";
  private static final String WINDOW_HEIGHT = "windowHeight";
  private static final String WINDOW_X = "windowX";
  private static final String WINDOW_Y = "windowY";

  private Path lastJarDirectory;
  private Path outputFolder;
  private String jreName;
  private String compression;
  private boolean stripDebug;
  private boolean stripHeaders;
  private boolean stripManPages;
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
    prefs.outputFolder = Path.of(PREFS.get(OUTPUT_FOLDER, "."));
    prefs.jreName = PREFS.get(JRE_NAME, "slim-jre");
    prefs.compression = PREFS.get(COMPRESSION, "zip-6");
    prefs.stripDebug = PREFS.getBoolean(STRIP_DEBUG, true);
    prefs.stripHeaders = PREFS.getBoolean(STRIP_HEADERS, true);
    prefs.stripManPages = PREFS.getBoolean(STRIP_MAN_PAGES, true);
    prefs.windowWidth = PREFS.getInt(WINDOW_WIDTH, 900);
    prefs.windowHeight = PREFS.getInt(WINDOW_HEIGHT, 700);
    prefs.windowX = PREFS.getInt(WINDOW_X, -1);
    prefs.windowY = PREFS.getInt(WINDOW_Y, -1);

    return prefs;
  }

  /** Saves current preferences to the system preference store. */
  public void save() {
    PREFS.put(LAST_JAR_DIRECTORY, lastJarDirectory.toString());
    PREFS.put(OUTPUT_FOLDER, outputFolder.toString());
    PREFS.put(JRE_NAME, jreName);
    PREFS.put(COMPRESSION, compression);
    PREFS.putBoolean(STRIP_DEBUG, stripDebug);
    PREFS.putBoolean(STRIP_HEADERS, stripHeaders);
    PREFS.putBoolean(STRIP_MAN_PAGES, stripManPages);
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

  public Path getOutputFolder() {
    return outputFolder;
  }

  public void setOutputFolder(Path outputFolder) {
    this.outputFolder = outputFolder;
  }

  public String getJreName() {
    return jreName;
  }

  public void setJreName(String jreName) {
    this.jreName = jreName;
  }

  public String getCompression() {
    return compression;
  }

  public void setCompression(String compression) {
    this.compression = compression;
  }

  public boolean isStripDebug() {
    return stripDebug;
  }

  public void setStripDebug(boolean stripDebug) {
    this.stripDebug = stripDebug;
  }

  public boolean isStripHeaders() {
    return stripHeaders;
  }

  public void setStripHeaders(boolean stripHeaders) {
    this.stripHeaders = stripHeaders;
  }

  public boolean isStripManPages() {
    return stripManPages;
  }

  public void setStripManPages(boolean stripManPages) {
    this.stripManPages = stripManPages;
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

  // ===== Backward compatibility methods =====

  /**
   * Returns the output directory (folder + JRE name combined).
   *
   * @return output directory path
   * @deprecated Use getOutputFolder() and getJreName() separately
   */
  @Deprecated
  public Path getOutputDirectory() {
    return outputFolder.resolve(jreName);
  }

  /**
   * Sets the output directory by parsing folder and name.
   *
   * @param outputDirectory output directory path
   * @deprecated Use setOutputFolder() and setJreName() separately
   */
  @Deprecated
  public void setOutputDirectory(Path outputDirectory) {
    if (outputDirectory != null) {
      Path parent = outputDirectory.getParent();
      this.outputFolder = parent != null ? parent : Path.of(".");
      this.jreName =
          outputDirectory.getFileName() != null
              ? outputDirectory.getFileName().toString()
              : "slim-jre";
    }
  }

  /**
   * Returns whether verbose is enabled.
   *
   * @return always false (verbose is no longer a user option)
   * @deprecated Verbose mode is no longer exposed in UI
   */
  @Deprecated
  public boolean isVerbose() {
    return false;
  }

  /**
   * Sets whether verbose is enabled.
   *
   * @param verbose ignored
   * @deprecated Verbose mode is no longer exposed in UI
   */
  @Deprecated
  public void setVerbose(boolean verbose) {
    // No-op - verbose is no longer a user option
  }

  /**
   * Returns whether service loader scanning is enabled.
   *
   * @return always true (all scanners are enabled by default)
   * @deprecated All scanners are now enabled by default
   */
  @Deprecated
  public boolean isScanServiceLoaders() {
    return true;
  }

  /**
   * Sets whether service loader scanning is enabled.
   *
   * @param enabled ignored
   * @deprecated All scanners are now enabled by default
   */
  @Deprecated
  public void setScanServiceLoaders(boolean enabled) {
    // No-op - all scanners are enabled by default
  }

  /**
   * Returns whether GraalVM metadata scanning is enabled.
   *
   * @return always true (all scanners are enabled by default)
   * @deprecated All scanners are now enabled by default
   */
  @Deprecated
  public boolean isScanGraalVmMetadata() {
    return true;
  }

  /**
   * Sets whether GraalVM metadata scanning is enabled.
   *
   * @param enabled ignored
   * @deprecated All scanners are now enabled by default
   */
  @Deprecated
  public void setScanGraalVmMetadata(boolean enabled) {
    // No-op - all scanners are enabled by default
  }
}
