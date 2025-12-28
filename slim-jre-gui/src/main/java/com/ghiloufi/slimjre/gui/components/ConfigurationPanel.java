package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.SlimJreConfig;
import com.ghiloufi.slimjre.gui.model.GuiPreferences;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;

/**
 * Panel for configuring JRE creation options.
 *
 * <p>Provides controls for:
 *
 * <ul>
 *   <li>Output directory selection
 *   <li>Strip debug info toggle
 *   <li>Service loader scanning toggle
 *   <li>GraalVM metadata scanning toggle
 *   <li>Compression level selection
 *   <li>Additional/excluded modules input
 * </ul>
 */
public class ConfigurationPanel extends JPanel {

  private final JTextField outputDirectoryField;
  private final JCheckBox stripDebugCheckbox;
  private final JCheckBox scanServiceLoadersCheckbox;
  private final JCheckBox scanGraalVmMetadataCheckbox;
  private final JCheckBox verboseCheckbox;
  private final JComboBox<String> compressionCombo;
  private final JTextField additionalModulesField;
  private final JTextField excludeModulesField;

  /** Creates a new configuration panel with default values. */
  public ConfigurationPanel() {
    setLayout(new GridBagLayout());

    // Initialize components
    outputDirectoryField = new JTextField("slim-jre");
    stripDebugCheckbox = new JCheckBox("Strip debug info", true);
    scanServiceLoadersCheckbox = new JCheckBox("Scan service loaders", true);
    scanGraalVmMetadataCheckbox = new JCheckBox("Scan GraalVM metadata", true);
    verboseCheckbox = new JCheckBox("Verbose output", false);

    String[] compressionLevels = new String[10];
    for (int i = 0; i <= 9; i++) {
      compressionLevels[i] = "zip-" + i + (i == 0 ? " (none)" : i == 6 ? " (default)" : "");
    }
    compressionCombo = new JComboBox<>(compressionLevels);
    compressionCombo.setSelectedIndex(6);

    additionalModulesField = new JTextField();
    excludeModulesField = new JTextField();

    initializeLayout();
    addTooltips();
  }

  private void initializeLayout() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    int row = 0;

    // Output directory
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    add(new JLabel("Output:"), gbc);

    JPanel outputPanel = new JPanel(new BorderLayout(5, 0));
    outputPanel.add(outputDirectoryField, BorderLayout.CENTER);
    JButton browseButton = new JButton("...");
    browseButton.setPreferredSize(new Dimension(30, 25));
    browseButton.addActionListener(e -> browseOutputDirectory());
    outputPanel.add(browseButton, BorderLayout.EAST);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(outputPanel, gbc);

    row++;

    // Separator
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    add(Box.createVerticalStrut(10), gbc);
    gbc.gridwidth = 1;

    row++;

    // Checkboxes
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    add(stripDebugCheckbox, gbc);

    row++;
    gbc.gridy = row;
    add(scanServiceLoadersCheckbox, gbc);

    row++;
    gbc.gridy = row;
    add(scanGraalVmMetadataCheckbox, gbc);

    row++;
    gbc.gridy = row;
    add(verboseCheckbox, gbc);
    gbc.gridwidth = 1;

    row++;

    // Separator
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    add(Box.createVerticalStrut(10), gbc);
    gbc.gridwidth = 1;

    row++;

