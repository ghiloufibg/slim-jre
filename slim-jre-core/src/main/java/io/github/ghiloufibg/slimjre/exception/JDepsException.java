package io.github.ghiloufibg.slimjre.exception;

/** Exception thrown when jdeps analysis fails. */
public class JDepsException extends SlimJreException {

  public JDepsException(String message) {
    super(message);
  }

  public JDepsException(String message, Throwable cause) {
    super(message, cause);
  }
}
