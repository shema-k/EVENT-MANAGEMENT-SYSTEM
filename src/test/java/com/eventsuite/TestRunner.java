package com.eventsuite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A very small test harness, so the project can be tested with nothing but a JDK.
 *
 * <p>No external libraries and no build tool, for the same reason the application
 * itself has none: compile the sources and run this. Each suite registers itself from
 * {@link #main}, which keeps the list in one place and means a new suite cannot be
 * written and then quietly never run.
 */
public final class TestRunner {

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;
    private static String suite = "";

    private TestRunner() {
    }

    public interface Body {
        void run() throws Exception;
    }

    public static void suite(String name) {
        suite = name;
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    public static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (AssertionError | Exception failure) {
            FAILURES.add(suite + " / " + name + "  ->  " + failure);
            System.out.println("  FAIL  " + name + "  ->  " + failure);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message
                    + " (expected <" + expected + "> but was <" + actual + ">)");
        }
    }

    public static void assertClose(double expected, double actual, double tolerance,
                                   String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " (expected " + expected
                    + " but was " + actual + ")");
        }
    }

    /** A throwable carrying a message, for asserting that something is rejected. */
    public static void assertThrows(String message, Body body) {
        try {
            body.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message + " (nothing was thrown)");
    }

    /**
     * Runs a body and returns the message of what it threw.
     *
     * <p>Used where the message matters rather than just the fact of the failure — the
     * rule is only worth having if what it says is worth reading.
     */
    public static String thrownMessage(Body body) {
        try {
            body.run();
        } catch (Exception thrown) {
            return thrown.getMessage() == null ? thrown.getClass().getName()
                    : thrown.getMessage();
        }
        throw new AssertionError("nothing was thrown");
    }

    /** A temporary directory, removed by the caller. */
    public static Path freshDirectory(String prefix) throws Exception {
        return Files.createTempDirectory(prefix);
    }

    public static int summary() {
        System.out.println();
        System.out.println("----------------------------------------");
        System.out.println(passed + " passed, " + FAILURES.size() + " failed");
        if (!FAILURES.isEmpty()) {
            System.out.println();
            for (String failure : FAILURES) {
                System.out.println("  FAILED: " + failure);
            }
        }
        return FAILURES.isEmpty() ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        MoneyTest.register();
        EventTest.register();
        TicketTest.register();
        EventStoreTest.register();
        TicketingFlowTest.register();
        System.exit(summary());
    }
}
