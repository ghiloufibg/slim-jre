package io.github.ghiloufibg.example.naming;

import java.util.Hashtable;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

/**
 * Tests java.naming module detection with JNDI patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.naming - Context, InitialContext, DirContext, LdapContext, Attributes,
 *       NamingEnumeration, etc.
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect javax.naming.* usage
 *   <li>jdeps: MUST detect javax.naming.* imports
 *   <li>ReflectionBytecodeScanner: May detect JNDI lookup patterns
 * </ul>
 */
public class NamingApp {

  public static void main(String[] args) {
    System.out.println("=== JNDI Naming Pattern Test ===\n");

    // Test 1: Basic Context
    testBasicContext();

    // Test 2: Directory Context
    testDirectoryContext();

    // Test 3: LDAP Context
    testLdapContext();

    // Test 4: Attributes
    testAttributes();

    // Test 5: Naming enumeration
    testNamingEnumeration();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testBasicContext() {
    System.out.println("--- Basic Context Pattern ---");

    try {
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");

      Context ctx = new InitialContext(env);
      System.out.println("[OK] InitialContext created");

      // Type references
      System.out.println("[INFO] Context type: " + Context.class.getName());
      System.out.println("[INFO] InitialContext type: " + InitialContext.class.getName());

      ctx.close();
    } catch (NamingException e) {
      System.out.println(
          "[INFO] NamingException (expected without JNDI provider): " + e.getMessage());
      System.out.println("[INFO] NamingException type: " + NamingException.class.getName());
    }
  }

  private static void testDirectoryContext() {
    System.out.println("\n--- Directory Context Pattern ---");

    try {
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, "ldap://localhost:389");

      DirContext dirCtx = new InitialDirContext(env);
      System.out.println("[OK] InitialDirContext created");

      dirCtx.close();
    } catch (NamingException e) {
      System.out.println("[INFO] DirContext test (expected without LDAP): " + e.getMessage());
    }

    // Type references
    System.out.println("[INFO] DirContext type: " + DirContext.class.getName());
    System.out.println("[INFO] InitialDirContext type: " + InitialDirContext.class.getName());
  }

  private static void testLdapContext() {
    System.out.println("\n--- LDAP Context Pattern ---");

    try {
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, "ldap://localhost:389");

      LdapContext ldapCtx = new InitialLdapContext(env, null);
      System.out.println("[OK] InitialLdapContext created");

      ldapCtx.close();
    } catch (NamingException e) {
      System.out.println("[INFO] LdapContext test (expected without LDAP): " + e.getMessage());
    }

    // Type references
    System.out.println("[INFO] LdapContext type: " + LdapContext.class.getName());
    System.out.println("[INFO] InitialLdapContext type: " + InitialLdapContext.class.getName());
  }

  private static void testAttributes() {
    System.out.println("\n--- Attributes Pattern ---");

    // Create attributes
    Attributes attrs = new BasicAttributes(true);
    Attribute attr = new BasicAttribute("cn", "Test User");
    attrs.put(attr);

    System.out.println("[OK] BasicAttributes created with " + attrs.size() + " attribute(s)");
    System.out.println("[INFO] Attributes type: " + Attributes.class.getName());
    System.out.println("[INFO] Attribute type: " + Attribute.class.getName());
    System.out.println("[INFO] BasicAttributes type: " + BasicAttributes.class.getName());
    System.out.println("[INFO] BasicAttribute type: " + BasicAttribute.class.getName());
  }

  private static void testNamingEnumeration() {
    System.out.println("\n--- Naming Enumeration Pattern ---");

    // Type references
    System.out.println("[INFO] NamingEnumeration type: " + NamingEnumeration.class.getName());
    System.out.println("[INFO] NameClassPair type: " + NameClassPair.class.getName());
    System.out.println("[INFO] Binding type: " + Binding.class.getName());
    System.out.println("[INFO] Name type: " + Name.class.getName());
    System.out.println("[INFO] NameParser type: " + NameParser.class.getName());
  }
}
