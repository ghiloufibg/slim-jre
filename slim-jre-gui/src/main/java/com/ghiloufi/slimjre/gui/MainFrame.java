package com.ghiloufi.slimjre.gui;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.Result;
import com.ghiloufi.slimjre.config.SlimJreConfig;
import com.ghiloufi.slimjre.exception.SlimJreException;
import com.ghiloufi.slimjre.gui.components.ActionPanel;
import com.ghiloufi.slimjre.gui.components.ConfigurationPanel;
import com.ghiloufi.slimjre.gui.components.JarSelectionPanel;
import com.ghiloufi.slimjre.gui.components.ResultsPanel;
import com.ghiloufi.slimjre.gui.model.GuiPreferences;
import com.ghiloufi.slimjre.gui.util.ConfigurationManager;
import com.ghiloufi.slimjre.gui.util.ConfigurationManager.GuiConfiguration;
import com.ghiloufi.slimjre.gui.util.ErrorDialog;
import com.ghiloufi.slimjre.gui.util.ReportExporter;
import com.ghiloufi.slimjre.gui.workers.AnalysisWorker;
import com.ghiloufi.slimjre.gui.workers.CreateJreWorker;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import javax.swing.AbstractAction;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Main application window for Slim JRE GUI.
 *
 * <p>Provides a user-friendly interface for: - Selecting JAR files to analyze - Configuring JRE
 * creation options - Running analysis and viewing module breakdown - Creating minimal custom JREs
 */
public class MainFrame extends JFrame {

  private static final String TITLE = "Slim JRE";
  private static final int DEFAULT_WIDTH = 900;
  private static final int DEFAULT_HEIGHT = 700;

  private final JarSelectionPanel jarSelectionPanel;
  private final ConfigurationPanel configurationPanel;
  private final ResultsPanel resultsPanel;
  private final ActionPanel actionPanel;
  private final JLabel statusLabel;
  private final GuiPreferences preferences;

  private SwingWorker<?, ?> currentWorker;
  private AnalysisResult lastAnalysisResult;

  /** Creates the main application frame with all components. */
  public MainFrame() {
    super(TITLE);
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(700, 500));

    // Load preferences
    preferences = GuiPreferences.load();

    // Apply saved window size and position
    if (preferences.getWindowWidth() > 0 && preferences.getWindowHeight() > 0) {
      setSize(preferences.getWindowWidth(), preferences.getWindowHeight());
      if (preferences.getWindowX() >= 0 && preferences.getWindowY() >= 0) {
        setLocation(preferences.getWindowX(), preferences.getWindowY());
      } else {
        setLocationRelativeTo(null);
      }
    } else {
      setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
      setLocationRelativeTo(null);
    }

    // Initialize components
    jarSelectionPanel = new JarSelectionPanel();
    configurationPanel = new ConfigurationPanel();
    resultsPanel = new ResultsPanel();
    actionPanel = new ActionPanel();
    statusLabel = new JLabel("Ready");

    // Apply preferences to configuration panel
    configurationPanel.applyPreferences(preferences);

    // Set up layout
    initializeLayout();

    // Set up menu bar
    setJMenuBar(createMenuBar());

    // Wire up actions
    wireActions();

    // Set up keyboard shortcuts
    setupKeyboardShortcuts();

