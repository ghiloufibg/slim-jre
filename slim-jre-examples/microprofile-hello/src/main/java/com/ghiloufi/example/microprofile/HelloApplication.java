package com.ghiloufi.example.microprofile;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * MicroProfile JAX-RS Application configuration.
 */
@ApplicationPath("/api")
public class HelloApplication extends Application {
}
