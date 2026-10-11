package io.nop.jq;

import io.nop.jq.runtime.JqHaltErrorException;
import io.nop.jq.runtime.JqHaltException;
import io.nop.jq.runtime.JqRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for jq 1.7.1 halt semantics: {@code halt} terminates the
 * program uncatchably, while {@code halt_error(code)} throws a catchable error
 * whose value is the input. Before the fix both were plain
 * {@link JqRuntimeException}s, so {@code try (halt) catch "caught"} wrongly
 * produced "caught".
 *
 * <p>Pure in-process tests (JqEngine only) — no external jq binary required.
 * The expected behaviors below were validated once against a reference jq 1.7.1
 * binary during development and are asserted here as literals.
 */
public class TestJqHaltSemantics {

    @Test
    public void testHaltNotCatchableByTryCatch() {
        IJsonQuery q = JqEngine.compile("try (halt) catch \"caught\"");
        var ex = assertThrows(JqHaltException.class, () -> q.apply(Map.of("a", 1)));
        assertEquals(0, ex.exitCode());
    }

    @Test
    public void testHaltNotSwallowedByErrorSuppression() {
        IJsonQuery q = JqEngine.compile("try (halt)");
        assertThrows(JqHaltException.class, () -> q.apply(Map.of("a", 1)));
    }

    @Test
    public void testHaltNotCatchableInsideArrayAndAlternative() {
        IJsonQuery q = JqEngine.compile("[try (halt) catch \"c\", \"after\"]");
        assertThrows(JqHaltException.class, () -> q.apply(Map.of("a", 1)));
    }

    @Test
    public void testHaltErrorIsCatchableWithValueBeingInput() {
        IJsonQuery q = JqEngine.compile("try (halt_error(3)) catch .");
        List<Object> out = q.apply(Map.of("a", 1));
        assertEquals(List.of(Map.of("a", 1)), out);
    }

    @Test
    public void testUncaughtHaltErrorCarriesExitCode() {
        IJsonQuery q = JqEngine.compile(". | halt_error(3)");
        var ex = assertThrows(JqRuntimeException.class, () -> q.apply(Map.of("a", 1)));
        assertInstanceOf(JqHaltErrorException.class, ex);
        assertEquals(3, ((JqHaltErrorException) ex).exitCode());
    }

    @Test
    public void testHaltPropagatesThroughPathAssignmentRecovery() {
        // path-eval catches JqRuntimeException to recover; halt must stay uncatchable
        IJsonQuery q = JqEngine.compile(".a |= (halt)");
        assertThrows(JqHaltException.class, () -> q.apply(Map.of("a", 1)));
    }
}
