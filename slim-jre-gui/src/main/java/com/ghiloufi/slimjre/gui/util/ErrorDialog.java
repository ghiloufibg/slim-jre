package com.ghiloufi.slimjre.gui.util;

import com.ghiloufi.slimjre.exception.SlimJreException;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.*;

/**
 * Enhanced error dialog with categorized messages and actionable suggestions.
 *
 * <p>Provides user-friendly error messages with:
 *
 * <ul>
 *   <li>Error category identification
 *   <li>Actionable recovery suggestions
 *   <li>Optional stack trace details
 * </ul>
 */
public final class ErrorDialog {

  private ErrorDialog() {}

  /**
   * Shows an error dialog for a SlimJreException.
   *
   * @param parent parent component for dialog positioning
   * @param e the exception to display
   */
  public static void show(Component parent, SlimJreException e) {
    String title = "Slim JRE Error";
    String message = formatMessage(e);
    showDialog(parent, title, message, e);
  }

  /**
   * Shows an error dialog for a general exception.
   *
   * @param parent parent component for dialog positioning
   * @param title dialog title
   * @param e the exception to display
   */
  public static void show(Component parent, String title, Exception e) {
    String message = formatGeneralMessage(e);
    showDialog(parent, title, message, e);
  }

  /**
   * Shows a simple error message dialog.
   *
   * @param parent parent component for dialog positioning
   * @param title dialog title
   * @param message error message
   */
  public static void showMessage(Component parent, String title, String message) {
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
  }

  private static String formatMessage(SlimJreException e) {
    String className = e.getClass().getSimpleName();
    String baseMessage = e.getMessage();

    return switch (className) {
      case "ConfigurationException" ->
              """
          Configuration Error

          %s

          Suggestions:
          • Check that the output directory is valid and writable
          • Verify that all specified JAR files exist
          • Ensure module names are valid Java module names
          """
              .formatted(baseMessage);

      case "JDepsException" ->
              """
          Dependency Analysis Error

          %s

          Suggestions:
          • Verify all JAR files are valid and not corrupted
          • Check that JAR files are accessible (not locked by another process)
          • Ensure you're using a JDK (not JRE) with jdeps available
          """
              .formatted(baseMessage);

      case "JLinkException" ->
              """
          JRE Creation Error

          %s

          Suggestions:
          • Check that the output directory is writable
          • Delete any existing output directory and try again
          • Verify you have sufficient disk space
          • Ensure you're using a JDK with jlink available
          """
              .formatted(baseMessage);

      default ->
              """
          Error

          %s

          If this problem persists, please check:
          • Your Java installation is complete
          • All input files are accessible
          • You have necessary permissions
          """
              .formatted(baseMessage);
    };
  }

  private static String formatGeneralMessage(Exception e) {
    String message = e.getMessage();
    if (message == null || message.isEmpty()) {
      message = e.getClass().getSimpleName();
    }

    return
        """
        An unexpected error occurred:

        %s

        If this problem persists, please report it with the error details.
        """
        .formatted(message);
  }

  private static void showDialog(Component parent, String title, String message, Exception e) {
    // Create main panel
    JPanel panel = new JPanel(new BorderLayout(10, 10));

    // Message area
    JTextArea messageArea = new JTextArea(message);
    messageArea.setEditable(false);
    messageArea.setBackground(UIManager.getColor("Panel.background"));
    messageArea.setFont(UIManager.getFont("Label.font"));
    messageArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    panel.add(messageArea, BorderLayout.CENTER);

    // Details button for stack trace
    JButton detailsButton = new JButton("Show Details");
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttonPanel.add(detailsButton);

    JTextArea detailsArea = new JTextArea();
    detailsArea.setEditable(false);
    detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    detailsArea.setText(sw.toString());

    JScrollPane detailsScroll = new JScrollPane(detailsArea);
    detailsScroll.setPreferredSize(new Dimension(500, 200));
    detailsScroll.setVisible(false);

    detailsButton.addActionListener(
        evt -> {
          detailsScroll.setVisible(!detailsScroll.isVisible());
          detailsButton.setText(detailsScroll.isVisible() ? "Hide Details" : "Show Details");

          // Resize dialog
          Window window = SwingUtilities.getWindowAncestor(panel);
          if (window != null) {
            window.pack();
          }
        });

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(buttonPanel, BorderLayout.NORTH);
    bottomPanel.add(detailsScroll, BorderLayout.CENTER);
    panel.add(bottomPanel, BorderLayout.SOUTH);

    JOptionPane.showMessageDialog(parent, panel, title, JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Shows a warning dialog.
   *
   * @param parent parent component
   * @param title dialog title
   * @param message warning message
   */
  public static void showWarning(Component parent, String title, String message) {
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
  }

  /**
   * Shows an info dialog.
   *
   * @param parent parent component
   * @param title dialog title
   * @param message info message
   */
  public static void showInfo(Component parent, String title, String message) {
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
  }
}
