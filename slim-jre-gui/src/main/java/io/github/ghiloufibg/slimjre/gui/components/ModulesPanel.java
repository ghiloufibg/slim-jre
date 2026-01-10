package io.github.ghiloufibg.slimjre.gui.components;

import io.github.ghiloufibg.slimjre.config.AnalysisResult;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Unified panel showing JAR files tree and their modules in a split view.
 *
 * <p>Left side: Tree of JAR files with module counts Right side: List of modules for selected
 * JAR(s)
 */
public class ModulesPanel extends JPanel {

  private final JTree jarTree;
  private final DefaultMutableTreeNode rootNode;
  private final DefaultTreeModel treeModel;
  private final JTable moduleTable;
  private final DefaultTableModel tableModel;
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

    // Initialize modules table (right side) - simple single column
    String[] columns = {"Module"};
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

    // Table with scroll
    JScrollPane tableScroll = new JScrollPane(moduleTable);
    tableScroll.setBorder(BorderFactory.createTitledBorder("Modules"));
    panel.add(tableScroll, BorderLayout.CENTER);

    return panel;
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

    // Populate table with module names only
    for (String module : modulesToShow) {
      tableModel.addRow(new Object[] {module});
    }

    updateStatus();
  }

  private void updateStatus() {
    int total = tableModel.getRowCount();

    if (total == 0) {
      statusLabel.setText("Select JAR file(s) from the tree to view modules");
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
    for (int i = jarTree.getRowCount() - 1; i >= 0; i--) {
      jarTree.collapseRow(i);
    }
  }

  /** Selects all JAR nodes (by selecting root). */
  public void selectAllJars() {
    jarTree.setSelectionRow(0);
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
}
