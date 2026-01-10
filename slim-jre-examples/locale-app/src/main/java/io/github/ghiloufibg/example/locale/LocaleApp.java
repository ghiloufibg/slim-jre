package io.github.ghiloufibg.example.locale;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;

/**
 * Demonstrates internationalization features that require jdk.localedata module.
 *
 * <p>This application uses multiple locale-aware APIs to test the LocaleModuleScanner detection:
 *
 * <ul>
 *   <li>Tier 1 (DEFINITE): Non-English locale constants (Locale.FRENCH, Locale.GERMAN, etc.)
 *   <li>Tier 2 (STRONG): DateTimeFormatter.ofLocalizedDate(), NumberFormat with locales
 *   <li>Tier 3 (POSSIBLE): Locale.getDefault(), MessageFormat
 * </ul>
 *
 * <p>Without jdk.localedata, this app will fall back to English formatting or fail.
 */
public class LocaleApp {

  public static void main(String[] args) {
    System.out.println("=== Locale Application Demo ===\n");

    // Show system default locale
    Locale defaultLocale = Locale.getDefault();
    System.out.println("System default locale: " + defaultLocale.getDisplayName());
    System.out.println();

    // Demonstrate with multiple locales
    demonstrateLocale(Locale.FRENCH);
    demonstrateLocale(Locale.GERMAN);
    demonstrateLocale(Locale.JAPANESE);
    demonstrateLocale(Locale.ITALIAN);
    demonstrateLocale(Locale.CHINESE);

    System.out.println("=== Demo Complete ===");
  }

  private static void demonstrateLocale(Locale locale) {
    System.out.println("--- " + locale.getDisplayName() + " (" + locale.toLanguageTag() + ") ---");

    // Date formatting (Tier 2: ofLocalizedDate)
    LocalDate today = LocalDate.now();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);
    try {
      String formattedDate = today.format(dateFormatter.withLocale(locale));
      System.out.println("  Date (FULL): " + formattedDate);
    } catch (Exception e) {
      System.out.println("  Date (FULL): [Error - " + e.getMessage() + "]");
    }

    // DateTime formatting (Tier 2: ofLocalizedDateTime)
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
    try {
      String formattedDateTime = now.format(dateTimeFormatter.withLocale(locale));
      System.out.println("  DateTime (MEDIUM): " + formattedDateTime);
    } catch (Exception e) {
      System.out.println("  DateTime (MEDIUM): [Error - " + e.getMessage() + "]");
    }

    // Number formatting (Tier 2: NumberFormat.getInstance)
    double amount = 1234567.89;
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    System.out.println("  Number: " + numberFormat.format(amount));

    // Currency formatting (Tier 2: NumberFormat.getCurrencyInstance)
    try {
      NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
      System.out.println("  Currency: " + currencyFormat.format(amount));
    } catch (Exception e) {
      System.out.println("  Currency: [Error - " + e.getMessage() + "]");
    }

    // Percent formatting
    NumberFormat percentFormat = NumberFormat.getPercentInstance(locale);
    System.out.println("  Percent: " + percentFormat.format(0.75));

    // MessageFormat (Tier 3: common locale API)
    String pattern = "On {0,date,long}, the temperature was {1,number,#.#} degrees.";
    MessageFormat messageFormat = new MessageFormat(pattern, locale);
    Object[] arguments = {new java.util.Date(), 23.5};
    System.out.println("  Message: " + messageFormat.format(arguments));

    // Currency info
    try {
      Currency currency = Currency.getInstance(locale);
      System.out.println(
          "  Currency code: "
              + currency.getCurrencyCode()
              + " ("
              + currency.getSymbol(locale)
              + ")");
    } catch (Exception e) {
      System.out.println("  Currency code: [Not available for this locale]");
    }

    System.out.println();
  }
}
