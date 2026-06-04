package io.nop.stream.cep.nfa;

import io.nop.stream.cep.nfa.sharedbuffer.Lockable;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TestWatermarkStateRobustness {

    @Test
    void testQuantifierTimesHashCodeConsistencyWithDuration() {
        io.nop.stream.cep.pattern.Quantifier.Times t1 =
                io.nop.stream.cep.pattern.Quantifier.Times.of(2, 4, Duration.ofMillis(1500));
        io.nop.stream.cep.pattern.Quantifier.Times t2 =
                io.nop.stream.cep.pattern.Quantifier.Times.of(2, 4, Duration.ofSeconds(1).plusMillis(500));

        assertEquals(t1.hashCode(), t2.hashCode());
        assertEquals(t1, t2);
    }

    @Test
    void testQuantifierTimesHashCodeNullDuration() {
        io.nop.stream.cep.pattern.Quantifier.Times t1 =
                io.nop.stream.cep.pattern.Quantifier.Times.of(2, null);
        io.nop.stream.cep.pattern.Quantifier.Times t2 =
                io.nop.stream.cep.pattern.Quantifier.Times.of(2, null);

        assertEquals(t1.hashCode(), t2.hashCode());
        assertEquals(t1, t2);
    }

    @Test
    void testDeweyNumberBasedComparisonNotUsingHashCode() {
        DeweyNumber a = new DeweyNumber(1);
        DeweyNumber b = a.addStage().increase(42);
        DeweyNumber c = a.addStage().increase(99);

        assertNotEquals(a.hashCode(), b.hashCode());
        assertFalse(b.equals(c));
    }
}
