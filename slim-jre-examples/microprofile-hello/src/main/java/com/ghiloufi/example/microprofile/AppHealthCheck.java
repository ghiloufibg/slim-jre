package com.ghiloufi.example.microprofile;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/** MicroProfile Health check demonstrating health endpoints. */
@Liveness
@ApplicationScoped
public class AppHealthCheck implements HealthCheck {

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("microprofile-hello")
        .up()
        .withData("javaVersion", System.getProperty("java.version"))
        .build();
  }
}
