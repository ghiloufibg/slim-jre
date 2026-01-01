package com.ghiloufi.slimjre.gui.components;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * Panel containing action buttons.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Analyze button for module detection
 *   <li>Create JRE button for JRE generation
 * </ul>
 */
public class ActionPanel extends JPanel {

  private final JButton analyzeButton;
  private final JButton createJreButton;

  /** Creates a new action panel with buttons. */
  public ActionPanel() {
    setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
    setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

    analyzeButton = new JButton("Analyze");
    analyzeButton.setPreferredSize(new Dimension(120, 32));
    analyzeButton.setFont(analyzeButton.getFont().deriveFont(Font.BOLD));
    analyzeButton.setToolTipText("Analyze JARs to detect required modules (Ctrl+A)");
    add(analyzeButton);

    createJreButton = new JButton("Create JRE");
    createJreButton.setPreferredSize(new Dimension(120, 32));
    createJreButton.setFont(createJreButton.getFont().deriveFont(Font.BOLD));
    createJreButton.setToolTipText("Create a minimal JRE with detected modules (Ctrl+Enter)");
    add(createJreButton);

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
   * Updates progress (no-op, kept for API compatibility).
   *
   * @param percent progress percentage (ignored)
   * @param message status message (ignored)
   */
  public void setProgress(int percent, String message) {
    // No-op: progress bar removed
  }

  /**
   * Sets indeterminate mode (no-op, kept for API compatibility).
   *
   * @param indeterminate ignored
   */
  public void setIndeterminate(boolean indeterminate) {
    // No-op: progress bar removed
  }

  /** Resets progress (no-op, kept for API compatibility). */
  public void reset() {
    // No-op: progress bar removed
  }
}
