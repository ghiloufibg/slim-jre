package io.github.ghiloufibg.example.enterprise;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enterprise Spring Boot Application for slim-jre accuracy testing.
 *
 * <p>Features tested: - REST API (java.net.http patterns) - JPA with H2 (java.sql) - Transactions
 * (@Transactional) - Validation (Bean Validation) - Logging (SLF4J → java.logging bridge) -
 * Actuator health endpoints (java.management for JMX)
 *
 * <p>Expected JDK modules (ground truth prediction): - java.base (always required) - java.sql (JDBC
 * for H2 database) - java.naming (JNDI for datasource lookup) - java.logging (JUL bridge for SLF4J)
 * - java.management (JMX for actuator metrics) - java.xml (XML configuration processing) -
 * java.instrument (Spring AOP proxies) - java.desktop (AWT classes pulled by some dependencies) -
 * java.compiler (annotation processing) - java.transaction.xa (JTA transaction support) -
 * jdk.unsupported (sun.misc.Unsafe for performance optimizations)
 */
@SpringBootApplication
public class SpringBootEnterpriseApp {

  private static final Logger log = LoggerFactory.getLogger(SpringBootEnterpriseApp.class);

  public static void main(String[] args) {
    log.info("Starting Spring Boot Enterprise Application");
    SpringApplication.run(SpringBootEnterpriseApp.class, args);
  }
}

// === Entity ===

@Entity
@Table(name = "products")
class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Name is required")
  @Size(min = 2, max = 100)
  private String name;

  @Size(max = 500)
  private String description;

  private Double price;

  private Integer quantity;

  // Getters and setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }
}

// === Repository ===

@Repository
interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByNameContainingIgnoreCase(String name);
}

// === Service with Transactions ===

@Service
class ProductService {

  private static final Logger log = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository repository;

  ProductService(ProductRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Product> findAll() {
    log.debug("Finding all products");
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public Optional<Product> findById(Long id) {
    log.debug("Finding product by id: {}", id);
    return repository.findById(id);
  }

  @Transactional
  public Product create(Product product) {
    log.info("Creating new product: {}", product.getName());
    return repository.save(product);
  }

  @Transactional
  public Product update(Long id, Product product) {
    log.info("Updating product: {}", id);
    product.setId(id);
    return repository.save(product);
  }

  @Transactional
  public void delete(Long id) {
    log.info("Deleting product: {}", id);
    repository.deleteById(id);
  }

  @Transactional
  public void bulkUpdate(List<Product> products) {
    log.info("Bulk updating {} products", products.size());
    repository.saveAll(products);
  }
}

// === REST Controller ===

@RestController
@RequestMapping("/api/products")
class ProductController {

  private static final Logger log = LoggerFactory.getLogger(ProductController.class);

  private final ProductService service;

  ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping
  public List<Product> findAll() {
    log.info("GET /api/products");
    return service.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> findById(@PathVariable Long id) {
    log.info("GET /api/products/{}", id);
    return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
    log.info("POST /api/products");
    Product created = service.create(product);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Product> update(
      @PathVariable Long id, @Valid @RequestBody Product product) {
    log.info("PUT /api/products/{}", id);
    return ResponseEntity.ok(service.update(id, product));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    log.info("DELETE /api/products/{}", id);
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
