package com.ghiloufi.example.microprofile;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MicroProfile REST resource demonstrating JAX-RS and Config.
 */
@Path("/hello")
@RequestScoped
public class HelloResource {

    @Inject
    @ConfigProperty(name = "app.greeting", defaultValue = "Hello from MicroProfile!")
    private String greeting;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return greeting;
    }

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public String info() {
        return """
            {
                "application": "MicroProfile Hello World",
                "runtime": "Helidon 4.x",
                "purpose": "slim-jre test app",
                "javaVersion": "%s"
            }
            """.formatted(System.getProperty("java.version"));
    }
}