    // Set up window close handler
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            savePreferencesAndExit();
          }
        });

    // Initial state
    updateState();
  }

  private void savePreferencesAndExit() {
    // Save window position and size
    preferences.setWindowX(getX());
    preferences.setWindowY(getY());
    preferences.setWindowWidth(getWidth());
    preferences.setWindowHeight(getHeight());

    // Save configuration panel settings
    configurationPanel.saveToPreferences(preferences);

    // Save preferences
    preferences.save();

    // Exit
    dispose();
    System.exit(0);
  }

  private void initializeLayout() {
    JPanel contentPane = new JPanel(new BorderLayout(10, 10));
    contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Top: JAR selection
    jarSelectionPanel.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "JAR Files"));
    jarSelectionPanel.setPreferredSize(new Dimension(0, 180));
    contentPane.add(jarSelectionPanel, BorderLayout.NORTH);

    // Center: Configuration + Results in a split pane
    JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    centerSplit.setResizeWeight(0.35);
    centerSplit.setDividerLocation(280);

    configurationPanel.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Configuration"));
    resultsPanel.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Results"));

    centerSplit.setLeftComponent(configurationPanel);
    centerSplit.setRightComponent(resultsPanel);
    contentPane.add(centerSplit, BorderLayout.CENTER);

    // Bottom: Actions + Status
    JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
    bottomPanel.add(actionPanel, BorderLayout.CENTER);

    // Status bar
    JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
    statusBar.setBorder(BorderFactory.createLoweredSoftBevelBorder());
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
    statusBar.add(statusLabel);
    statusBar.add(new JSeparator(SwingConstants.VERTICAL));
    statusBar.add(new JLabel("JDK " + Runtime.version().feature()));
    bottomPanel.add(statusBar, BorderLayout.SOUTH);

    contentPane.add(bottomPanel, BorderLayout.SOUTH);

    setContentPane(contentPane);
  }

  private JMenuBar createMenuBar() {
    JMenuBar menuBar = new JMenuBar();

    // File menu
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic('F');

    JMenuItem addJarItem = new JMenuItem("Add JAR...");
    addJarItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
    addJarItem.addActionListener(e -> jarSelectionPanel.showAddJarDialog());
    fileMenu.add(addJarItem);

    JMenuItem addFolderItem = new JMenuItem("Add Folder...");
    addFolderItem.setAccelerator(KeyStroke.getKeyStroke("control shift O"));
    addFolderItem.addActionListener(e -> jarSelectionPanel.showAddFolderDialog());
    fileMenu.add(addFolderItem);

    fileMenu.addSeparator();

    JMenuItem saveConfigItem = new JMenuItem("Save Configuration...");
    saveConfigItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
    saveConfigItem.addActionListener(e -> saveConfiguration());
    fileMenu.add(saveConfigItem);

    JMenuItem loadConfigItem = new JMenuItem("Load Configuration...");
    loadConfigItem.setAccelerator(KeyStroke.getKeyStroke("control L"));
    loadConfigItem.addActionListener(e -> loadConfiguration());
    fileMenu.add(loadConfigItem);

    fileMenu.addSeparator();

    JMenuItem exitItem = new JMenuItem("Exit");
    exitItem.setAccelerator(KeyStroke.getKeyStroke("alt F4"));
    exitItem.addActionListener(e -> savePreferencesAndExit());
    fileMenu.add(exitItem);

    menuBar.add(fileMenu);

    // Help menu
    JMenu helpMenu = new JMenu("Help");
    helpMenu.setMnemonic('H');

    JMenuItem aboutItem = new JMenuItem("About Slim JRE");
    aboutItem.addActionListener(e -> showAboutDialog());
    helpMenu.add(aboutItem);

    menuBar.add(helpMenu);

    return menuBar;
  }

  private void wireActions() {
    // JAR selection changes
    jarSelectionPanel.addChangeListener(e -> updateState());

    // Analyze button
    actionPanel.setAnalyzeAction(e -> runAnalysis());

    // Create JRE button
    actionPanel.setCreateJreAction(e -> runCreateJre());

    // Export button
    actionPanel.setExportAction(e -> exportReport());
  }

  private void setupKeyboardShortcuts() {
    JRootPane rootPane = getRootPane();
    InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap actionMap = rootPane.getActionMap();

    // F1 - Help/About
    inputMap.put(KeyStroke.getKeyStroke("F1"), "showHelp");
    actionMap.put(
        "showHelp",
        new AbstractAction() {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            showAboutDialog();
          }
        });

    // Ctrl+O - Add JAR
    inputMap.put(KeyStroke.getKeyStroke("control O"), "addJar");
    actionMap.put(
        "addJar",
        new AbstractAction() {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            jarSelectionPanel.showAddJarDialog();
          }
        });

    // Ctrl+Shift+O - Add Folder
    inputMap.put(KeyStroke.getKeyStroke("control shift O"), "addFolder");
    actionMap.put(
        "addFolder",
        new AbstractAction() {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            jarSelectionPanel.showAddFolderDialog();
          }
        });

    // Escape - Cancel current operation (if possible) or clear selection
    inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
    actionMap.put(
        "cancel",
        new AbstractAction() {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            if (currentWorker != null && !currentWorker.isDone()) {
              currentWorker.cancel(true);
              actionPanel.setProgress(0, "Cancelled");
              updateState();
            }
          }
        });
  }

  private void updateState() {
    List<Path> jars = jarSelectionPanel.getSelectedJars();
    boolean hasJars = !jars.isEmpty();
    boolean isRunning = currentWorker != null && !currentWorker.isDone();

    actionPanel.setAnalyzeEnabled(hasJars && !isRunning);
    actionPanel.setCreateJreEnabled(hasJars && !isRunning);

    // Update status
    if (hasJars) {
      long totalSize =
          jars.stream()
              .mapToLong(
                  p -> {
                    try {
                      return java.nio.file.Files.size(p);
                    } catch (Exception e) {
                      return 0;
                    }
                  })
              .sum();
      statusLabel.setText(
          String.format(
              "%d JAR(s) selected (%s)",
              jars.size(), com.ghiloufi.slimjre.gui.util.SizeFormatter.format(totalSize)));
    } else {
      statusLabel.setText("Ready - Select JAR files to analyze");
    }
  }

  private void runAnalysis() {
    List<Path> jars = jarSelectionPanel.getSelectedJars();
    if (jars.isEmpty()) {
      return;
    }

    // Disable buttons and show progress
    actionPanel.setAnalyzeEnabled(false);
    actionPanel.setCreateJreEnabled(false);
    actionPanel.setProgress(0, "Starting analysis...");
    resultsPanel.clear();

    // Create and run worker
    AnalysisWorker worker =
        new AnalysisWorker(
            jars,
            configurationPanel.isScanServiceLoaders(),
            configurationPanel.isScanGraalVmMetadata());

    worker.addPropertyChangeListener(
        evt -> {
          if ("progress".equals(evt.getPropertyName())) {
            AnalysisWorker.ProgressUpdate update =
                (AnalysisWorker.ProgressUpdate) evt.getNewValue();
            actionPanel.setProgress(update.percent(), update.message());
          } else if ("state".equals(evt.getPropertyName())
              && SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
            onAnalysisComplete(worker);
          }
        });

    currentWorker = worker;
    worker.execute();
  }

  private void onAnalysisComplete(AnalysisWorker worker) {
    try {
      AnalysisResult result = worker.get();
      lastAnalysisResult = result;
      resultsPanel.displayAnalysisResult(result);
      actionPanel.setProgress(
          100, "Analysis complete - " + result.allModules().size() + " modules detected");
      actionPanel.setExportEnabled(true);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause instanceof SlimJreException) {
        ErrorDialog.show(this, (SlimJreException) cause);
      } else {
        ErrorDialog.show(this, "Analysis Error", (Exception) (cause != null ? cause : e));
      }
      actionPanel.setProgress(0, "Analysis failed");
      lastAnalysisResult = null;
      actionPanel.setExportEnabled(false);
    } finally {
      currentWorker = null;
      updateState();
    }
  }

  private void runCreateJre() {
    List<Path> jars = jarSelectionPanel.getSelectedJars();
    if (jars.isEmpty()) {
      return;
    }

    // Build configuration
    SlimJreConfig config;
    try {
      config = configurationPanel.buildConfig(jars);
      config.validate();
    } catch (SlimJreException e) {
      ErrorDialog.show(this, e);
      return;
    }

    // Disable buttons and show progress
    actionPanel.setAnalyzeEnabled(false);
    actionPanel.setCreateJreEnabled(false);
    actionPanel.setProgress(0, "Starting JRE creation...");

    // Create and run worker
    CreateJreWorker worker = new CreateJreWorker(config);

    worker.addPropertyChangeListener(
        evt -> {
          if ("progress".equals(evt.getPropertyName())) {
            CreateJreWorker.ProgressUpdate update =
                (CreateJreWorker.ProgressUpdate) evt.getNewValue();
            actionPanel.setProgress(update.percent(), update.message());
          } else if ("state".equals(evt.getPropertyName())
              && SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
            onCreateJreComplete(worker);
          }
        });

    currentWorker = worker;
    worker.execute();
  }

  private void onCreateJreComplete(CreateJreWorker worker) {
    try {
      Result result = worker.get();
      resultsPanel.displayCreationResult(result);
      actionPanel.setProgress(100, "JRE created successfully!");

      // Offer to open output folder
      int choice =
          JOptionPane.showConfirmDialog(
              this,
              String.format(
                  "JRE created successfully!\n\n"
                      + "Location: %s\n"
                      + "Size: %s (%.1f%% reduction)\n\n"
                      + "Open output folder?",
                  result.jrePath(),
                  com.ghiloufi.slimjre.gui.util.SizeFormatter.format(result.slimJreSize()),
                  result.reductionPercentage()),
              "Success",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.INFORMATION_MESSAGE);

      if (choice == JOptionPane.YES_OPTION) {
        Desktop.getDesktop().open(result.jrePath().toFile());
      }
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause instanceof SlimJreException) {
        ErrorDialog.show(this, (SlimJreException) cause);
      } else {
        ErrorDialog.show(this, "JRE Creation Error", (Exception) (cause != null ? cause : e));
      }
      actionPanel.setProgress(0, "JRE creation failed");
    } finally {
      currentWorker = null;
      updateState();
    }
  }

  private void saveConfiguration() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Save Configuration");
    chooser.setFileFilter(new FileNameExtensionFilter("JSON Configuration (*.json)", "json"));
    chooser.setSelectedFile(new java.io.File("slim-jre-config.json"));

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path outputPath = chooser.getSelectedFile().toPath();
      if (!outputPath.toString().endsWith(".json")) {
        outputPath = Path.of(outputPath + ".json");
      }

      try {
        List<Path> jars = jarSelectionPanel.getSelectedJars();
        GuiConfiguration config =
            new GuiConfiguration(
                jars,
                configurationPanel.getOutputDirectory(),
                configurationPanel.isStripDebug(),
                configurationPanel.isScanServiceLoaders(),
                configurationPanel.isScanGraalVmMetadata(),
                configurationPanel.isVerbose(),
                configurationPanel.getCompression(),
                configurationPanel.getAdditionalModules(),
                configurationPanel.getExcludeModules());

        ConfigurationManager.saveConfiguration(config, outputPath);
        ErrorDialog.showInfo(this, "Configuration Saved", "Configuration saved to:\n" + outputPath);
      } catch (IOException ex) {
        ErrorDialog.showMessage(
            this, "Save Error", "Failed to save configuration:\n" + ex.getMessage());
      }
    }
  }

  private void loadConfiguration() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Load Configuration");
    chooser.setFileFilter(new FileNameExtensionFilter("JSON Configuration (*.json)", "json"));

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path inputPath = chooser.getSelectedFile().toPath();

      try {
        GuiConfiguration config = ConfigurationManager.loadConfiguration(inputPath);

        // Apply to JAR selection panel
        jarSelectionPanel.clearJars();
        for (Path jar : config.jarFiles()) {
          if (java.nio.file.Files.exists(jar)) {
            jarSelectionPanel.addJar(jar);
          }
        }

        // Apply to configuration panel
        configurationPanel.setOutputDirectory(config.outputDirectory());
        configurationPanel.setStripDebug(config.stripDebug());
        configurationPanel.setScanServiceLoaders(config.scanServiceLoaders());
        configurationPanel.setScanGraalVmMetadata(config.scanGraalVmMetadata());
        configurationPanel.setVerbose(config.verbose());
        configurationPanel.setCompression(config.compression());
        configurationPanel.setAdditionalModules(config.additionalModules());
        configurationPanel.setExcludeModules(config.excludeModules());

        updateState();
        ErrorDialog.showInfo(
            this, "Configuration Loaded", "Configuration loaded from:\n" + inputPath);
      } catch (IOException ex) {
        ErrorDialog.showMessage(
            this, "Load Error", "Failed to load configuration:\n" + ex.getMessage());
      }
    }
  }

  private void exportReport() {
    if (lastAnalysisResult == null) {
      ErrorDialog.showWarning(this, "No Data", "Run analysis first to generate a report.");
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export Report");
    chooser.addChoosableFileFilter(new FileNameExtensionFilter("Text Report (*.txt)", "txt"));
    chooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON Report (*.json)", "json"));
    chooser.setFileFilter(chooser.getChoosableFileFilters()[1]); // Default to text
    chooser.setSelectedFile(new java.io.File("slim-jre-report.txt"));

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path outputPath = chooser.getSelectedFile().toPath();
      String filterDesc = chooser.getFileFilter().getDescription();
      boolean isJson = filterDesc.contains("JSON");

      // Ensure correct extension
      if (isJson && !outputPath.toString().endsWith(".json")) {
        outputPath = Path.of(outputPath.toString().replaceAll("\\.txt$", "") + ".json");
      } else if (!isJson && !outputPath.toString().endsWith(".txt")) {
        outputPath = Path.of(outputPath.toString().replaceAll("\\.json$", "") + ".txt");
      }

      try {
        List<Path> jars = jarSelectionPanel.getSelectedJars();
        if (isJson) {
          ReportExporter.exportAnalysisToJson(lastAnalysisResult, jars, outputPath);
        } else {
          ReportExporter.exportAnalysisToText(lastAnalysisResult, jars, outputPath);
        }
        ErrorDialog.showInfo(this, "Report Exported", "Report exported to:\n" + outputPath);
      } catch (IOException ex) {
        ErrorDialog.showMessage(
            this, "Export Error", "Failed to export report:\n" + ex.getMessage());
      }
    }
  }

  private void showAboutDialog() {
    JOptionPane.showMessageDialog(
        this,
        """
            Slim JRE GUI

            Version: 1.0.0-SNAPSHOT

            A graphical interface for creating minimal custom JREs
            for your Java applications using jdeps and jlink.

            Requires: JDK 21+
            """,
        "About Slim JRE",
        JOptionPane.INFORMATION_MESSAGE);
  }
}
