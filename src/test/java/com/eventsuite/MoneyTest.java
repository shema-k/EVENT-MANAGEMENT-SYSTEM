package com.eventsuite;

import com.eventsuite.finance.Money;
import java.math.BigDecimal;
import static com.eventsuite.TestRunner.assertClose;
import static com.eventsuite.TestRunner.assertEquals;
import static com.eventsuite.TestRunner.assertFalse;
import static com.eventsuite.TestRunner.assertThrows;
import static com.eventsuite.TestRunner.assertTrue;
import static com.eventsuite.TestRunner.suite;
import static com.eventsuite.TestRunner.test;

/** Money: the arithmetic the finance module rests on. */
final class MoneyTest {

    private MoneyTest() {
    }

    static void register() {
        suite("Money");

        test("null becomes zero rather than failing", () ->
                assertEquals("0.00", Money.of((BigDecimal) null).toPlainString(),
                        "A missing component should read as smaller, not crash"));

        test("amounts are held to two decimal places", () ->
                assertEquals("12.35", Money.of("12.345").toPlainString(),
                        "Half up, so repeated addition does not drift"));

        test("a comma is ignored so a pasted figure works", () ->
                assertEquals("12345.60", Money.of("12,345.60").toPlainString(),
                        "Figures arrive from bank statements with separators"));

        test("a currency symbol is ignored", () ->
                assertEquals("99.00", Money.of("£99").toPlainString(),
                        "A symbol is presentational and must not become part of the value"));

        test("nonsense is rejected by name", () -> {
            String message = TestRunner.thrownMessage(() -> Money.of("about forty"));
            assertTrue(message.contains("about forty"),
                    "The message should quote what was entered, got: " + message);
        });

        test("a percentage of an amount", () ->
                assertEquals("12.00", Money.percentOf(new BigDecimal("100"),
                        new BigDecimal("12")).toPlainString(), "Twelve per cent of a hundred"));

        test("a discount is subtracted from the price", () ->
                assertEquals("85.00", Money.applyPercent(new BigDecimal("100"),
                        new BigDecimal("15")).toPlainString(), "Fifteen per cent off a hundred"));

        test("a discount cannot take the price below zero", () ->
                assertEquals("0.00", Money.applyPercent(new BigDecimal("10"),
                        new BigDecimal("150")).toPlainString(),
                        "A negative ticket price is a refund, not a sale"));

        test("a negative amount is accepted for a credit", () ->
                assertEquals("-50.00", Money.of("-50").toPlainString(),
                        "Money owed is negative and must be enterable"));

        test("a share of nothing is zero, not infinite", () ->
                assertEquals("0.00", Money.sharePercent(new BigDecimal("10"),
                        BigDecimal.ZERO).toPlainString(),
                        "A budget of zero with spend against it cannot report a ratio"));

        test("adding many small amounts does not drift", () -> {
            BigDecimal total = BigDecimal.ZERO;
            for (int index = 0; index < 1000; index++) {
                total = Money.sum(total, new BigDecimal("0.10"));
            }
            assertEquals("100.00", total.toPlainString(),
                    "A thousand ten-pence sales must come to exactly a hundred");
        });

        test("adding nine thousand card payments keeps the pennies", () -> {
            BigDecimal total = BigDecimal.ZERO;
            for (int index = 0; index < 9000; index++) {
                total = Money.sum(total, new BigDecimal("12.34"));
            }
            assertEquals("111060.00", total.toPlainString(),
                    "Binary rounding would lose real money here");
        });

        test("a share of a whole", () ->
                assertEquals("50.00", Money.sharePercent(new BigDecimal("50"),
                        new BigDecimal("100")).toPlainString(), "Half of a hundred"));

        test("compact form is readable at tile size", () -> {
            assertEquals("£1.2k", Money.compact(new BigDecimal("1234")),
                    "A tile has no room for six digits");
            assertEquals("£2.5M", Money.compact(new BigDecimal("2500000")),
                    "A large figure still needs to fit");
            assertEquals("£420", Money.compact(new BigDecimal("420")),
                    "Small amounts are shown as they are");
        });

        test("a negative figure shows its sign", () ->
                assertEquals("-£12.50", Money.signed(new BigDecimal("-12.50")),
                        "An overrun has to be visible as one"));

        test("percentOf of null is zero", () ->
                assertEquals("0.00", Money.percentOf(null, new BigDecimal("10")).toPlainString(),
                        "A missing amount cannot produce a share"));
    }
}
