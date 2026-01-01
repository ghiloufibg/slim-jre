package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.CryptoMode;
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
 *   <li>Output folder and JRE name selection
 *   <li>Compression level selection
 *   <li>Strip debug, headers, and man pages toggles
 *   <li>Additional/excluded modules input
 * </ul>
 *
 * <p>All scanners (jdeps, service loaders, reflection, API usage, GraalVM metadata, crypto, locale,
 * zipfs, jmx) are enabled by default without user configuration.
 */
public class ConfigurationPanel extends JPanel {

  private final JTextField outputFolderField;
  private final JTextField jreNameField;
  private final JComboBox<String> compressionCombo;
  private final JCheckBox stripDebugCheckbox;
  private final JCheckBox stripHeadersCheckbox;
  private final JCheckBox stripManPagesCheckbox;
  private final JTextField additionalModulesField;
  private final JTextField excludeModulesField;

  /** Creates a new configuration panel with default values. */
  public ConfigurationPanel() {
    setLayout(new GridBagLayout());

    // Initialize components
    outputFolderField = new JTextField(".");
    jreNameField = new JTextField("slim-jre");

    String[] compressionLevels = new String[10];
    for (int i = 0; i <= 9; i++) {
      compressionLevels[i] = "zip-" + i + (i == 0 ? " (none)" : i == 6 ? " (default)" : "");
    }
    compressionCombo = new JComboBox<>(compressionLevels);
    compressionCombo.setSelectedIndex(6);

    stripDebugCheckbox = new JCheckBox("Strip debug info", true);
    stripHeadersCheckbox = new JCheckBox("Strip header files", true);
    stripManPagesCheckbox = new JCheckBox("Strip man pages", true);

    additionalModulesField = new JTextField();
    excludeModulesField = new JTextField();

    initializeLayout();
    addTooltips();
  }

  private void initializeLayout() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.anchor = GridBagConstraints.WEST;

    int row = 0;

    // Output folder
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    add(new JLabel("Folder:"), gbc);

    JPanel folderPanel = new JPanel(new BorderLayout(5, 0));
    folderPanel.add(outputFolderField, BorderLayout.CENTER);
    JButton browseButton = new JButton("...");
    browseButton.setPreferredSize(new Dimension(30, 25));
    browseButton.addActionListener(e -> browseOutputFolder());
    folderPanel.add(browseButton, BorderLayout.EAST);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(folderPanel, gbc);

    row++;

