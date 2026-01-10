package io.github.ghiloufibg.slimjre.config;

/**
 * Controls how SSL/TLS and cryptographic module requirements are handled.
 *
 * <p>The crypto detection addresses a fundamental limitation of jdeps: it cannot detect {@code
 * jdk.crypto.ec} because crypto providers use internal, non-exported packages. When applications
 * use SSL/TLS APIs like {@code SSLContext} or {@code HttpClient}, the actual crypto implementation
 * in {@code sun.security.ec.*} is loaded via service providers at runtime, which jdeps cannot
 * trace.
 */
public enum CryptoMode {
  /**
   * Automatically detect SSL/TLS usage and include crypto modules if needed.
   *
   * <p>Scans bytecode for patterns like {@code javax/net/ssl/*}, {@code java/net/http/*}, and
   * {@code javax/crypto/*}. If detected, adds {@code jdk.crypto.ec} module.
   */
  AUTO,

  /**
   * Always include crypto modules regardless of detection.
   *
   * <p>Use this for maximum safety when you know your application uses HTTPS/TLS but static
   * analysis might not detect it (e.g., reflection-based loading, runtime configuration).
   */
  ALWAYS,

  /**
   * Never include crypto modules, even if SSL/TLS usage is detected.
   *
   * <p><b>Warning:</b> Use only for HTTP-only applications. HTTPS/TLS connections will fail at
   * runtime if crypto modules are needed but not included.
   */
  NEVER
}
