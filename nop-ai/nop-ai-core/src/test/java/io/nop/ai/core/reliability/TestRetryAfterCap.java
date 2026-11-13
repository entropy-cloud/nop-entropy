package io.nop.ai.core.reliability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.api.chat.ErrorClassification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C7-003 (plan 2556 裁定 F): the provider-advised
 * {@code retryAfterMs} is sanity-capped at {@code MAX_RETRY_AFTER_MS} (10min)
 * at the {@code StandardRetryPolicy.shouldRetry} entry point — a single
 * normalization covering all three consumption paths (overloaded floor,
 * rate-limited floor, transient floor) — with the original value WARN-logged.
 */
public class TestRetryAfterCap {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    public void attachLog() {
        logger = (Logger) LoggerFactory.getLogger(StandardRetryPolicy.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    public void detachLog() {
        logger.detachAppender(appender);
    }

    private static final long HUGE = 10_000_000L; // ~2.8 hours

    @Test
    public void hugeRetryAfterOnRateLimitedIsCapped() {
        StandardRetryPolicy policy = new StandardRetryPolicy(3, 1000L, 30_000L);
        RetryOutcome outcome = policy.shouldRetry(new RetryContext(0, null,
                ErrorClassification.RATE_LIMITED, false, HUGE));
        assertTrue(outcome.isRetry());
        long delay = outcome.getDelayMs();
        assertTrue(delay >= StandardRetryPolicy.MAX_RETRY_AFTER_MS,
                "the capped retry must not be shorter than the ceiling");
        // delay = cap + jitter(min(base*2^0, maxDelay) = 1000)
        assertTrue(delay <= StandardRetryPolicy.MAX_RETRY_AFTER_MS + 1000L,
                "delay must be cap + bounded jitter, got " + delay);
    }

    @Test
    public void hugeRetryAfterOnTransientFloorIsCapped() {
        StandardRetryPolicy policy = new StandardRetryPolicy(3, 1000L, 30_000L);
        RetryOutcome outcome = policy.shouldRetry(new RetryContext(0, null,
                ErrorClassification.TRANSIENT, false, HUGE));
        assertTrue(outcome.isRetry());
        // TRANSIENT floor: max(full-jitter in [0,1000], capped retryAfter=600000)
        assertEquals(StandardRetryPolicy.MAX_RETRY_AFTER_MS, outcome.getDelayMs(),
                "the transient floor must clamp to the cap");
    }

    @Test
    public void hugeRetryAfterOnOverloadedFloorIsCapped() {
        StandardRetryPolicy policy = new StandardRetryPolicy(3, 1000L, 30_000L);
        RetryOutcome outcome = policy.shouldRetry(new RetryContext(0, null,
                ErrorClassification.OVERLOADED, false, HUGE));
        assertTrue(outcome.isRetry());
        // Overloaded curve step 10s ± 20% → max 12s; floor = capped 600s → delay = 600s.
        assertEquals(StandardRetryPolicy.MAX_RETRY_AFTER_MS, outcome.getDelayMs(),
                "the overloaded floor must clamp to the cap");
    }

    @Test
    public void originalValueIsWarnLogged() {
        StandardRetryPolicy policy = new StandardRetryPolicy(3, 1000L, 30_000L);
        policy.shouldRetry(new RetryContext(0, null, ErrorClassification.RATE_LIMITED, false, HUGE));

        List<ILoggingEvent> events = appender.list;
        ILoggingEvent warn = events.stream()
                .filter(e -> e.getFormattedMessage().contains("nop.ai.retry-after-capped"))
                .findFirst().orElse(null);
        assertNotNull(warn, "capping an oversized Retry-After must WARN (nop.ai.retry-after-capped)");
        assertTrue(warn.getFormattedMessage().contains(String.valueOf(HUGE)),
                "the WARN must record the original uncapped value");
    }

    @Test
    public void normalRetryAfterPassesThroughUnchanged() {
        StandardRetryPolicy policy = new StandardRetryPolicy(3, 1000L, 30_000L);
        RetryOutcome outcome = policy.shouldRetry(new RetryContext(0, null,
                ErrorClassification.RATE_LIMITED, false, 5000L));
        assertTrue(outcome.isRetry());
        assertTrue(outcome.getDelayMs() >= 5000L,
                "an in-range Retry-After must remain the floor of the delay");
        assertTrue(outcome.getDelayMs() < 5000L + 30_000L + 1,
                "no cap interference for an in-range hint");
        assertTrue(appender.list.stream().noneMatch(
                        e -> e.getFormattedMessage().contains("nop.ai.retry-after-capped")),
                "an in-range hint must not trigger the cap WARN");
    }
}
