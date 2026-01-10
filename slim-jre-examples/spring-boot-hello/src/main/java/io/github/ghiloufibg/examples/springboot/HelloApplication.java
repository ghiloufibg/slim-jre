package io.github.ghiloufibg.examples.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Spring Boot application for slim-jre testing.
 *
 * <p>Run with: java -jar target/spring-boot-hello-1.0.0-alpha.1.jar
 *
 * <p>Test with: curl http://localhost:8080/hello
 */
@SpringBootApplication
@RestController
public class HelloApplication {

  public static void main(String[] args) {
    SpringApplication.run(HelloApplication.class, args);
  }

  @GetMapping("/hello")
  public String hello() {
    return "Hello from Spring Boot!";
  }

  @GetMapping("/")
  public String root() {
    return "Spring Boot Hello World - slim-jre test app";
  }
}
