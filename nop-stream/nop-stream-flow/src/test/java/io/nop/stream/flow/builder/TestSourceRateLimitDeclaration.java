package io.nop.stream.flow.builder;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.RateLimitedSourceFunction;
import io.nop.stream.flow.model.StreamSourceModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U3 (deferred-unlock, plan 2376): the {@code <source>} rate-limit declaration
 * wiring, pinned directly against the builder's package-private decision point.
 */
public class TestSourceRateLimitDeclaration {

    private static SourceFunction<String> fn() {
        return new SourceFunction<String>() {
            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }
        };
    }

    private static StreamSourceModel source(long rows, long bytes) {
        StreamSourceModel t = new StreamSourceModel();
        t.setId("src");
        t.setRateLimitRowsPerSecond(rows);
        t.setRateLimitBytesPerSecond(bytes);
        return t;
    }

    @Test
    public void declaredRateWrapsResolvedSource() {
        SourceFunction<String> wrapped = StreamModelDslBuilder.applyRateLimitDeclaration(
                fn(), source(100, 0));
        assertTrue(wrapped instanceof RateLimitedSourceFunction,
                () -> "a declared rate must wrap the resolved source, got: "
                        + wrapped.getClass().getName());
        assertEquals(100L, ((RateLimitedSourceFunction<String>) wrapped).getLimiter()
                .getRowsPerSecond());
    }

    @Test
    public void allZeroDeclarationReturnsDelegateUnwrapped() {
        SourceFunction<String> delegate = fn();
        assertSame(delegate, StreamModelDslBuilder.applyRateLimitDeclaration(delegate,
                source(0, 0)), "0/0 = unlimited: no wrapper overhead");
    }

    @Test
    public void negativeRowsFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.applyRateLimitDeclaration(fn(), source(-1, 0)));
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        assertTrue(ex.getMessage().contains("negative"), () -> ex.getMessage());
    }

    @Test
    public void negativeBytesFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.applyRateLimitDeclaration(fn(), source(0, -5)));
        assertTrue(ex.getMessage().contains("negative"), () -> ex.getMessage());
    }

    @Test
    public void bytesOnlyDeclarationAlsoWraps() {
        SourceFunction<String> wrapped = StreamModelDslBuilder.applyRateLimitDeclaration(
                fn(), source(0, 2048));
        assertTrue(wrapped instanceof RateLimitedSourceFunction,
                "a bytes-only declaration must also wrap");
        assertEquals(2048L, ((RateLimitedSourceFunction<String>) wrapped).getLimiter()
                .getBytesPerSecond());
    }
}
