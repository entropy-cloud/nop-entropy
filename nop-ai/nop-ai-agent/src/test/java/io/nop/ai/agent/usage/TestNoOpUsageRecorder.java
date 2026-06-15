package io.nop.ai.agent.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 201 (L2-17) Phase 1: verify {@link NoOpUsageRecorder} pass-through
 * semantics — singleton identity and a no-throw {@link IUsageRecorder#record}.
 */
public class TestNoOpUsageRecorder {

    @Test
    void noOpReturnsSameSingletonInstance() {
        IUsageRecorder a = NoOpUsageRecorder.noOp();
        IUsageRecorder b = NoOpUsageRecorder.noOp();
        assertSame(a, b, "noOp() must return the same singleton instance");
    }

    @Test
    void recordDoesNotThrow() {
        IUsageRecorder recorder = NoOpUsageRecorder.noOp();
        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-1");
        record.setPromptTokens(10);
        record.setCompletionTokens(5);
        assertDoesNotThrow(() -> recorder.record(record),
                "NoOp pass-through must accept any record without throwing");
    }

    @Test
    void recordAcceptsNullWithoutThrowing() {
        // The pass-through must be robust to defensive callers; it must not
        // throw even if handed a null (it simply discards everything).
        IUsageRecorder recorder = NoOpUsageRecorder.noOp();
        assertDoesNotThrow(() -> recorder.record(null));
    }
}
