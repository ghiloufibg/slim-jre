package com.ghiloufi.example.enterprise;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Enterprise Quarkus Application for slim-jre accuracy testing.
 *
 * <p>Features tested:
 * - REST API (Jakarta REST)
 * - Hibernate ORM with Panache (java.sql)
 * - H2 in-memory database
 * - Transactions (@Transactional)
 * - Validation (Hibernate Validator)
 * - Logging (JBoss Logging → java.logging)
 * - Health endpoints (SmallRye Health)
 * - Metrics (Micrometer)
 *
 * <p>Expected JDK modules (ground truth prediction):
 * - java.base (always required)
 * - java.sql (JDBC for H2 database)
 * - java.naming (JNDI for datasource lookup)
 * - java.logging (JUL for logging)
 * - java.management (JMX for metrics)
 * - java.xml (XML configuration)
 * - java.transaction.xa (JTA for @Transactional)
 * - java.compiler (annotation processing)
 * - java.desktop (may be pulled by dependencies)
 * - jdk.unsupported (sun.misc.Unsafe usage)
 */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class QuarkusEnterpriseApp {

  private static final Logger LOG = Logger.getLogger(QuarkusEnterpriseApp.class);

  @Inject OrderService orderService;

  @GET
  public List<Order> findAll() {
    LOG.info("GET /api/orders");
    return orderService.findAll();
  }

  @GET
  @Path("/{id}")
  public Response findById(@PathParam("id") Long id) {
    LOG.infov("GET /api/orders/{0}", id);
    Order order = orderService.findById(id);
    if (order == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(order).build();
  }

  @POST
  @Transactional
  public Response create(@Valid Order order) {
    LOG.info("POST /api/orders");
    Order created = orderService.create(order);
    return Response.status(Response.Status.CREATED).entity(created).build();
  }

  @PUT
  @Path("/{id}")
  @Transactional
  public Response update(@PathParam("id") Long id, @Valid Order order) {
    LOG.infov("PUT /api/orders/{0}", id);
    Order updated = orderService.update(id, order);
    if (updated == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(updated).build();
  }

  @DELETE
  @Path("/{id}")
  @Transactional
  public Response delete(@PathParam("id") Long id) {
    LOG.infov("DELETE /api/orders/{0}", id);
    boolean deleted = orderService.delete(id);
    if (!deleted) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.noContent().build();
  }
}

// === Entity using Panache ===

@Entity
@Table(name = "orders")
class Order extends PanacheEntity {

  @NotBlank(message = "Customer name is required")
  @Size(min = 2, max = 100)
  public String customerName;

  @Size(max = 200)
  public String shippingAddress;

  public Double totalAmount;

  public String status;

  public Order() {}

  public Order(String customerName, String shippingAddress, Double totalAmount, String status) {
    this.customerName = customerName;
    this.shippingAddress = shippingAddress;
    this.totalAmount = totalAmount;
    this.status = status;
  }
}

// === Repository ===

@ApplicationScoped
class OrderRepository implements PanacheRepository<Order> {

  public List<Order> findByCustomer(String customerName) {
    return find("customerName", customerName).list();
  }

  public List<Order> findByStatus(String status) {
    return find("status", status).list();
  }
}

// === Service with Transactions ===

@ApplicationScoped
class OrderService {

  private static final Logger LOG = Logger.getLogger(OrderService.class);

  @Inject OrderRepository repository;

  public List<Order> findAll() {
    LOG.debug("Finding all orders");
    return repository.listAll();
  }

  public Order findById(Long id) {
    LOG.debugv("Finding order by id: {0}", id);
    return repository.findById(id);
  }

  @Transactional
  public Order create(Order order) {
    LOG.infov("Creating new order for customer: {0}", order.customerName);
    order.status = "PENDING";
    repository.persist(order);
    return order;
  }

  @Transactional
  public Order update(Long id, Order order) {
    LOG.infov("Updating order: {0}", id);
    Order existing = repository.findById(id);
    if (existing == null) {
      return null;
    }
    existing.customerName = order.customerName;
    existing.shippingAddress = order.shippingAddress;
    existing.totalAmount = order.totalAmount;
    existing.status = order.status;
    return existing;
  }

  @Transactional
  public boolean delete(Long id) {
    LOG.infov("Deleting order: {0}", id);
    return repository.deleteById(id);
  }

  @Transactional
  public void processOrder(Long id) {
    LOG.infov("Processing order: {0}", id);
    Order order = repository.findById(id);
    if (order != null) {
      order.status = "PROCESSED";
    }
  }
}
