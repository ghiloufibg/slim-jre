package com.ghiloufi.examples.quarkus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Root endpoint for the Quarkus hello world app. */
@Path("/")
public class RootResource {

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String root() {
    return "Quarkus Hello World - slim-jre test app";
  }
}
