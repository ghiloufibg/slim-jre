package com.ghiloufi.slimjre.core;

/**
 * Confidence level for locale detection results.
 *
 * <p>Indicates how certain we are that the application requires the {@code jdk.localedata} module.
 */
public enum LocaleConfidence {
  /**
   * Definite need for locale data - explicit non-English locale constants detected. Example: {@code
   * Locale.FRENCH}, {@code Locale.GERMANY}
   */
  DEFINITE,

  /**
   * Strong indication of internationalization - locale-aware APIs detected but no explicit
   * non-English locale. Example: {@code DateTimeFormatter.ofLocalizedDate()}, {@code
   * ResourceBundle.getBundle()}
   */
  STRONG,

  /**
   * Possible need - common locale APIs that might work with English only. Example: {@code
   * Locale.getDefault()}, {@code MessageFormat}
   */
  POSSIBLE,

  /** No locale-related patterns detected. */
  NONE
}
