package com.ghiloufi.slimjre.gui.components;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * Panel containing action buttons and progress indicator.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Analyze button for module detection
 *   <li>Create JRE button for JRE generation
 *   <li>Progress bar with status message
 * </ul>
 */
public class ActionPanel extends JPanel {

  private final JButton analyzeButton;
  private final JButton createJreButton;
  private final JProgressBar progressBar;
  private final JLabel statusLabel;

  /** Creates a new action panel with buttons and progress bar. */
  public ActionPanel() {
    setLayout(new BorderLayout(10, 5));
    setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));

    // Buttons panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));

    analyzeButton = new JButton("Analyze");
    analyzeButton.setPreferredSize(new Dimension(120, 32));
    analyzeButton.setFont(analyzeButton.getFont().deriveFont(Font.BOLD));
    analyzeButton.setToolTipText("Analyze JARs to detect required modules (Ctrl+A)");
    buttonPanel.add(analyzeButton);

    createJreButton = new JButton("Create JRE");
    createJreButton.setPreferredSize(new Dimension(120, 32));
    createJreButton.setFont(createJreButton.getFont().deriveFont(Font.BOLD));
    createJreButton.setToolTipText("Create a minimal JRE with detected modules (Ctrl+Enter)");
    buttonPanel.add(createJreButton);

    add(buttonPanel, BorderLayout.NORTH);

    // Progress panel
    JPanel progressPanel = new JPanel(new BorderLayout(10, 0));
    progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(false);
    progressBar.setPreferredSize(new Dimension(0, 20));
    progressPanel.add(progressBar, BorderLayout.CENTER);

    statusLabel = new JLabel(" ");
    statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
    statusLabel.setPreferredSize(new Dimension(300, 20));
    progressPanel.add(statusLabel, BorderLayout.EAST);

    add(progressPanel, BorderLayout.CENTER);

    // Register keyboard shortcuts
    registerKeyboardShortcuts();
  }

  private void registerKeyboardShortcuts() {
    // Ctrl+A for Analyze
    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("control A"), "analyze");
    getActionMap()
        .put(
            "analyze",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                if (analyzeButton.isEnabled()) {
                  analyzeButton.doClick();
                }
              }
            });

    // Ctrl+Enter for Create JRE
    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("control ENTER"), "createJre");
    getActionMap()
        .put(
            "createJre",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                if (createJreButton.isEnabled()) {
                  createJreButton.doClick();
                }
              }
            });
  }

  /**
   * Sets the action listener for the Analyze button.
   *
   * @param listener the action listener
   */
  public void setAnalyzeAction(ActionListener listener) {
    // Remove existing listeners
    for (ActionListener al : analyzeButton.getActionListeners()) {
      analyzeButton.removeActionListener(al);
    }
    analyzeButton.addActionListener(listener);
  }

  /**
   * Sets the action listener for the Create JRE button.
   *
   * @param listener the action listener
   */
  public void setCreateJreAction(ActionListener listener) {
    // Remove existing listeners
    for (ActionListener al : createJreButton.getActionListeners()) {
      createJreButton.removeActionListener(al);
    }
    createJreButton.addActionListener(listener);
  }

  /**
   * Enables or disables the Analyze button.
   *
   * @param enabled true to enable
   */
  public void setAnalyzeEnabled(boolean enabled) {
    analyzeButton.setEnabled(enabled);
  }

  /**
   * Enables or disables the Create JRE button.
   *
   * @param enabled true to enable
   */
  public void setCreateJreEnabled(boolean enabled) {
    createJreButton.setEnabled(enabled);
  }

  /**
   * Updates the progress bar and status message.
   *
   * @param percent progress percentage (0-100)
   * @param message status message to display
   */
  public void setProgress(int percent, String message) {
    progressBar.setValue(percent);
    progressBar.setStringPainted(percent > 0);
    if (percent > 0 && percent < 100) {
      progressBar.setString(percent + "%");
    } else {
      progressBar.setString(null);
    }
    statusLabel.setText(message);
  }

  /**
   * Sets the progress bar to indeterminate mode.
   *
   * @param indeterminate true for indeterminate mode
   */
  public void setIndeterminate(boolean indeterminate) {
    progressBar.setIndeterminate(indeterminate);
    if (indeterminate) {
      progressBar.setStringPainted(false);
    }
  }

  /** Resets the progress bar and status. */
  public void reset() {
    progressBar.setValue(0);
    progressBar.setIndeterminate(false);
    progressBar.setStringPainted(false);
    statusLabel.setText(" ");
  }
}
