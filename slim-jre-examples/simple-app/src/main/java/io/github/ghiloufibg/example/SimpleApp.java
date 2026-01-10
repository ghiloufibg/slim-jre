package io.github.ghiloufibg.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A simple Java application to demonstrate Slim JRE creation. This app uses only java.base module
 * features.
 */
public class SimpleApp {

  public static void main(String[] args) {
    System.out.println("=================================");
    System.out.println("   Simple App - Slim JRE Demo");
    System.out.println("=================================");
    System.out.println();

    // Display Java version
    System.out.println("Java Version: " + System.getProperty("java.version"));
    System.out.println("Java Runtime: " + System.getProperty("java.runtime.name"));
    System.out.println("Java Home: " + System.getProperty("java.home"));
    System.out.println();

    // Display current time
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    System.out.println("Current Time: " + now.format(formatter));
    System.out.println();

    // Simple calculation
    long sum = 0;
    for (int i = 1; i <= 100; i++) {
      sum += i;
    }
    System.out.println("Sum of 1-100: " + sum);
    System.out.println();

    // Process command line arguments
    if (args.length > 0) {
      System.out.println("Arguments received:");
      for (int i = 0; i < args.length; i++) {
        System.out.println("  [" + i + "] " + args[i]);
      }
    } else {
      System.out.println("No command line arguments.");
    }

    System.out.println();
    System.out.println("Application completed successfully!");
  }
}
