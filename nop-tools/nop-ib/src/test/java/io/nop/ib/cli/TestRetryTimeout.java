package io.nop.ib.cli;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential-retry timeout derivation: worker-measured class time scaled 4x
 * with a 60s floor and the 300s batch cap; no measurement (worker hang/abort)
 * gets a short 120s budget so a genuine hang cannot burn a second watchdog
 * period.
 */
class TestRetryTimeout {

    @Test
    void nullOrUnparseableFailsFastAt120s() {
        assertEquals(Duration.ofSeconds(120), TestCommand.retryTimeout(null));
        assertEquals(Duration.ofSeconds(120), TestCommand.retryTimeout("null"));
        assertEquals(Duration.ofSeconds(120), TestCommand.retryTimeout("abc"));
        assertEquals(Duration.ofSeconds(120), TestCommand.retryTimeout("-5"));
    }

    @Test
    void fastClassesGetThe60sFloor() {
        assertEquals(Duration.ofSeconds(60), TestCommand.retryTimeout("0"));
        assertEquals(Duration.ofSeconds(60), TestCommand.retryTimeout("1200"));
        assertEquals(Duration.ofSeconds(60), TestCommand.retryTimeout("15000"));
    }

    @Test
    void slowClassesScaleFourXCappedAt300s() {
        assertEquals(Duration.ofSeconds(120), TestCommand.retryTimeout("30000"));
        assertEquals(Duration.ofMillis(240_000), TestCommand.retryTimeout("60000"));
        assertEquals(Duration.ofSeconds(300), TestCommand.retryTimeout("150000"));
        assertEquals(Duration.ofSeconds(300), TestCommand.retryTimeout("900000"));
    }
}
