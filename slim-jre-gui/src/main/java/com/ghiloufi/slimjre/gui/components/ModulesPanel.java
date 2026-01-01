package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.AnalysisResult;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Unified panel showing JAR files tree and their modules in a split view.
 *
 * <p>Left side: Tree of JAR files with module counts Right side: Table of modules for selected
 * JAR(s) with source information
 */
public class ModulesPanel extends JPanel {

  // Source colors matching the original PerJarTreePanel
  private static final Color JDEPS_COLOR = new Color(66, 133, 244);
  private static final Color SERVICES_COLOR = new Color(52, 168, 83);
  private static final Color REFLECTION_COLOR = new Color(251, 188, 4);
  private static final Color API_USAGE_COLOR = new Color(234, 67, 53);
  private static final Color GRAALVM_COLOR = new Color(154, 160, 166);
  private static final Color CRYPTO_COLOR = new Color(171, 71, 188);
  private static final Color LOCALE_COLOR = new Color(0, 150, 136);
  private static final Color ZIPFS_COLOR = new Color(255, 152, 0);
  private static final Color JMX_COLOR = new Color(121, 85, 72);

  private final JTree jarTree;
  private final DefaultMutableTreeNode rootNode;
  private final DefaultTreeModel treeModel;
  private final JTable moduleTable;
  private final DefaultTableModel tableModel;
  private final TableRowSorter<DefaultTableModel> rowSorter;
  private final JTextField filterField;
  private final JLabel statusLabel;

  private AnalysisResult currentResult;
  private Map<String, Set<String>> jarToModules;

  /** Creates a new unified modules panel. */
  public ModulesPanel() {
    super(new BorderLayout());

    // Initialize JAR tree (left side)
    rootNode = new DefaultMutableTreeNode("JAR Files");
    treeModel = new DefaultTreeModel(rootNode);
    jarTree = new JTree(treeModel);
    jarTree.setCellRenderer(new JarTreeCellRenderer());
    jarTree.setRootVisible(true);
    jarTree.setShowsRootHandles(true);
    jarTree.addTreeSelectionListener(this::onTreeSelectionChanged);

    // Initialize modules table (right side)
    String[] columns = {"Module", "Source", "Scanner Type"};
    tableModel =
        new DefaultTableModel(columns, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };

    // Initialize filter field early (needed for final field)
    filterField = new JTextField();
    filterField.setToolTipText("Type to filter modules (regex supported)");
    moduleTable = new JTable(tableModel);
    moduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    moduleTable.getTableHeader().setReorderingAllowed(false);
    moduleTable.setDefaultRenderer(Object.class, new ModuleTableCellRenderer());

    // Set column widths
    moduleTable.getColumnModel().getColumn(0).setPreferredWidth(200);
    moduleTable.getColumnModel().getColumn(1).setPreferredWidth(100);
    moduleTable.getColumnModel().getColumn(2).setPreferredWidth(120);

    // Row sorter for filtering and sorting
    rowSorter = new TableRowSorter<>(tableModel);
    moduleTable.setRowSorter(rowSorter);

    // Build left panel (JAR tree with toolbar)
    JPanel leftPanel = buildLeftPanel();

    // Build right panel (modules table with filter)
    JPanel rightPanel = buildRightPanel();

    // Split pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
    splitPane.setResizeWeight(0.35);
    splitPane.setDividerLocation(250);

    add(splitPane, BorderLayout.CENTER);

    // Status bar at bottom
    statusLabel = new JLabel("Select JAR files to view modules");
    statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
    statusLabel.setForeground(Color.GRAY);
    add(statusLabel, BorderLayout.SOUTH);

    // Initialize data structures
    jarToModules = new HashMap<>();
  }

  private JPanel buildLeftPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    // Toolbar
    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    toolBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

    JButton expandBtn = new JButton("Expand");
    expandBtn.setToolTipText("Expand all JAR nodes");
    expandBtn.addActionListener(e -> expandAll());
    toolBar.add(expandBtn);

    JButton collapseBtn = new JButton("Collapse");
    collapseBtn.setToolTipText("Collapse all JAR nodes");
    collapseBtn.addActionListener(e -> collapseAll());
    toolBar.add(collapseBtn);

    toolBar.addSeparator();

    JButton selectAllBtn = new JButton("All");
    selectAllBtn.setToolTipText("Select all JARs");
    selectAllBtn.addActionListener(e -> selectAllJars());
    toolBar.add(selectAllBtn);

    panel.add(toolBar, BorderLayout.NORTH);

    // Tree with scroll
    JScrollPane treeScroll = new JScrollPane(jarTree);
    treeScroll.setBorder(BorderFactory.createTitledBorder("JAR Files"));
    panel.add(treeScroll, BorderLayout.CENTER);

