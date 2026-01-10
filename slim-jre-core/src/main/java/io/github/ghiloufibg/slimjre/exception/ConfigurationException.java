package io.github.ghiloufibg.slimjre.exception;

/** Exception thrown when configuration is invalid. */
public class ConfigurationException extends SlimJreException {

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
