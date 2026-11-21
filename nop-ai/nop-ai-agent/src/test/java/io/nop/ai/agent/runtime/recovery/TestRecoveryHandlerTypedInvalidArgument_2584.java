package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 2 (audit nop-ai Minor, error-handling convention): the
 * config setters of the recovery handlers must fail fast with a typed
 * {@link NopAiAgentException} (ERR_AGENT_INVALID_ARGUMENT), not a bare
 * {@link IllegalArgumentException}.
 */
public class TestRecoveryHandlerTypedInvalidArgument_2584 {

    @Test
    void orphanHandlerSettersUseTypedError() {
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.SKIP, null, null);

        NopAiAgentException ex1 = assertThrows(NopAiAgentException.class,
                () -> handler.setResumeMaxAttempts(0));
        assertTrue(String.valueOf(ex1.getMessage()).contains("resumeMaxAttempts"));

        NopAiAgentException ex2 = assertThrows(NopAiAgentException.class,
                () -> handler.setResumeBackoffBaseMs(-1));
        assertTrue(String.valueOf(ex2.getMessage()).contains("resumeBackoffBaseMs"));

        // valid values still accepted
        assertDoesNotThrow(() -> {
            handler.setResumeMaxAttempts(1);
            handler.setResumeBackoffBaseMs(0);
        });
    }

    @Test
    void timeoutHandlerSettersUseTypedError() {
        RecordingEngine engine = new RecordingEngine();
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60, engine, ds, "inst-typed-err");

        NopAiAgentException ex1 = assertThrows(NopAiAgentException.class,
                () -> handler.setCancelMaxAttempts(0));
        assertTrue(String.valueOf(ex1.getMessage()).contains("cancelMaxAttempts"));

        NopAiAgentException ex2 = assertThrows(NopAiAgentException.class,
                () -> handler.setCancelBackoffBaseMs(-1));
        assertTrue(String.valueOf(ex2.getMessage()).contains("cancelBackoffBaseMs"));

        assertDoesNotThrow(() -> {
            handler.setCancelMaxAttempts(1);
            handler.setCancelBackoffBaseMs(0);
        });
    }

    /** Minimal engine stub (only abstract members implemented; handlers use defaults). */
    static final class RecordingEngine implements io.nop.ai.agent.engine.IAgentEngine {
        final AtomicInteger cancelCount = new AtomicInteger();

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.agent.engine.AgentExecutionResult> execute(
                io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(
                io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> cancelSession(
                String sessionId, String reason, boolean forced) {
            cancelCount.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
