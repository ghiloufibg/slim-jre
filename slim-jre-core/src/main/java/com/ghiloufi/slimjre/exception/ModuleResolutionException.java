package com.ghiloufi.slimjre.exception;

/** Exception thrown when a required module cannot be found. */
public class ModuleResolutionException extends SlimJreException {

  private final String moduleName;

  public ModuleResolutionException(String moduleName) {
    super("Module not found: " + moduleName);
    this.moduleName = moduleName;
  }

  public ModuleResolutionException(String moduleName, String message) {
    super(message);
    this.moduleName = moduleName;
  }

  public String getModuleName() {
    return moduleName;
  }
}
