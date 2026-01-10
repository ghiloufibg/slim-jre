package io.github.ghiloufibg.slimjre.gui;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;

/**
 * Main entry point for the Slim JRE GUI application.
 *
 * <p>Provides a graphical interface for creating minimal custom JREs, similar to Lombok's delombok
 * GUI. Allows developers to experiment with JRE minimization without CLI knowledge.
 */
public final class SlimJreGui {

  private SlimJreGui() {}

  /** Launches the Slim JRE GUI application. */
  public static void main(String[] args) {
    // Set up FlatLaf Look and Feel
    try {
      FlatLightLaf.setup();
    } catch (Exception e) {
      // Fallback to system L&F
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
        // Use default if all else fails
      }
    }

    // Configure UI defaults
    UIManager.put("Button.arc", 8);
    UIManager.put("Component.arc", 8);
    UIManager.put("TextComponent.arc", 8);

    // Launch on EDT
    SwingUtilities.invokeLater(
        () -> {
          MainFrame frame = new MainFrame();
          frame.setVisible(true);
        });
  }
}
