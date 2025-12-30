package com.ghiloufi.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP Client application demonstrating HTTPS support. This app requires jdk.crypto.ec for TLS/SSL
 * - tests crypto module detection.
 */
public class HttpClientApp {

  public static void main(String[] args) {
    System.out.println("=================================");
    System.out.println("   HTTP Client - Slim JRE Demo");
    System.out.println("=================================");
    System.out.println();

    // Display Java version
    System.out.println("Java Version: " + System.getProperty("java.version"));
    System.out.println("Java Home: " + System.getProperty("java.home"));
    System.out.println();

    // Default URL to fetch (HTTPS to test crypto)
    String url = args.length > 0 ? args[0] : "https://httpbin.org/get";

    System.out.println("Fetching: " + url);
    System.out.println();

    try {
      HttpClient client =
          HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_2)
              .connectTimeout(Duration.ofSeconds(10))
              .build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header("User-Agent", "SlimJRE-HttpClient/1.0")
              .GET()
              .build();

      System.out.println("Sending request...");
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      System.out.println("Response Status: " + response.statusCode());
      System.out.println("Response Headers:");
      response
          .headers()
          .map()
          .forEach(
              (key, values) -> {
                System.out.println("  " + key + ": " + String.join(", ", values));
              });
      System.out.println();
      System.out.println("Response Body (first 500 chars):");
      String body = response.body();
      System.out.println(body.length() > 500 ? body.substring(0, 500) + "..." : body);

      System.out.println();
      System.out.println("HTTPS request completed successfully!");
      System.out.println("(This proves jdk.crypto.ec is working)");

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
