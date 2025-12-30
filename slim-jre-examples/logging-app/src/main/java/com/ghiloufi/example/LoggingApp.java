package com.ghiloufi.example;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Logging application demonstrating java.util.logging. Tests java.logging module detection. */
public class LoggingApp {

  private static final Logger LOGGER = Logger.getLogger(LoggingApp.class.getName());

  public static void main(String[] args) {
    // Configure console handler
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(Level.ALL);
    handler.setFormatter(new SimpleFormatter());

    Logger rootLogger = Logger.getLogger("");
    rootLogger.addHandler(handler);
    rootLogger.setLevel(Level.ALL);

    System.out.println("=================================");
    System.out.println("   Logging App - Slim JRE Demo");
    System.out.println("=================================");
    System.out.println();

    // Display Java version
    LOGGER.info("Java Version: " + System.getProperty("java.version"));
    LOGGER.info("Java Home: " + System.getProperty("java.home"));

    // Demonstrate different log levels
    LOGGER.finest("This is FINEST level (most detailed)");
    LOGGER.finer("This is FINER level");
    LOGGER.fine("This is FINE level");
    LOGGER.config("This is CONFIG level");
    LOGGER.info("This is INFO level");
    LOGGER.warning("This is WARNING level");

    // Simulate some application logic with logging
    LOGGER.info("Starting application logic...");

    try {
      performTask("Task 1");
      performTask("Task 2");
      performTask("Task 3");

      // Simulate an expected condition
      if (args.length == 0) {
        LOGGER.warning("No command line arguments provided");
      } else {
        LOGGER.info("Received " + args.length + " argument(s)");
        for (int i = 0; i < args.length; i++) {
          LOGGER.fine("  Arg[" + i + "]: " + args[i]);
        }
      }

      LOGGER.info("All tasks completed successfully!");

    } catch (Exception e) {
      LOGGER.severe("Application error: " + e.getMessage());
    }

    System.out.println();
    System.out.println("Logging demonstration completed!");
    System.out.println("(This proves java.logging module is working)");
  }

  private static void performTask(String taskName) {
    LOGGER.entering(LoggingApp.class.getName(), "performTask", taskName);
    LOGGER.info("Executing: " + taskName);

    // Simulate work
    long result = 0;
    for (int i = 0; i < 1000; i++) {
      result += i;
    }

    LOGGER.fine("Task " + taskName + " computed result: " + result);
    LOGGER.exiting(LoggingApp.class.getName(), "performTask");
  }
}
