package com.ghiloufi.example.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Tests java.desktop module detection with AWT/Swing patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.desktop - AWT (Color, Graphics, Image, Toolkit), Swing (JFrame, JButton, JLabel,
 *       JPanel), ImageIO, PropertyChangeListener (java.beans)
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect java.awt.*, javax.swing.*, javax.imageio.*, java.beans.*
 *   <li>jdeps: MUST detect all static imports
 * </ul>
 *
 * <p>NOTE: This app may not run in headless environments, but the bytecode references are what
 * we're testing for detection.
 */
public class DesktopApp {

  public static void main(String[] args) {
    System.out.println("=== Desktop (AWT/Swing) Pattern Test ===\n");

    // Test 1: AWT types
    testAwtTypes();

    // Test 2: Swing types
    testSwingTypes();

    // Test 3: ImageIO
    testImageIO();

    // Test 4: JavaBeans
    testJavaBeans();

    // Test 5: Create GUI (if not headless)
    testGuiCreation();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testAwtTypes() {
    System.out.println("--- AWT Types Pattern ---");

    // Color and Graphics
    Color color = Color.RED;
    System.out.println("[OK] Color type: " + Color.class.getName());

    // Graphics types
    System.out.println("[INFO] Graphics type: " + Graphics.class.getName());
    System.out.println("[INFO] Graphics2D type: " + Graphics2D.class.getName());

    // Image and Toolkit
    System.out.println("[INFO] Image type: " + Image.class.getName());
    System.out.println("[INFO] Toolkit type: " + Toolkit.class.getName());
    System.out.println("[INFO] BufferedImage type: " + BufferedImage.class.getName());

    // Layout and containers
    System.out.println("[INFO] BorderLayout type: " + BorderLayout.class.getName());
    System.out.println("[INFO] Container type: " + Container.class.getName());
    System.out.println("[INFO] Component type: " + Component.class.getName());
    System.out.println("[INFO] Dimension type: " + Dimension.class.getName());
    System.out.println("[INFO] Font type: " + Font.class.getName());
  }

  private static void testSwingTypes() {
    System.out.println("\n--- Swing Types Pattern ---");

    // Swing components
    System.out.println("[INFO] JFrame type: " + JFrame.class.getName());
    System.out.println("[INFO] JPanel type: " + JPanel.class.getName());
    System.out.println("[INFO] JButton type: " + JButton.class.getName());
    System.out.println("[INFO] JLabel type: " + JLabel.class.getName());
    System.out.println("[INFO] SwingUtilities type: " + SwingUtilities.class.getName());

    // Action listener
    System.out.println("[INFO] ActionListener type: " + ActionListener.class.getName());
    System.out.println("[INFO] ActionEvent type: " + ActionEvent.class.getName());
  }

  private static void testImageIO() {
    System.out.println("\n--- ImageIO Pattern ---");

    // ImageIO is in java.desktop module
    String[] formats = ImageIO.getReaderFormatNames();
    System.out.println("[OK] ImageIO reader formats: " + formats.length);
    System.out.println("[INFO] ImageIO type: " + ImageIO.class.getName());
  }

  private static void testJavaBeans() {
    System.out.println("\n--- JavaBeans Pattern ---");

    // PropertyChangeListener is in java.desktop (java.beans package)
    System.out.println(
        "[INFO] PropertyChangeListener type: " + PropertyChangeListener.class.getName());
    System.out.println("[INFO] PropertyChangeEvent type: " + PropertyChangeEvent.class.getName());
  }

  private static void testGuiCreation() {
    System.out.println("\n--- GUI Creation Pattern ---");

    // Check if headless
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      System.out.println("[INFO] Running in headless mode - skipping GUI creation");
      return;
    }

    try {
      SwingUtilities.invokeAndWait(
          () -> {
            JFrame frame = new JFrame("Desktop App Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Slim JRE Desktop Test"), BorderLayout.NORTH);

            JButton button = new JButton("Click Me");
            button.addActionListener(e -> System.out.println("Button clicked!"));
            panel.add(button, BorderLayout.CENTER);

            frame.add(panel);
            System.out.println("[OK] GUI created successfully");

            // Don't actually show it in test mode
            // frame.setVisible(true);
            frame.dispose();
          });
    } catch (Exception e) {
      System.out.println("[INFO] GUI creation test: " + e.getMessage());
    }
  }
}
