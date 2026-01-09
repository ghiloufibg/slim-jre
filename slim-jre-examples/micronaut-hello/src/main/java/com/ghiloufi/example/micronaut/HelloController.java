package com.ghiloufi.example.micronaut;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

/** Simple REST controller for Micronaut hello world demo. */
@Controller
public class HelloController {

  @Get("/hello")
  @Produces(MediaType.TEXT_PLAIN)
  public String hello() {
    return "Hello from Micronaut!";
  }

  @Get("/")
  @Produces(MediaType.TEXT_PLAIN)
  public String index() {
    return "Micronaut Hello World - slim-jre test app";
  }
}
