package com.ghiloufi.slimjre.gui.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.TransferHandler;

/**
 * Transfer handler for drag-and-drop file operations.
 *
 * <p>Supports dropping JAR files and directories from the file explorer. Directories are scanned
 * recursively for JAR files.
 */
public class FileDropHandler extends TransferHandler {

  private final Consumer<List<Path>> jarConsumer;
  private final Consumer<List<Path>> directoryConsumer;

  /**
   * Creates a new file drop handler.
   *
   * @param jarConsumer callback for dropped JAR files
   * @param directoryConsumer callback for dropped directories (optional, can be null)
   */
  public FileDropHandler(Consumer<List<Path>> jarConsumer, Consumer<List<Path>> directoryConsumer) {
    this.jarConsumer = jarConsumer;
    this.directoryConsumer = directoryConsumer;
  }

  /**
   * Creates a new file drop handler that processes both JARs and directories as JARs.
   *
   * @param jarConsumer callback for all dropped JAR files (including those from directories)
   */
  public FileDropHandler(Consumer<List<Path>> jarConsumer) {
    this(jarConsumer, null);
  }

  @Override
  public boolean canImport(TransferSupport support) {
    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
  }

  @Override
  public boolean importData(TransferSupport support) {
    if (!canImport(support)) {
      return false;
    }

    try {
      Transferable transferable = support.getTransferable();
      @SuppressWarnings("unchecked")
      List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

      List<Path> jars = new ArrayList<>();
      List<Path> directories = new ArrayList<>();

      for (File file : files) {
        Path path = file.toPath();
        if (file.isDirectory()) {
          if (directoryConsumer != null) {
            directories.add(path);
          } else {
            // Scan directory for JARs and add them directly
            collectJarsFromDirectory(path, jars);
          }
        } else if (isJarFile(file)) {
          jars.add(path);
        }
      }

      // Notify consumers
      if (!jars.isEmpty() && jarConsumer != null) {
        jarConsumer.accept(jars);
      }
      if (!directories.isEmpty() && directoryConsumer != null) {
        directoryConsumer.accept(directories);
      }

      return !jars.isEmpty() || !directories.isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isJarFile(File file) {
    return file.isFile() && file.getName().toLowerCase().endsWith(".jar");
  }

  private void collectJarsFromDirectory(Path directory, List<Path> jars) {
    try {
      Files.walk(directory)
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
          .forEach(jars::add);
    } catch (Exception e) {
      // Ignore directories that can't be read
    }
  }
}