    // Compression
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    add(new JLabel("Compression:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(compressionCombo, gbc);

    row++;

    // Separator
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    add(Box.createVerticalStrut(10), gbc);
    gbc.gridwidth = 1;

    row++;

    // Additional modules
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    add(new JLabel("Add modules:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(additionalModulesField, gbc);

    row++;

    // Exclude modules
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    add(new JLabel("Exclude:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(excludeModulesField, gbc);

    row++;

    // Spacer to push everything up
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(Box.createVerticalGlue(), gbc);
  }

  private void addTooltips() {
    outputDirectoryField.setToolTipText("Directory where the minimal JRE will be created");
    stripDebugCheckbox.setToolTipText("Remove debug information from JRE classes (smaller size)");
    scanServiceLoadersCheckbox.setToolTipText(
        "Scan META-INF/services for service loader dependencies");
    scanGraalVmMetadataCheckbox.setToolTipText(
        "Scan GraalVM native-image metadata for reflection dependencies");
    verboseCheckbox.setToolTipText("Enable verbose output during JRE creation");
    compressionCombo.setToolTipText("Compression level for the JRE (higher = smaller but slower)");
    additionalModulesField.setToolTipText(
        "Comma-separated list of modules to force-include (e.g., java.management,java.sql)");
    excludeModulesField.setToolTipText(
        "Comma-separated list of modules to exclude (e.g., java.desktop)");
  }

  private void browseOutputDirectory() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select Output Directory");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    String current = outputDirectoryField.getText().trim();
    if (!current.isEmpty()) {
      File currentDir = new File(current);
      if (currentDir.exists()) {
        chooser.setCurrentDirectory(currentDir);
      }
    }

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      outputDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  /**
   * Builds a SlimJreConfig from the current panel state.
   *
   * @param jars list of JAR files to include
   * @return configured SlimJreConfig
   */
  public SlimJreConfig buildConfig(List<Path> jars) {
    SlimJreConfig.Builder builder =
        SlimJreConfig.builder()
            .jars(jars)
            .outputPath(Path.of(outputDirectoryField.getText().trim()))
            .stripDebug(stripDebugCheckbox.isSelected())
            .scanServiceLoaders(scanServiceLoadersCheckbox.isSelected())
            .scanGraalVmMetadata(scanGraalVmMetadataCheckbox.isSelected())
            .verbose(verboseCheckbox.isSelected())
            .compression(getSelectedCompression());

    // Add additional modules
    Set<String> addModules = parseModuleList(additionalModulesField.getText());
    if (!addModules.isEmpty()) {
      builder.additionalModules(addModules);
    }

    // Add exclude modules
    Set<String> excludeModules = parseModuleList(excludeModulesField.getText());
    if (!excludeModules.isEmpty()) {
      builder.excludeModules(excludeModules);
    }

    return builder.build();
  }

  private String getSelectedCompression() {
    String selected = (String) compressionCombo.getSelectedItem();
    if (selected != null) {
      // Extract "zip-N" from "zip-N (text)"
      int spaceIndex = selected.indexOf(' ');
      if (spaceIndex > 0) {
        return selected.substring(0, spaceIndex);
      }
      return selected;
    }
    return "zip-6";
  }

  private Set<String> parseModuleList(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(text.split("[,;\\s]+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  /**
   * Returns whether service loader scanning is enabled.
   *
   * @return true if enabled
   */
  public boolean isScanServiceLoaders() {
    return scanServiceLoadersCheckbox.isSelected();
  }

  /**
   * Returns whether GraalVM metadata scanning is enabled.
   *
   * @return true if enabled
   */
  public boolean isScanGraalVmMetadata() {
    return scanGraalVmMetadataCheckbox.isSelected();
  }

  /**
   * Returns the output directory path.
   *
   * @return output directory path
   */
  public Path getOutputPath() {
    return Path.of(outputDirectoryField.getText().trim());
  }

  // ===== Getter methods for configuration save/load =====

  /**
   * Returns the output directory.
   *
   * @return output directory path
   */
  public Path getOutputDirectory() {
    return Path.of(outputDirectoryField.getText().trim());
  }

  /**
   * Returns whether strip debug is enabled.
   *
   * @return true if enabled
   */
  public boolean isStripDebug() {
    return stripDebugCheckbox.isSelected();
  }

  /**
   * Returns whether verbose output is enabled.
   *
   * @return true if enabled
   */
  public boolean isVerbose() {
    return verboseCheckbox.isSelected();
  }

  /**
   * Returns the compression level string.
   *
   * @return compression level (e.g., "zip-6")
   */
  public String getCompression() {
    return getSelectedCompression();
  }

  /**
   * Returns additional modules as comma-separated string.
   *
   * @return additional modules string
   */
  public String getAdditionalModules() {
    return additionalModulesField.getText();
  }

  /**
   * Returns exclude modules as comma-separated string.
   *
   * @return exclude modules string
   */
  public String getExcludeModules() {
    return excludeModulesField.getText();
  }

  // ===== Setter methods for configuration load =====

  /**
   * Sets the output directory.
   *
   * @param path output directory path
   */
  public void setOutputDirectory(Path path) {
    outputDirectoryField.setText(path.toString());
  }

  /**
   * Sets whether strip debug is enabled.
   *
   * @param enabled true to enable
   */
  public void setStripDebug(boolean enabled) {
    stripDebugCheckbox.setSelected(enabled);
  }

  /**
   * Sets whether service loader scanning is enabled.
   *
   * @param enabled true to enable
   */
  public void setScanServiceLoaders(boolean enabled) {
    scanServiceLoadersCheckbox.setSelected(enabled);
  }

  /**
   * Sets whether GraalVM metadata scanning is enabled.
   *
   * @param enabled true to enable
   */
  public void setScanGraalVmMetadata(boolean enabled) {
    scanGraalVmMetadataCheckbox.setSelected(enabled);
  }

  /**
   * Sets whether verbose output is enabled.
   *
   * @param enabled true to enable
   */
  public void setVerbose(boolean enabled) {
    verboseCheckbox.setSelected(enabled);
  }

  /**
   * Sets the compression level.
   *
   * @param compression compression level (e.g., "zip-6")
   */
  public void setCompression(String compression) {
    for (int i = 0; i < compressionCombo.getItemCount(); i++) {
      String item = compressionCombo.getItemAt(i);
      if (item != null && item.startsWith(compression)) {
        compressionCombo.setSelectedIndex(i);
        break;
      }
    }
  }

  /**
   * Sets additional modules.
   *
   * @param modules comma-separated module list
   */
  public void setAdditionalModules(String modules) {
    additionalModulesField.setText(modules != null ? modules : "");
  }

  /**
   * Sets exclude modules.
   *
   * @param modules comma-separated module list
   */
  public void setExcludeModules(String modules) {
    excludeModulesField.setText(modules != null ? modules : "");
  }

  // ===== Preferences integration =====

  /**
   * Applies preferences to the panel.
   *
   * @param prefs preferences to apply
   */
  public void applyPreferences(GuiPreferences prefs) {
    setOutputDirectory(prefs.getOutputDirectory());
    setStripDebug(prefs.isStripDebug());
    setScanServiceLoaders(prefs.isScanServiceLoaders());
    setScanGraalVmMetadata(prefs.isScanGraalVmMetadata());
    setVerbose(prefs.isVerbose());
    setCompression(prefs.getCompression());
  }

  /**
   * Saves current panel state to preferences.
   *
   * @param prefs preferences to update
   */
  public void saveToPreferences(GuiPreferences prefs) {
    prefs.setOutputDirectory(getOutputDirectory());
    prefs.setStripDebug(isStripDebug());
    prefs.setScanServiceLoaders(isScanServiceLoaders());
    prefs.setScanGraalVmMetadata(isScanGraalVmMetadata());
    prefs.setVerbose(isVerbose());
    prefs.setCompression(getCompression());
  }
}
