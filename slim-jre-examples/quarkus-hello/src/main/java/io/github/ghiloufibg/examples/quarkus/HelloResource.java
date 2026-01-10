package io.github.ghiloufibg.examples.quarkus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal Quarkus REST resource for slim-jre testing.
 *
 * <p>Run with: java -jar target/quarkus-app/quarkus-run.jar
 *
 * <p>Test with: curl http://localhost:8080/hello
 */
@Path("/hello")
public class HelloResource {

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String hello() {
    return "Hello from Quarkus!";
  }
}
