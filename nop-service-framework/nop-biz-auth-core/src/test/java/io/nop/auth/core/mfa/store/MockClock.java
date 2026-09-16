package io.nop.auth.core.mfa.store;

import io.nop.api.core.time.IClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A mock clock for deterministic time control in tests.
 * Replaces Thread.sleep-based TTL expiry tests with instant time advancement.
 */
class MockClock implements IClock {
    private volatile long millis;
    private final ZoneId zoneId = ZoneId.systemDefault();

    MockClock(long initialMillis) {
        this.millis = initialMillis;
    }

    static MockClock now() {
        return new MockClock(System.currentTimeMillis());
    }

    void advanceMillis(long delta) {
        this.millis += delta;
    }

    void advanceSeconds(long seconds) {
        this.millis += seconds * 1000L;
    }

    @Override
    public long currentTimeMillis() {
        return millis;
    }

    @Override
    public long nanoTime() {
        return millis * 1_000_000L;
    }

    @Override
    public LocalDate currentDate() {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate();
    }

    @Override
    public LocalDateTime currentDateTime() {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime();
    }
}
