package com.ghiloufi.slimjre.gui;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.Result;
import com.ghiloufi.slimjre.config.SlimJreConfig;
import com.ghiloufi.slimjre.exception.SlimJreException;
import com.ghiloufi.slimjre.gui.components.ActionPanel;
import com.ghiloufi.slimjre.gui.components.ConfigurationPanel;
import com.ghiloufi.slimjre.gui.components.JarSelectionPanel;
import com.ghiloufi.slimjre.gui.components.ResultsPanel;
import com.ghiloufi.slimjre.gui.workers.AnalysisWorker;
import com.ghiloufi.slimjre.gui.workers.CreateJreWorker;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;

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

  private SwingWorker<?, ?> currentWorker;

  /** Creates the main application frame with all components. */
  public MainFrame() {
    super(TITLE);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    setMinimumSize(new Dimension(700, 500));
    setLocationRelativeTo(null);

    // Initialize components
    jarSelectionPanel = new JarSelectionPanel();
    configurationPanel = new ConfigurationPanel();
    resultsPanel = new ResultsPanel();
    actionPanel = new ActionPanel();
    statusLabel = new JLabel("Ready");

    // Set up layout
    initializeLayout();

    // Set up menu bar
    setJMenuBar(createMenuBar());

    // Wire up actions
    wireActions();

    // Initial state
    updateState();
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

    JMenuItem exitItem = new JMenuItem("Exit");
    exitItem.setAccelerator(KeyStroke.getKeyStroke("alt F4"));
    exitItem.addActionListener(e -> dispose());
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
      resultsPanel.displayAnalysisResult(result);
      actionPanel.setProgress(
          100, "Analysis complete - " + result.allModules().size() + " modules detected");
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause instanceof SlimJreException) {
        showError("Analysis Error", cause.getMessage());
      } else {
        showError("Unexpected Error", e.getMessage());
      }
      actionPanel.setProgress(0, "Analysis failed");
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
      showError("Configuration Error", e.getMessage());
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
        showError("JRE Creation Error", cause.getMessage());
      } else {
        showError("Unexpected Error", e.getMessage());
      }
      actionPanel.setProgress(0, "JRE creation failed");
    } finally {
      currentWorker = null;
      updateState();
    }
  }

  private void showError(String title, String message) {
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
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
