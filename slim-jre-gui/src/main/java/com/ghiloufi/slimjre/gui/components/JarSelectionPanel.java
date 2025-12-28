package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.gui.util.SizeFormatter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Panel for selecting JAR files with drag-drop support.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>JList displaying JAR paths and sizes
 *   <li>Drag-and-drop from file explorer
 *   <li>Add JAR / Add Folder buttons
 *   <li>Remove / Clear buttons
 * </ul>
 */
public class JarSelectionPanel extends JPanel {

  private final DefaultListModel<JarEntry> jarListModel;
  private final JList<JarEntry> jarList;
  private final List<ChangeListener> changeListeners;
  private Path lastDirectory;

  /** Creates a new JAR selection panel. */
  public JarSelectionPanel() {
    super(new BorderLayout(10, 0));
    this.jarListModel = new DefaultListModel<>();
    this.jarList = new JList<>(jarListModel);
    this.changeListeners = new ArrayList<>();
    this.lastDirectory = Path.of(System.getProperty("user.home"));

    initializeComponents();
    setupDragAndDrop();
  }

  private void initializeComponents() {
    // JAR list with scroll pane
    jarList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    jarList.setCellRenderer(new JarEntryCellRenderer());
    jarList.setVisibleRowCount(6);

    JScrollPane scrollPane = new JScrollPane(jarList);
    scrollPane.setPreferredSize(new Dimension(400, 150));

    // Empty state message
    jarList.setBackground(UIManager.getColor("List.background"));

    add(scrollPane, BorderLayout.CENTER);

    // Buttons panel
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

    JButton addJarButton = new JButton("Add JAR...");
    addJarButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    addJarButton.setMaximumSize(new Dimension(120, 28));
    addJarButton.addActionListener(e -> showAddJarDialog());

    JButton addFolderButton = new JButton("Add Folder...");
    addFolderButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    addFolderButton.setMaximumSize(new Dimension(120, 28));
    addFolderButton.addActionListener(e -> showAddFolderDialog());

    JButton removeButton = new JButton("Remove");
    removeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    removeButton.setMaximumSize(new Dimension(120, 28));
    removeButton.addActionListener(e -> removeSelected());

    JButton clearButton = new JButton("Clear All");
    clearButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    clearButton.setMaximumSize(new Dimension(120, 28));
    clearButton.addActionListener(e -> clearAll());

    buttonPanel.add(addJarButton);
    buttonPanel.add(Box.createVerticalStrut(5));
    buttonPanel.add(addFolderButton);
    buttonPanel.add(Box.createVerticalStrut(15));
    buttonPanel.add(removeButton);
    buttonPanel.add(Box.createVerticalStrut(5));
    buttonPanel.add(clearButton);
    buttonPanel.add(Box.createVerticalGlue());

    add(buttonPanel, BorderLayout.EAST);

    // Help label at bottom
    JLabel helpLabel = new JLabel("Drag and drop JAR files here, or use the buttons to add files");
    helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
    helpLabel.setForeground(Color.GRAY);
    add(helpLabel, BorderLayout.SOUTH);
  }

  private void setupDragAndDrop() {
    new DropTarget(
        jarList,
        DnDConstants.ACTION_COPY,
        new DropTargetAdapter() {
          @Override
          public void drop(DropTargetDropEvent event) {
            try {
              event.acceptDrop(DnDConstants.ACTION_COPY);
              @SuppressWarnings("unchecked")
              List<File> files =
                  (List<File>)
                      event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

              for (File file : files) {
                if (file.isDirectory()) {
                  addDirectory(file.toPath());
                } else if (file.getName().toLowerCase().endsWith(".jar")) {
                  addJar(file.toPath());
                }
              }
              event.dropComplete(true);
            } catch (Exception e) {
              event.dropComplete(false);
            }
          }

          @Override
          public void dragOver(DropTargetDragEvent event) {
            if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
              event.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
              event.rejectDrag();
            }
          }
        });
  }

  /** Shows a file chooser dialog to select JAR files. */
  public void showAddJarDialog() {
    JFileChooser chooser = new JFileChooser(lastDirectory.toFile());
    chooser.setDialogTitle("Select JAR Files");
    chooser.setMultiSelectionEnabled(true);
    chooser.setFileFilter(
        new javax.swing.filechooser.FileFilter() {
          @Override
          public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith(".jar");
          }

          @Override
          public String getDescription() {
            return "JAR Files (*.jar)";
          }
        });

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      lastDirectory = chooser.getCurrentDirectory().toPath();
      for (File file : chooser.getSelectedFiles()) {
        addJar(file.toPath());
      }
    }
  }

  /** Shows a directory chooser to add all JARs from a folder. */
  public void showAddFolderDialog() {
    JFileChooser chooser = new JFileChooser(lastDirectory.toFile());
    chooser.setDialogTitle("Select Folder Containing JARs");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      lastDirectory = chooser.getSelectedFile().toPath();
      addDirectory(lastDirectory);
    }
  }

  /**
   * Adds a JAR file to the list.
   *
   * @param jarPath path to the JAR file
   */
  public void addJar(Path jarPath) {
    if (!Files.exists(jarPath)) {
      return;
    }

    // Check for duplicates
    for (int i = 0; i < jarListModel.size(); i++) {
      if (jarListModel.get(i).path().equals(jarPath)) {
        return;
      }
    }

    try {
      long size = Files.size(jarPath);
      jarListModel.addElement(new JarEntry(jarPath, size));
      fireChangeEvent();
    } catch (IOException e) {
      // Ignore files we can't read
    }
  }

  /**
   * Adds all JAR files from a directory.
   *
   * @param directory path to the directory
   */
  public void addDirectory(Path directory) {
    if (!Files.isDirectory(directory)) {
      return;
    }

    try (Stream<Path> stream = Files.list(directory)) {
      stream
          .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
          .filter(Files::isRegularFile)
          .forEach(this::addJar);
    } catch (IOException e) {
      // Ignore directories we can't read
    }
  }

  /** Removes the selected JAR entries from the list. */
  public void removeSelected() {
    int[] indices = jarList.getSelectedIndices();
    // Remove from end to start to maintain valid indices
    for (int i = indices.length - 1; i >= 0; i--) {
      jarListModel.remove(indices[i]);
    }
    fireChangeEvent();
  }

  /** Clears all JAR entries from the list. */
  public void clearAll() {
    jarListModel.clear();
    fireChangeEvent();
  }

  /**
   * Returns the list of selected JAR paths.
   *
   * @return list of JAR paths
   */
  public List<Path> getSelectedJars() {
    List<Path> jars = new ArrayList<>();
    for (int i = 0; i < jarListModel.size(); i++) {
      jars.add(jarListModel.get(i).path());
    }
    return jars;
  }

  /**
   * Adds a change listener to be notified when the JAR list changes.
   *
   * @param listener the listener to add
   */
  public void addChangeListener(ChangeListener listener) {
    changeListeners.add(listener);
  }

  private void fireChangeEvent() {
    ChangeEvent event = new ChangeEvent(this);
    for (ChangeListener listener : changeListeners) {
      listener.stateChanged(event);
    }
  }

  /** Represents a JAR file entry with its path and size. */
  public record JarEntry(Path path, long size) {}

  /** Custom cell renderer for JAR entries. */
  private static class JarEntryCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof JarEntry entry) {
        String displayName = entry.path().getFileName().toString();
        String sizeStr = SizeFormatter.format(entry.size());
        setText(String.format("%s  (%s)", displayName, sizeStr));
        setToolTipText(entry.path().toString());
      }

      return this;
    }
  }
}
