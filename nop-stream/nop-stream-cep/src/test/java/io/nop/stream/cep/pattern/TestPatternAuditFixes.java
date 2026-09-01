package io.nop.stream.cep.pattern;

import io.nop.stream.cep.Event;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.nop.stream.cep.NopCepErrors.ARG_PATTERN_DETAIL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the 2026-09-01 cep module audit fixes on {@link Pattern}:
 *
 * <ul>
 *   <li>Non-positive inner window times on {@code oneOrMore}/{@code times}/{@code timesOrMore}
 *       must fail fast (previously they were silently accepted and {@code NFA.isStateTimedOut}'s
 *       {@code windowTime > 0L} guard turned them into "window never applies").</li>
 *   <li>A null pattern name must fail fast at the API boundary (previously it flowed into
 *       NFAStateNameHandler and produced corrupted internal state names like "null:0").</li>
 * </ul>
 */
public class TestPatternAuditFixes {

    private static MalformedPatternException assertMalformedWithDetail(String fragment,
                                                                       ThrowingCall call) {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class, call::run);
        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertTrue(detail != null && detail.contains(fragment),
                "diagnostic detail should contain '" + fragment + "' but was: " + detail);
        return ex;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    // ------------------------------------------------------------------
    // Inner window time validation (oneOrMore / times / timesOrMore)
    // ------------------------------------------------------------------

    @Test
    void testOneOrMoreZeroInnerWindowFailsFast() {
        assertMalformedWithDetail("oneOrMore",
                () -> Pattern.<Event>begin("start").oneOrMore(Duration.ZERO));
    }

    @Test
    void testOneOrMoreNegativeInnerWindowFailsFast() {
        assertMalformedWithDetail("positive inner window",
                () -> Pattern.<Event>begin("start").oneOrMore(Duration.ofMillis(-5)));
    }

    @Test
    void testTimesZeroInnerWindowFailsFast() {
        assertMalformedWithDetail("times",
                () -> Pattern.<Event>begin("start").times(2, Duration.ZERO));
    }

    @Test
    void testTimesRangeNegativeInnerWindowFailsFast() {
        assertMalformedWithDetail("times",
                () -> Pattern.<Event>begin("start").times(1, 3, Duration.ofSeconds(-1)));
    }

    @Test
    void testTimesOrMoreZeroInnerWindowFailsFast() {
        assertMalformedWithDetail("timesOrMore",
                () -> Pattern.<Event>begin("start").timesOrMore(2, Duration.ZERO));
    }

    @Test
    void testNullAndPositiveInnerWindowsStillAccepted() {
        // null means "no inner window" and stays legal on every overload
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").oneOrMore(null));
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").times(2, null));
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").times(1, 3, null));
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").timesOrMore(2, null));
        // a positive inner window stays legal
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").oneOrMore(Duration.ofMillis(100)));
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").times(2, Duration.ofMillis(100)));
        assertDoesNotThrow(() -> Pattern.<Event>begin("start").timesOrMore(2, Duration.ofMillis(100)));
    }

    // ------------------------------------------------------------------
    // Null pattern name validation
    // ------------------------------------------------------------------

    @Test
    void testBeginNullNameFailsFast() {
        assertMalformedWithDetail("name must not be null",
                () -> Pattern.<Event>begin(null));
    }

    @Test
    void testFollowedByNullNameFailsFast() {
        assertMalformedWithDetail("name must not be null",
                () -> Pattern.<Event>begin("start").followedBy((String) null));
    }
}
