package com.alertworker.common.util;

import java.util.concurrent.ThreadLocalRandom;

// #AI-CODE-CLASS-dev_release/v0.0.1-20260901: snowflake-style numeric id (timestamp + machine + sequence)
public final class MessageNoGenerator {

    // Snowflake layout: 41-bit timestamp (ms since custom epoch) | 10-bit machine id | 12-bit sequence.
    private static final long EPOCH = 1577836800000L; // 2020-01-01T00:00:00Z
    private static final long SEQUENCE_BITS = 12L;
    private static final long MACHINE_BITS = 10L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L;
    private static final long MACHINE_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS;

    // Random machine id per JVM (0..1023); enough to keep ids unique within a gateway instance.
    private static final long MACHINE_ID = ThreadLocalRandom.current().nextInt(1 << (int) MACHINE_BITS);

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private MessageNoGenerator() {
    }

    public static synchronized String generate() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                timestamp = nextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (MACHINE_ID << MACHINE_SHIFT)
                | sequence;
        return String.valueOf(id);
    }

    private static long nextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
