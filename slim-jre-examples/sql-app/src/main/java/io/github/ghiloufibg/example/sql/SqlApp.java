package io.github.ghiloufibg.example.sql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Tests java.sql module detection with various JDBC patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.sql - DriverManager, Connection, Statement, ResultSet, DataSource
 *   <li>java.logging - Logger for error handling
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect java.sql.*, javax.sql.* imports
 *   <li>jdeps: MUST detect all static imports
 *   <li>ReflectionBytecodeScanner: Should NOT detect (no reflection)
 * </ul>
 */
public class SqlApp {

  private static final Logger log = Logger.getLogger(SqlApp.class.getName());

  public static void main(String[] args) {
    System.out.println("=== SQL/JDBC Pattern Test ===\n");

    // Test 1: DriverManager (requires java.sql)
    testDriverManager();

    // Test 2: Connection types (requires java.sql)
    demonstrateConnectionPatterns();

    // Test 3: Statement types (requires java.sql)
    demonstrateStatementPatterns();

    // Test 4: DataSource pattern (requires java.sql - javax.sql.DataSource)
    demonstrateDataSourcePattern();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testDriverManager() {
    System.out.println("--- DriverManager Pattern ---");
    try {
      // This will fail without a driver, but the bytecode reference matters
      String url = "jdbc:h2:mem:testdb";
      Connection conn = DriverManager.getConnection(url);
      System.out.println("[OK] DriverManager.getConnection succeeded");
      conn.close();
    } catch (SQLException e) {
      // Expected without H2 driver
      System.out.println("[INFO] DriverManager test (expected without driver): " + e.getMessage());
      log.log(Level.FINE, "Expected SQLException", e);
    }
  }

  private static void demonstrateConnectionPatterns() {
    System.out.println("\n--- Connection Patterns ---");

    // Type references that require java.sql
    Connection conn = null;
    DatabaseMetaData metadata = null;

    System.out.println("[INFO] Connection type reference: " + Connection.class.getName());
    System.out.println("[INFO] DatabaseMetaData type: " + DatabaseMetaData.class.getName());
  }

  private static void demonstrateStatementPatterns() {
    System.out.println("\n--- Statement Patterns ---");

    // Type references that require java.sql
    Statement stmt = null;
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    System.out.println("[INFO] Statement type: " + Statement.class.getName());
    System.out.println("[INFO] PreparedStatement type: " + PreparedStatement.class.getName());
    System.out.println("[INFO] ResultSet type: " + ResultSet.class.getName());
  }

  private static void demonstrateDataSourcePattern() {
    System.out.println("\n--- DataSource Pattern (javax.sql) ---");

    // Type reference that requires java.sql (javax.sql is in java.sql module)
    DataSource ds = null;

    System.out.println("[INFO] DataSource type: " + DataSource.class.getName());
  }
}
