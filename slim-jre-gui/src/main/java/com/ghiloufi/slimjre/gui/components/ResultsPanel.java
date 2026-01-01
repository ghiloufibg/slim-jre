package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.AnalysisResult;
import com.ghiloufi.slimjre.config.Result;
import com.ghiloufi.slimjre.gui.util.SizeFormatter;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;

/**
 * Panel for displaying analysis and creation results.
 *
 * <p>Provides a tabbed interface with:
 *
 * <ul>
 *   <li>Summary view with module breakdown statistics for all 9 scanner types
 *   <li>Unified Modules view combining JAR tree and module table
 * </ul>
 */
public class ResultsPanel extends JPanel {

  private final JTabbedPane tabbedPane;
  private final JTextArea summaryArea;
  private final ModulesPanel modulesPanel;

  /** Creates a new results panel with tabbed display. */
  public ResultsPanel() {
    super(new BorderLayout());

    tabbedPane = new JTabbedPane();

    // Summary tab
    JPanel summaryPanel = new JPanel(new BorderLayout(10, 10));
    summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    summaryArea = new JTextArea();
    summaryArea.setEditable(false);
    summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    summaryArea.setText(getEmptyStateText());
    JScrollPane summaryScroll = new JScrollPane(summaryArea);

    summaryPanel.add(summaryScroll, BorderLayout.CENTER);
    tabbedPane.addTab("Summary", summaryPanel);

    // Unified Modules tab (combines old Modules + Per-JAR tabs)
    modulesPanel = new ModulesPanel();
    tabbedPane.addTab("Modules", modulesPanel);

    add(tabbedPane, BorderLayout.CENTER);
  }

  private String getEmptyStateText() {
    return
    """


              Select JAR files and click "Analyze" to see
              the required JDK modules for your application.

              Or click "Create JRE" to build a minimal
              custom JRE directly.
            """;
  }

  /**
   * Displays the analysis result in the panel.
   *
   * @param result the analysis result to display
   */
  public void displayAnalysisResult(AnalysisResult result) {
    StringBuilder sb = new StringBuilder();

    // Header
    sb.append("\n");
    sb.append("  Analysis Complete\n");
    sb.append("\n");

    // Main result - total modules
    int totalModules = result.allModules().size();
    sb.append(String.format("  Found %d required JDK modules\n", totalModules));
    sb.append("\n");

    // Per-JAR breakdown (if multiple JARs)
    Map<Path, Set<String>> perJar = result.perJarModules();
    if (perJar.size() > 1) {
      sb.append("  Modules per JAR:\n");
      for (var entry : perJar.entrySet()) {
        sb.append(
            String.format(
                "    • %s: %d modules\n", entry.getKey().getFileName(), entry.getValue().size()));
      }
      sb.append("\n");
    }

    // Module list
    sb.append("  Required Modules:\n");
    String moduleList = result.allModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 50)).append("\n");
    sb.append("\n");

    // Next step hint
    sb.append("  Click \"Create JRE\" to build a minimal runtime.\n");

    summaryArea.setText(sb.toString());
    summaryArea.setCaretPosition(0);

    // Update unified modules panel
    modulesPanel.setData(result);

    // Select summary tab
    tabbedPane.setSelectedIndex(0);
  }

  /**
   * Displays the JRE creation result in the panel.
   *
   * @param result the creation result to display
   */
  public void displayCreationResult(Result result) {
    StringBuilder sb = new StringBuilder();

    // Header
    sb.append("\n");
    sb.append("  JRE Created Successfully!\n");
    sb.append("\n");

    // Size savings - the key metric
    sb.append(String.format("  Size: %s", SizeFormatter.format(result.slimJreSize())));
    sb.append(String.format(" (%.0f%% smaller than full JDK)\n", result.reductionPercentage()));
    sb.append("\n");

    // Location
    sb.append("  Location:\n");
    sb.append("    ").append(result.jrePath()).append("\n");
    sb.append("\n");

    // Modules included
    sb.append(String.format("  Includes %d modules:\n", result.includedModules().size()));
    String moduleList =
        result.includedModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 50)).append("\n");

    summaryArea.setText(sb.toString());
    summaryArea.setCaretPosition(0);

    // Clear modules panel (no per-JAR data for creation results)
    modulesPanel.clear();

    // Select summary tab
    tabbedPane.setSelectedIndex(0);
  }

  /** Clears all results from the panel. */
  public void clear() {
    summaryArea.setText(getEmptyStateText());
    modulesPanel.clear();
  }

  private String wrapText(String text, int width) {
    StringBuilder sb = new StringBuilder();
    int lineLength = 0;
    for (String word : text.split(", ")) {
      if (lineLength + word.length() + 2 > width) {
        sb.append(",\n  ");
        lineLength = 2;
      } else if (lineLength > 0) {
        sb.append(", ");
        lineLength += 2;
      } else {
        sb.append("  ");
        lineLength = 2;
      }
      sb.append(word);
      lineLength += word.length();
    }
    return sb.toString();
  }

  private String formatDuration(java.time.Duration duration) {
    long seconds = duration.getSeconds();
    long millis = duration.toMillisPart();
    if (seconds > 0) {
      return String.format("%d.%03d seconds", seconds, millis);
    } else {
      return String.format("%d ms", millis);
    }
  }
}
