package io.github.ghiloufibg.slimjre.exception;

/**
 * Base exception for all Slim JRE operations. This is an unchecked exception to avoid boilerplate
 * try-catch blocks.
 */
public class SlimJreException extends RuntimeException {

  public SlimJreException(String message) {
    super(message);
  }

  public SlimJreException(String message, Throwable cause) {
    super(message, cause);
  }
}
