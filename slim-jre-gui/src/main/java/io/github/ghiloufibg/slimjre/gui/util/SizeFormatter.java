package io.github.ghiloufibg.slimjre.gui.util;

/**
 * Utility class for formatting file sizes in human-readable format.
 *
 * <p>Formats byte sizes into appropriate units (B, KB, MB, GB) with proper precision.
 */
public final class SizeFormatter {

  private static final long KB = 1024L;
  private static final long MB = KB * 1024L;
  private static final long GB = MB * 1024L;

  private SizeFormatter() {}

  /**
   * Formats a byte size into a human-readable string.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>512 -> "512 B"
   *   <li>1536 -> "1.5 KB"
   *   <li>1572864 -> "1.5 MB"
   *   <li>1610612736 -> "1.5 GB"
   * </ul>
   *
   * @param bytes the size in bytes
   * @return formatted string with appropriate unit
   */
  public static String format(long bytes) {
    if (bytes < 0) {
      return "0 B";
    }

    if (bytes < KB) {
      return bytes + " B";
    } else if (bytes < MB) {
      double kb = (double) bytes / KB;
      return formatValue(kb) + " KB";
    } else if (bytes < GB) {
      double mb = (double) bytes / MB;
      return formatValue(mb) + " MB";
    } else {
      double gb = (double) bytes / GB;
      return formatValue(gb) + " GB";
    }
  }

  private static String formatValue(double value) {
    if (value < 10) {
      return String.format("%.2f", value);
    } else if (value < 100) {
      return String.format("%.1f", value);
    } else {
      return String.format("%.0f", value);
    }
  }

  /**
   * Formats a byte size into a compact human-readable string.
   *
   * <p>Similar to {@link #format(long)} but with less precision for compact display.
   *
   * @param bytes the size in bytes
   * @return compact formatted string with appropriate unit
   */
  public static String formatCompact(long bytes) {
    if (bytes < 0) {
      return "0B";
    }

    if (bytes < KB) {
      return bytes + "B";
    } else if (bytes < MB) {
      return String.format("%.0fKB", (double) bytes / KB);
    } else if (bytes < GB) {
      return String.format("%.1fMB", (double) bytes / MB);
    } else {
      return String.format("%.1fGB", (double) bytes / GB);
    }
  }
}
