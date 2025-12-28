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
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Panel for displaying analysis and creation results.
 *
 * <p>Provides a tabbed interface with:
 *
 * <ul>
 *   <li>Summary view with module breakdown statistics
 *   <li>Module table with sorting and filtering
 *   <li>Text output for detailed information
 * </ul>
 */
public class ResultsPanel extends JPanel {

  private final JTabbedPane tabbedPane;
  private final JTextArea summaryArea;
  private final ModuleSourceChart moduleChart;
  private final JTable moduleTable;
  private final DefaultTableModel tableModel;
  private final JTextField filterField;
  private final TableRowSorter<DefaultTableModel> rowSorter;
  private final PerJarTreePanel perJarTreePanel;

  /** Creates a new results panel with tabbed display. */
  public ResultsPanel() {
    super(new BorderLayout());

    tabbedPane = new JTabbedPane();

    // Summary tab with chart
    JPanel summaryPanel = new JPanel(new BorderLayout(10, 10));
    summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    summaryArea = new JTextArea();
    summaryArea.setEditable(false);
    summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    summaryArea.setText(getEmptyStateText());
    JScrollPane summaryScroll = new JScrollPane(summaryArea);

    moduleChart = new ModuleSourceChart();
    moduleChart.setPreferredSize(new java.awt.Dimension(280, 260));

    // Split pane for summary text and chart
    JSplitPane summarySplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    summarySplit.setLeftComponent(summaryScroll);
    summarySplit.setRightComponent(moduleChart);
    summarySplit.setResizeWeight(0.6);
    summarySplit.setDividerLocation(350);

    summaryPanel.add(summarySplit, BorderLayout.CENTER);
    tabbedPane.addTab("Summary", summaryPanel);

    // Modules tab
    String[] columns = {"Module", "Source"};
    tableModel =
        new DefaultTableModel(columns, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };
    moduleTable = new JTable(tableModel);
    moduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    moduleTable.getTableHeader().setReorderingAllowed(false);

    // Row sorter for filtering and sorting
    rowSorter = new TableRowSorter<>(tableModel);
    moduleTable.setRowSorter(rowSorter);

    // Filter panel
    JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
    filterPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    filterPanel.add(new JLabel("Filter: "), BorderLayout.WEST);
    filterField = new JTextField();
    filterField.addActionListener(e -> applyFilter());
    filterField
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              @Override
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
              }
            });
    filterPanel.add(filterField, BorderLayout.CENTER);

    JPanel modulesPanel = new JPanel(new BorderLayout());
    modulesPanel.add(filterPanel, BorderLayout.NORTH);
    modulesPanel.add(new JScrollPane(moduleTable), BorderLayout.CENTER);
    tabbedPane.addTab("Modules", modulesPanel);

    // Per-JAR tree view tab
    perJarTreePanel = new PerJarTreePanel();
    tabbedPane.addTab("Per-JAR", perJarTreePanel);

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

  private void applyFilter() {
    String text = filterField.getText().trim();
    if (text.isEmpty()) {
      rowSorter.setRowFilter(null);
    } else {
      rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
    }
  }

  /**
   * Displays the analysis result in the panel.
   *
   * @param result the analysis result to display
   */
  public void displayAnalysisResult(AnalysisResult result) {
    // Build summary text
    StringBuilder sb = new StringBuilder();
    sb.append("=".repeat(50)).append("\n");
    sb.append("  ANALYSIS RESULTS\n");
    sb.append("=".repeat(50)).append("\n\n");

    sb.append("Module Detection Summary:\n");
    sb.append("-".repeat(30)).append("\n");
    sb.append(
        String.format("  jdeps (static):      %3d modules\n", result.requiredModules().size()));
    sb.append(
        String.format(
            "  Service loaders:     %3d modules\n", result.serviceLoaderModules().size()));
    sb.append(
        String.format("  Reflection:          %3d modules\n", result.reflectionModules().size()));
    sb.append(
        String.format("  API usage:           %3d modules\n", result.apiUsageModules().size()));
    sb.append(
        String.format(
            "  GraalVM metadata:    %3d modules\n", result.graalVmMetadataModules().size()));
    sb.append("-".repeat(30)).append("\n");
    sb.append(String.format("  TOTAL:               %3d modules\n", result.allModules().size()));
    sb.append("\n");

    // Per-JAR breakdown
    Map<Path, Set<String>> perJar = result.perJarModules();
    if (!perJar.isEmpty()) {
      sb.append("Per-JAR Breakdown:\n");
      sb.append("-".repeat(30)).append("\n");
      for (var entry : perJar.entrySet()) {
        sb.append(
            String.format(
                "  %s: %d modules\n", entry.getKey().getFileName(), entry.getValue().size()));
      }
      sb.append("\n");
    }

    // All modules list
    sb.append("All Required Modules:\n");
    sb.append("-".repeat(30)).append("\n");
    String moduleList = result.allModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 48)).append("\n\n");

    // jlink command
    sb.append("Recommended jlink command:\n");
    sb.append("-".repeat(30)).append("\n");
    String modules = result.allModules().stream().sorted().collect(Collectors.joining(","));
    sb.append("jlink --add-modules ").append(modules).append(" \\\n");
    sb.append("      --strip-debug --compress zip-6 \\\n");
    sb.append("      --no-header-files --no-man-pages \\\n");
    sb.append("      --output slim-jre\n");

    summaryArea.setText(sb.toString());
    summaryArea.setCaretPosition(0);

    // Update pie chart
    moduleChart.setData(result);

    // Populate module table
    tableModel.setRowCount(0);
    addModulesToTable(result.requiredModules(), "jdeps");
    addModulesToTable(result.serviceLoaderModules(), "service-loader");
    addModulesToTable(result.reflectionModules(), "reflection");
    addModulesToTable(result.apiUsageModules(), "api-usage");
    addModulesToTable(result.graalVmMetadataModules(), "graalvm");

    // Update per-JAR tree
    perJarTreePanel.setData(result);

    // Select summary tab
    tabbedPane.setSelectedIndex(0);
  }

  private void addModulesToTable(Set<String> modules, String source) {
    for (String module : modules.stream().sorted().toList()) {
      // Check if already in table (avoid duplicates, keep first source)
      boolean exists = false;
      for (int i = 0; i < tableModel.getRowCount(); i++) {
        if (tableModel.getValueAt(i, 0).equals(module)) {
          exists = true;
          break;
        }
      }
      if (!exists) {
        tableModel.addRow(new Object[] {module, source});
      }
    }
  }

  /**
   * Displays the JRE creation result in the panel.
   *
   * @param result the creation result to display
   */
  public void displayCreationResult(Result result) {
    StringBuilder sb = new StringBuilder();
    sb.append("=".repeat(50)).append("\n");
    sb.append("  JRE CREATION COMPLETE\n");
    sb.append("=".repeat(50)).append("\n\n");

    sb.append("Output Location:\n");
    sb.append("  ").append(result.jrePath()).append("\n\n");

    sb.append("Size Comparison:\n");
    sb.append("-".repeat(30)).append("\n");
    sb.append(
        String.format("  Original JDK:  %s\n", SizeFormatter.format(result.originalJreSize())));
    sb.append(String.format("  Slim JRE:      %s\n", SizeFormatter.format(result.slimJreSize())));
    sb.append(String.format("  Reduction:     %.1f%%\n", result.reductionPercentage()));
    sb.append("\n");

    sb.append("Included Modules: ").append(result.includedModules().size()).append("\n");
    sb.append("-".repeat(30)).append("\n");
    String moduleList =
        result.includedModules().stream().sorted().collect(Collectors.joining(", "));
    sb.append(wrapText(moduleList, 48)).append("\n\n");

    sb.append("Duration: ").append(formatDuration(result.duration())).append("\n");

    summaryArea.setText(sb.toString());
    summaryArea.setCaretPosition(0);

    // Update module table
    tableModel.setRowCount(0);
    for (String module : result.includedModules().stream().sorted().toList()) {
      tableModel.addRow(new Object[] {module, "included"});
    }

    // Select summary tab
    tabbedPane.setSelectedIndex(0);
  }

  /** Clears all results from the panel. */
  public void clear() {
    summaryArea.setText(getEmptyStateText());
    moduleChart.clear();
    tableModel.setRowCount(0);
    filterField.setText("");
    perJarTreePanel.clear();
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
