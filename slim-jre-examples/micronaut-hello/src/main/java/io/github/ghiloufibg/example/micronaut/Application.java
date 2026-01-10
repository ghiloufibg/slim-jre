package io.github.ghiloufibg.example.micronaut;

import io.micronaut.runtime.Micronaut;

/** Micronaut application entry point. Demonstrates Micronaut framework support with slim-jre. */
public class Application {
  public static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }
}
