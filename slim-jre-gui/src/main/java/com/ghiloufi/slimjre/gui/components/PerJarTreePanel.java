package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.AnalysisResult;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

/**
 * Tree view panel showing module breakdown per JAR file.
 *
 * <p>Displays a hierarchical view where each JAR file is a parent node and its required modules are
 * child nodes.
 */
public class PerJarTreePanel extends JPanel {

  private final JTree tree;
  private final DefaultMutableTreeNode rootNode;
  private final DefaultTreeModel treeModel;

  /** Creates a new per-JAR tree panel. */
  public PerJarTreePanel() {
    super(new BorderLayout());

    rootNode = new DefaultMutableTreeNode("JAR Files");
    treeModel = new DefaultTreeModel(rootNode);
    tree = new JTree(treeModel);

    // Custom renderer
    tree.setCellRenderer(new ModuleTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    // Enable multiple selection for copying
    tree.getSelectionModel()
        .setSelectionMode(javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    JScrollPane scrollPane = new JScrollPane(tree);
    add(scrollPane, BorderLayout.CENTER);

    // Toolbar with expand/collapse buttons
    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);

    JButton expandAllBtn = new JButton("Expand All");
    expandAllBtn.addActionListener(e -> expandAll());
    toolBar.add(expandAllBtn);

    JButton collapseAllBtn = new JButton("Collapse All");
    collapseAllBtn.addActionListener(e -> collapseAll());
    toolBar.add(collapseAllBtn);

    add(toolBar, BorderLayout.NORTH);
  }

  /**
   * Updates the tree with analysis results.
   *
   * @param result the analysis result to display
   */
  public void setData(AnalysisResult result) {
    rootNode.removeAllChildren();

    Map<Path, Set<String>> perJarModules = result.perJarModules();
    if (perJarModules == null || perJarModules.isEmpty()) {
      // Fall back to showing all modules under a single node
      DefaultMutableTreeNode allNode = new DefaultMutableTreeNode("All Modules");
      Set<String> sortedModules = new TreeSet<>(result.allModules());
      for (String module : sortedModules) {
        allNode.add(
            new DefaultMutableTreeNode(new ModuleNode(module, getModuleSource(result, module))));
      }
      rootNode.add(allNode);
    } else {
      // Show per-JAR breakdown
      for (Map.Entry<Path, Set<String>> entry : perJarModules.entrySet()) {
        String jarName = entry.getKey().getFileName().toString();
        int moduleCount = entry.getValue().size();
        DefaultMutableTreeNode jarNode =
            new DefaultMutableTreeNode(new JarNode(jarName, moduleCount));

        Set<String> sortedModules = new TreeSet<>(entry.getValue());
        for (String module : sortedModules) {
          jarNode.add(
              new DefaultMutableTreeNode(new ModuleNode(module, getModuleSource(result, module))));
        }

        rootNode.add(jarNode);
      }
    }

    treeModel.reload();
    expandAll();
  }

  private String getModuleSource(AnalysisResult result, String module) {
    if (result.requiredModules().contains(module)) return "jdeps";
    if (result.serviceLoaderModules().contains(module)) return "services";
    if (result.reflectionModules().contains(module)) return "reflection";
    if (result.apiUsageModules().contains(module)) return "api_usage";
    if (result.graalVmMetadataModules().contains(module)) return "graalvm";
    return "unknown";
  }

  /** Clears the tree. */
  public void clear() {
    rootNode.removeAllChildren();
    treeModel.reload();
  }

  /** Expands all tree nodes. */
  public void expandAll() {
    for (int i = 0; i < tree.getRowCount(); i++) {
      tree.expandRow(i);
    }
  }

  /** Collapses all tree nodes. */
  public void collapseAll() {
    for (int i = tree.getRowCount() - 1; i >= 0; i--) {
      tree.collapseRow(i);
    }
  }

  /** Node representing a JAR file. */
  record JarNode(String name, int moduleCount) {
    @Override
    public String toString() {
      return name + " (" + moduleCount + " modules)";
    }
  }

  /** Node representing a module. */
  record ModuleNode(String name, String source) {
    @Override
    public String toString() {
      return name;
    }
  }

  /** Custom tree cell renderer with icons and colors. */
  private static class ModuleTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Color JDEPS_COLOR = new Color(66, 133, 244);
    private static final Color SERVICES_COLOR = new Color(52, 168, 83);
    private static final Color REFLECTION_COLOR = new Color(251, 188, 4);
    private static final Color API_USAGE_COLOR = new Color(234, 67, 53);
    private static final Color GRAALVM_COLOR = new Color(154, 160, 166);

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
          setFont(getFont().deriveFont(Font.BOLD));
        } else if (userObject instanceof ModuleNode moduleNode) {
          setIcon(null);
          if (!selected) {
            setForeground(getSourceColor(moduleNode.source()));
          }
          setToolTipText("Source: " + formatSource(moduleNode.source()));
        }
      }

      return this;
    }

    private Color getSourceColor(String source) {
      return switch (source) {
        case "jdeps" -> JDEPS_COLOR;
        case "services" -> SERVICES_COLOR;
        case "reflection" -> REFLECTION_COLOR;
        case "api_usage" -> API_USAGE_COLOR;
        case "graalvm" -> GRAALVM_COLOR;
        default -> UIManager.getColor("Tree.textForeground");
      };
    }

    private String formatSource(String source) {
      return switch (source) {
        case "jdeps" -> "jdeps (static analysis)";
        case "services" -> "Service loader";
        case "reflection" -> "Reflection pattern";
        case "api_usage" -> "API usage pattern";
        case "graalvm" -> "GraalVM metadata";
        default -> source;
      };
    }
  }
}