    return panel;
  }

  private JPanel buildRightPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    // Filter bar
    JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
    filterPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JLabel filterLabel = new JLabel("Filter:");
    filterLabel.setPreferredSize(new Dimension(40, 20));
    filterPanel.add(filterLabel, BorderLayout.WEST);

    // Add document listener to filterField (already initialized in constructor)
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

    JButton clearFilterBtn = new JButton("Clear");
    clearFilterBtn.addActionListener(e -> filterField.setText(""));
    filterPanel.add(clearFilterBtn, BorderLayout.EAST);

    panel.add(filterPanel, BorderLayout.NORTH);

    // Table with scroll
    JScrollPane tableScroll = new JScrollPane(moduleTable);
    tableScroll.setBorder(BorderFactory.createTitledBorder("Modules"));
    panel.add(tableScroll, BorderLayout.CENTER);

    // Legend panel at bottom
    panel.add(createLegendPanel(), BorderLayout.SOUTH);

    return panel;
  }

  private JPanel createLegendPanel() {
    JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
    legend.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

    legend.add(createLegendItem("jdeps", JDEPS_COLOR));
    legend.add(createLegendItem("services", SERVICES_COLOR));
    legend.add(createLegendItem("reflection", REFLECTION_COLOR));
    legend.add(createLegendItem("api-usage", API_USAGE_COLOR));
    legend.add(createLegendItem("crypto", CRYPTO_COLOR));
    legend.add(createLegendItem("locale", LOCALE_COLOR));
    legend.add(createLegendItem("zipfs", ZIPFS_COLOR));
    legend.add(createLegendItem("jmx", JMX_COLOR));

    return legend;
  }

  private JLabel createLegendItem(String text, Color color) {
    JLabel label = new JLabel("● " + text);
    label.setForeground(color);
    label.setFont(label.getFont().deriveFont(10f));
    return label;
  }

  private void applyFilter() {
    String text = filterField.getText().trim();
    if (text.isEmpty()) {
      rowSorter.setRowFilter(null);
    } else {
      try {
        rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
      } catch (java.util.regex.PatternSyntaxException e) {
        // Invalid regex, ignore
      }
    }
    updateStatus();
  }

  private void onTreeSelectionChanged(TreeSelectionEvent e) {
    updateModuleTable();
  }

  private void updateModuleTable() {
    tableModel.setRowCount(0);

    if (currentResult == null) {
      updateStatus();
      return;
    }

    TreePath[] selectedPaths = jarTree.getSelectionPaths();
    Set<String> modulesToShow = new TreeSet<>();
    Set<String> selectedJars = new HashSet<>();

    if (selectedPaths == null || selectedPaths.length == 0) {
      // No selection - show nothing or all?
      updateStatus();
      return;
    }

    for (TreePath path : selectedPaths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
      Object userObject = node.getUserObject();

      if (userObject instanceof String && "JAR Files".equals(userObject)) {
        // Root selected - show all modules
        modulesToShow.addAll(currentResult.allModules());
        selectedJars.add("All JARs");
        break;
      } else if (userObject instanceof JarNode jarNode) {
        selectedJars.add(jarNode.name());
        Set<String> jarModules = jarToModules.get(jarNode.name());
        if (jarModules != null) {
          modulesToShow.addAll(jarModules);
        }
      }
    }

    // Populate table
    for (String module : modulesToShow) {
      String source = getModuleSource(currentResult, module);
      String scannerType = formatScannerType(source);
      tableModel.addRow(new Object[] {module, source, scannerType});
    }

    updateStatus();
  }

  private void updateStatus() {
    int total = tableModel.getRowCount();
    int visible = moduleTable.getRowCount();
    String filterText = filterField.getText().trim();

    if (total == 0) {
      statusLabel.setText("Select JAR file(s) from the tree to view modules");
    } else if (!filterText.isEmpty() && visible != total) {
      statusLabel.setText(String.format("Showing %d of %d modules (filtered)", visible, total));
    } else {
      statusLabel.setText(String.format("%d modules", total));
    }
  }

  /**
   * Updates the panel with analysis results.
   *
   * @param result the analysis result to display
   */
  public void setData(AnalysisResult result) {
    this.currentResult = result;
    rootNode.removeAllChildren();
    jarToModules.clear();

    if (result == null) {
      treeModel.reload();
      tableModel.setRowCount(0);
      updateStatus();
      return;
    }

    Map<Path, Set<String>> perJarModules = result.perJarModules();

    if (perJarModules == null || perJarModules.isEmpty()) {
      // No per-JAR data - create single "All Modules" node
      DefaultMutableTreeNode allNode =
          new DefaultMutableTreeNode(new JarNode("All Modules", result.allModules().size()));
      rootNode.add(allNode);
      jarToModules.put("All Modules", result.allModules());
    } else {
      // Create nodes for each JAR
      List<Map.Entry<Path, Set<String>>> sortedEntries =
          perJarModules.entrySet().stream()
              .sorted(Comparator.comparing(e -> e.getKey().getFileName().toString()))
              .toList();

      for (Map.Entry<Path, Set<String>> entry : sortedEntries) {
        String jarName = entry.getKey().getFileName().toString();
        int moduleCount = entry.getValue().size();
        DefaultMutableTreeNode jarNode =
            new DefaultMutableTreeNode(new JarNode(jarName, moduleCount));
        rootNode.add(jarNode);
        jarToModules.put(jarName, entry.getValue());
      }
    }

    treeModel.reload();
    expandAll();

    // Auto-select root to show all modules
    jarTree.setSelectionRow(0);
  }

  /** Clears all data from the panel. */
  public void clear() {
    currentResult = null;
    rootNode.removeAllChildren();
    jarToModules.clear();
    treeModel.reload();
    tableModel.setRowCount(0);
    filterField.setText("");
    statusLabel.setText("Select JAR files to view modules");
  }

  /** Expands all tree nodes. */
  public void expandAll() {
    for (int i = 0; i < jarTree.getRowCount(); i++) {
      jarTree.expandRow(i);
    }
  }

  /** Collapses all tree nodes. */
  public void collapseAll() {
    for (int i = jarTree.getRowCount() - 1; i > 0; i--) {
      jarTree.collapseRow(i);
    }
  }

  /** Selects all JAR nodes (by selecting root). */
  public void selectAllJars() {
    jarTree.setSelectionRow(0);
  }

  private String getModuleSource(AnalysisResult result, String module) {
    if (result.requiredModules().contains(module)) return "jdeps";
    if (result.serviceLoaderModules().contains(module)) return "services";
    if (result.reflectionModules().contains(module)) return "reflection";
    if (result.apiUsageModules().contains(module)) return "api-usage";
    if (result.graalVmMetadataModules().contains(module)) return "graalvm";
    if (result.cryptoModules().contains(module)) return "crypto";
    if (result.localeModules().contains(module)) return "locale";
    if (result.zipFsModules().contains(module)) return "zipfs";
    if (result.jmxModules().contains(module)) return "jmx";
    return "unknown";
  }

  private String formatScannerType(String source) {
    return switch (source) {
      case "jdeps" -> "Static Analysis";
      case "services" -> "Service Loader";
      case "reflection" -> "Reflection Pattern";
      case "api-usage" -> "API Usage";
      case "graalvm" -> "GraalVM Metadata";
      case "crypto" -> "Crypto/SSL/TLS";
      case "locale" -> "Locale/i18n";
      case "zipfs" -> "ZipFS";
      case "jmx" -> "JMX Remote";
      default -> source;
    };
  }

  private Color getSourceColor(String source) {
    return switch (source) {
      case "jdeps" -> JDEPS_COLOR;
      case "services" -> SERVICES_COLOR;
      case "reflection" -> REFLECTION_COLOR;
      case "api-usage" -> API_USAGE_COLOR;
      case "graalvm" -> GRAALVM_COLOR;
      case "crypto" -> CRYPTO_COLOR;
      case "locale" -> LOCALE_COLOR;
      case "zipfs" -> ZIPFS_COLOR;
      case "jmx" -> JMX_COLOR;
      default -> UIManager.getColor("Table.foreground");
    };
  }

  /** Node representing a JAR file. */
  record JarNode(String name, int moduleCount) {
    @Override
    public String toString() {
      return name + " (" + moduleCount + ")";
    }
  }

  /** Custom tree cell renderer for JAR nodes. */
  private class JarTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {

      super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      if (value instanceof DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();

        if (userObject instanceof JarNode) {
          setIcon(UIManager.getIcon("FileView.fileIcon"));
          setFont(getFont().deriveFont(Font.PLAIN));
        } else if (userObject instanceof String) {
          // Root node
          setIcon(UIManager.getIcon("FileView.directoryIcon"));
          setFont(getFont().deriveFont(Font.BOLD));
        }
      }

      return this;
    }
  }

  /** Custom table cell renderer with colored source column. */
  private class ModuleTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      // Get the source value for this row
      int modelRow = table.convertRowIndexToModel(row);
      String source = (String) tableModel.getValueAt(modelRow, 1);

      if (!isSelected && (column == 1 || column == 2)) {
        setForeground(getSourceColor(source));
      } else if (!isSelected) {
        setForeground(UIManager.getColor("Table.foreground"));
      }

      return this;
    }
  }
}
