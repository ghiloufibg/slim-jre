package com.ghiloufi.example.xml;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tests java.xml module detection with DOM, SAX, and XPath patterns.
 *
 * <p>EXPECTED MODULES:
 *
 * <ul>
 *   <li>java.base - always included
 *   <li>java.xml - DocumentBuilderFactory, SAXParserFactory, XPathFactory, TransformerFactory,
 *       Document, Element, NodeList, DefaultHandler, etc.
 * </ul>
 *
 * <p>SCANNER EXPECTATIONS:
 *
 * <ul>
 *   <li>ApiUsageScanner: MUST detect javax.xml.*, org.xml.sax.*, org.w3c.dom.*
 *   <li>jdeps: MUST detect all static imports
 *   <li>ReflectionBytecodeScanner: May detect factory patterns via ServiceLoader
 * </ul>
 */
public class XmlApp {

  private static final String SAMPLE_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <root>
          <item id="1">First Item</item>
          <item id="2">Second Item</item>
          <item id="3">Third Item</item>
      </root>
      """;

  public static void main(String[] args) {
    System.out.println("=== XML Processing Pattern Test ===\n");

    // Test 1: DOM Parsing
    testDomParsing();

    // Test 2: SAX Parsing
    testSaxParsing();

    // Test 3: XPath
    testXPath();

    // Test 4: Transformation
    testTransformation();

    System.out.println("\n=== Test Complete ===");
  }

  private static void testDomParsing() {
    System.out.println("--- DOM Parsing Pattern ---");
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(SAMPLE_XML)));

      NodeList items = doc.getElementsByTagName("item");
      System.out.println("[OK] DOM parsed " + items.getLength() + " items");

      for (int i = 0; i < items.getLength(); i++) {
        Node node = items.item(i);
        if (node instanceof Element element) {
          System.out.println(
              "  - Item " + element.getAttribute("id") + ": " + element.getTextContent());
        }
      }
    } catch (Exception e) {
      System.out.println("[ERROR] DOM parsing failed: " + e.getMessage());
    }
  }

  private static void testSaxParsing() {
    System.out.println("\n--- SAX Parsing Pattern ---");
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser parser = factory.newSAXParser();

      DefaultHandler handler =
          new DefaultHandler() {
            @Override
            public void startElement(
                String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
              if ("item".equals(qName)) {
                System.out.println("  - SAX found item with id: " + attributes.getValue("id"));
              }
            }
          };

      parser.parse(new InputSource(new StringReader(SAMPLE_XML)), handler);
      System.out.println("[OK] SAX parsing completed");
    } catch (Exception e) {
      System.out.println("[ERROR] SAX parsing failed: " + e.getMessage());
    }
  }

  private static void testXPath() {
    System.out.println("\n--- XPath Pattern ---");
    try {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = docFactory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(SAMPLE_XML)));

      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPath xpath = xpathFactory.newXPath();

      String result = xpath.evaluate("//item[@id='2']/text()", doc);
      System.out.println("[OK] XPath result for id=2: " + result);
    } catch (Exception e) {
      System.out.println("[ERROR] XPath failed: " + e.getMessage());
    }
  }

  private static void testTransformation() {
    System.out.println("\n--- Transformation Pattern ---");
    try {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = docFactory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(SAMPLE_XML)));

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();

      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(new java.io.StringWriter());
      transformer.transform(source, result);

      System.out.println(
          "[OK] Transformation completed (output length: "
              + result.getWriter().toString().length()
              + " chars)");
    } catch (Exception e) {
      System.out.println("[ERROR] Transformation failed: " + e.getMessage());
    }
  }
}
