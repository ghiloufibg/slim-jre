package com.ghiloufi.slimjre.gui.components;

import com.ghiloufi.slimjre.config.AnalysisResult;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/**
 * Pie chart visualization for module source breakdown.
 *
 * <p>Displays the distribution of detected modules by their detection source (jdeps, service
 * loaders, reflection, API usage, GraalVM metadata).
 */
public class ModuleSourceChart extends JPanel {

  private static final Map<String, Color> SOURCE_COLORS = new LinkedHashMap<>();

  static {
    SOURCE_COLORS.put("jdeps", new Color(66, 133, 244)); // Google Blue
    SOURCE_COLORS.put("services", new Color(52, 168, 83)); // Google Green
    SOURCE_COLORS.put("reflection", new Color(251, 188, 4)); // Google Yellow
    SOURCE_COLORS.put("api_usage", new Color(234, 67, 53)); // Google Red
    SOURCE_COLORS.put("graalvm", new Color(154, 160, 166)); // Gray
  }

  private Map<String, Integer> data = new LinkedHashMap<>();
  private String title = "Module Sources";

  /** Creates a new module source chart with default settings. */
  public ModuleSourceChart() {
    setPreferredSize(new Dimension(300, 280));
    setBackground(UIManager.getColor("Panel.background"));
  }

  /**
   * Updates the chart with analysis results.
   *
   * @param result the analysis result to display
   */
  public void setData(AnalysisResult result) {
    this.data = new LinkedHashMap<>();
    // Only add non-zero values
    if (!result.requiredModules().isEmpty()) {
      data.put("jdeps", result.requiredModules().size());
    }
    if (!result.serviceLoaderModules().isEmpty()) {
      data.put("services", result.serviceLoaderModules().size());
    }
    if (!result.reflectionModules().isEmpty()) {
      data.put("reflection", result.reflectionModules().size());
    }
    if (!result.apiUsageModules().isEmpty()) {
      data.put("api_usage", result.apiUsageModules().size());
    }
    if (!result.graalVmMetadataModules().isEmpty()) {
      data.put("graalvm", result.graalVmMetadataModules().size());
    }
    repaint();
  }

  /** Clears the chart data. */
  public void clear() {
    this.data.clear();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      int width = getWidth();
      int height = getHeight();

      // Draw title
      g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
      FontMetrics titleFm = g2.getFontMetrics();
      int titleWidth = titleFm.stringWidth(title);
      g2.setColor(UIManager.getColor("Label.foreground"));
      g2.drawString(title, (width - titleWidth) / 2, 20);

      int total = data.values().stream().mapToInt(Integer::intValue).sum();

      if (total == 0) {
        // Draw empty state message
        g2.setFont(g2.getFont().deriveFont(Font.ITALIC, 12f));
        String emptyMsg = "No analysis data available";
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.GRAY);
        g2.drawString(emptyMsg, (width - fm.stringWidth(emptyMsg)) / 2, height / 2);
        return;
      }

      // Calculate pie chart dimensions
      int chartPadding = 20;
      int legendHeight = data.size() * 22 + 10;
      int availableHeight = height - 30 - legendHeight; // 30 for title area
      int diameter = Math.min(width - chartPadding * 2, availableHeight - chartPadding);
      diameter = Math.max(diameter, 80); // Minimum size

      int chartX = (width - diameter) / 2;
      int chartY = 35;

      // Draw pie slices
      double startAngle = 90; // Start from top
      for (Map.Entry<String, Integer> entry : data.entrySet()) {
        if (entry.getValue() == 0) continue;

        double arcAngle = (entry.getValue() * 360.0) / total;
        Color color = SOURCE_COLORS.getOrDefault(entry.getKey(), Color.GRAY);

        // Draw filled arc
        g2.setColor(color);
        Arc2D arc =
            new Arc2D.Double(chartX, chartY, diameter, diameter, startAngle, -arcAngle, Arc2D.PIE);
        g2.fill(arc);

        // Draw border
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.draw(arc);

        startAngle -= arcAngle;
      }

      // Draw total in center
      g2.setColor(UIManager.getColor("Label.foreground"));
      g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
      String totalStr = String.valueOf(total);
      FontMetrics fm = g2.getFontMetrics();
      int textX = chartX + (diameter - fm.stringWidth(totalStr)) / 2;
      int textY = chartY + diameter / 2 + fm.getAscent() / 3;
      g2.drawString(totalStr, textX, textY);

      g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
      fm = g2.getFontMetrics();
      String modulesStr = "modules";
      textX = chartX + (diameter - fm.stringWidth(modulesStr)) / 2;
      g2.drawString(modulesStr, textX, textY + 14);

      // Draw legend
      int legendY = chartY + diameter + 20;
      int legendX = 20;
      int legendItemWidth = (width - 40) / 2;

      g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
      fm = g2.getFontMetrics();

      int itemIndex = 0;
      for (Map.Entry<String, Integer> entry : data.entrySet()) {
        if (entry.getValue() == 0) continue;

        int row = itemIndex / 2;
        int col = itemIndex % 2;
        int x = legendX + col * legendItemWidth;
        int y = legendY + row * 22;

        // Color box
        g2.setColor(SOURCE_COLORS.getOrDefault(entry.getKey(), Color.GRAY));
        g2.fillRect(x, y, 14, 14);
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(x, y, 14, 14);

        // Label with count
        String label = formatLabel(entry.getKey()) + ": " + entry.getValue();
        g2.setColor(UIManager.getColor("Label.foreground"));
        g2.drawString(label, x + 20, y + 11);

        itemIndex++;
      }
    } finally {
      g2.dispose();
    }
  }

  private String formatLabel(String key) {
    return switch (key) {
      case "jdeps" -> "jdeps (static)";
      case "services" -> "Service loaders";
      case "reflection" -> "Reflection";
      case "api_usage" -> "API usage";
      case "graalvm" -> "GraalVM metadata";
      default -> key;
    };
  }
}
