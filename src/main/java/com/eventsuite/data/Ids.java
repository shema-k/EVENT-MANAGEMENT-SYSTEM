package com.eventsuite.data;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Identifiers for records the system creates.
 *
 * <p>Ids are prefixed by kind so a reference from one table can be recognised in a
 * log or a support email without a lookup: {@code evt-}, {@code tkt-}, {@code inv-}.
 * That is worth the extra characters because the most common question about a broken
 * record is which system part owns it.
 *
 * <p>The numeric part comes from a counter mixed with a clock reading and a random
 * component, rather than from a database sequence alone. The consequence is that two
 * copies of the same file, opened at once, will not mint the same id for two
 * different rows — which is the one case a plain counter gets wrong.
 */
public final class Ids {

    /** Excludes 0, O, 1 and I, which get misread when written down by hand. */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    /** An id of the form {@code prefix-base36counter-random}. */
    public static String next(String prefix) {
        long stamp = System.currentTimeMillis();
        long sequence = COUNTER.incrementAndGet();
        return prefix + "-"
                + Long.toString(stamp, 36)
                + Long.toString(sequence, 36)
                + randomSuffix(4);
    }

    public static String event() {
        return next("evt");
    }
    public static String ticketType() {
        return next("tty");
    }

    public static String attendee() {
        return next("att");
    }

    public static String registration() {
        return next("reg");
    }

    public static String speaker() {
        return next("spk");
    }

    public static String resource() {
        return next("res");
    }

    public static String booking() {
        return next("bkg");
    }

    public static String payment() {
        return next("pay");
    }

    public static String invoice() {
        return next("inv");
    }

    public static String campaign() {
        return next("cmp");
    }

    public static String discount() {
        return next("dsc");
    }

    public static String checkIn() {
        return next("chk");
    }

    public static String engagement() {
        return next("eng");
    }

    public static String feedback() {
        return next("fbk");
    }

    public static String sustainability() {
        return next("sus");
    }

    public static String rule() {
        return next("rul");
    }

    public static String log() {
        return next("log");
    }

    /**
     * A receipt number for the current year, counting up.
     *
     * <p>Invoices and receipts are numbered so they can be quoted in an email without
     * ambiguity, and they carry the year so a receipt from last year is not mistaken
     * for one from this one.
     */
    public static String receiptNumber(String prefix, int year, int sequence) {
        return String.format(Locale.ROOT, "%s-%d-%03d", prefix, year, Math.max(0, sequence));
    }

    private static String randomSuffix(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }
}
