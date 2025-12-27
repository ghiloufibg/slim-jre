package com.ghiloufi.slimjre.exception;

/** Exception thrown when jlink execution fails. */
public class JLinkException extends SlimJreException {

  public JLinkException(String message) {
    super(message);
  }

  public JLinkException(String message, Throwable cause) {
    super(message, cause);
  }
}