    // JRE name
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    add(new JLabel("JRE Name:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(jreNameField, gbc);

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
    gbc.fill = GridBagConstraints.NONE;
    add(new JLabel("Compression:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(compressionCombo, gbc);

    row++;

    // Separator
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    add(Box.createVerticalStrut(5), gbc);
    gbc.gridwidth = 1;

    row++;

    // Checkboxes
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.NONE;
    add(stripDebugCheckbox, gbc);

    row++;
    gbc.gridy = row;
    add(stripHeadersCheckbox, gbc);

    row++;
    gbc.gridy = row;
    add(stripManPagesCheckbox, gbc);
    gbc.gridwidth = 1;

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
    gbc.fill = GridBagConstraints.NONE;
    add(new JLabel("Add:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(additionalModulesField, gbc);

    row++;

    // Exclude modules
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    add(new JLabel("Exclude:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(excludeModulesField, gbc);

    row++;

    // Help text for modules
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 4, 4, 4);
    JLabel helpLabel =
        new JLabel("<html><i>(comma-separated, e.g., java.desktop, jdk.jfr)</i></html>");
    helpLabel.setFont(helpLabel.getFont().deriveFont(10f));
    helpLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    add(helpLabel, gbc);
    gbc.insets = new Insets(4, 4, 4, 4);

    row++;

    // Spacer to push everything up
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(Box.createVerticalGlue(), gbc);
  }

  private void addTooltips() {
    outputFolderField.setToolTipText("Parent directory where the JRE folder will be created");
    jreNameField.setToolTipText("Name of the folder for the minimal JRE");
    compressionCombo.setToolTipText("Compression level (higher = smaller but slower to create)");
    stripDebugCheckbox.setToolTipText("Remove debug information for smaller size");
    stripHeadersCheckbox.setToolTipText("Exclude native header files (not needed at runtime)");
    stripManPagesCheckbox.setToolTipText("Exclude manual pages (not needed at runtime)");
    additionalModulesField.setToolTipText(
        "Modules to force-include even if not detected (e.g., java.management, jdk.jfr)");
    excludeModulesField.setToolTipText("Modules to exclude even if detected (e.g., java.desktop)");
  }

  private void browseOutputFolder() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select Output Folder");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    String current = outputFolderField.getText().trim();
    if (!current.isEmpty()) {
      File currentDir = new File(current);
      if (currentDir.exists()) {
        chooser.setCurrentDirectory(currentDir);
      }
    }

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      outputFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  /**
   * Builds a SlimJreConfig from the current panel state.
   *
   * <p>All scanners are enabled by default (no user configuration needed).
   *
   * @param jars list of JAR files to include
   * @return configured SlimJreConfig
   */
  public SlimJreConfig buildConfig(List<Path> jars) {
    Path outputPath =
        Path.of(outputFolderField.getText().trim()).resolve(jreNameField.getText().trim());

    SlimJreConfig.Builder builder =
        SlimJreConfig.builder()
            .jars(jars)
            .outputPath(outputPath)
            .compression(getSelectedCompression())
            .stripDebug(stripDebugCheckbox.isSelected())
            .noHeaderFiles(stripHeadersCheckbox.isSelected())
            .noManPages(stripManPagesCheckbox.isSelected())
            // All scanners enabled by default - no UI options
            .scanServiceLoaders(true)
            .scanGraalVmMetadata(true)
            .cryptoMode(CryptoMode.AUTO)
            .verbose(false);

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
   * Returns the output path (folder + JRE name).
   *
   * @return output directory path
   */
  public Path getOutputPath() {
    return Path.of(outputFolderField.getText().trim()).resolve(jreNameField.getText().trim());
  }

  // ===== Getter methods for configuration save/load =====

  /**
   * Returns the output folder.
   *
   * @return output folder path
   */
  public Path getOutputFolder() {
    return Path.of(outputFolderField.getText().trim());
  }

  /**
   * Returns the JRE name.
   *
   * @return JRE folder name
   */
  public String getJreName() {
    return jreNameField.getText().trim();
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
   * Returns whether strip debug is enabled.
   *
   * @return true if enabled
   */
  public boolean isStripDebug() {
    return stripDebugCheckbox.isSelected();
  }

  /**
   * Returns whether strip headers is enabled.
   *
   * @return true if enabled
   */
  public boolean isStripHeaders() {
    return stripHeadersCheckbox.isSelected();
  }

  /**
   * Returns whether strip man pages is enabled.
   *
   * @return true if enabled
   */
  public boolean isStripManPages() {
    return stripManPagesCheckbox.isSelected();
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
   * Sets the output folder.
   *
   * @param path output folder path
   */
  public void setOutputFolder(Path path) {
    outputFolderField.setText(path.toString());
  }

  /**
   * Sets the JRE name.
   *
   * @param name JRE folder name
   */
  public void setJreName(String name) {
    jreNameField.setText(name != null ? name : "slim-jre");
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
   * Sets whether strip debug is enabled.
   *
   * @param enabled true to enable
   */
  public void setStripDebug(boolean enabled) {
    stripDebugCheckbox.setSelected(enabled);
  }

  /**
   * Sets whether strip headers is enabled.
   *
   * @param enabled true to enable
   */
  public void setStripHeaders(boolean enabled) {
    stripHeadersCheckbox.setSelected(enabled);
  }

  /**
   * Sets whether strip man pages is enabled.
   *
   * @param enabled true to enable
   */
  public void setStripManPages(boolean enabled) {
    stripManPagesCheckbox.setSelected(enabled);
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
    setOutputFolder(prefs.getOutputFolder());
    setJreName(prefs.getJreName());
    setCompression(prefs.getCompression());
    setStripDebug(prefs.isStripDebug());
    setStripHeaders(prefs.isStripHeaders());
    setStripManPages(prefs.isStripManPages());
  }

  /**
   * Saves current panel state to preferences.
   *
   * @param prefs preferences to update
   */
  public void saveToPreferences(GuiPreferences prefs) {
    prefs.setOutputFolder(getOutputFolder());
    prefs.setJreName(getJreName());
    prefs.setCompression(getCompression());
    prefs.setStripDebug(isStripDebug());
    prefs.setStripHeaders(isStripHeaders());
    prefs.setStripManPages(isStripManPages());
  }
}
