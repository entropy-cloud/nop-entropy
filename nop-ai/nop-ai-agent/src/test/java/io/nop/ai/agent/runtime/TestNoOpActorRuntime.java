package io.nop.ai.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link NoOpActorRuntime} shipped default behaviour.
 *
 * <p>Verifies the No Silent No-Op contract (Minimum Rules #24): the shipped
 * default returns {@code false} from {@link NoOpActorRuntime#isEnabled()} so
 * the engine skips the Actor path entirely (not exception-based control flow),
 * and {@link NoOpActorRuntime#createActor} throws
 * {@link UnsupportedOperationException} as a defensive measure when called
 * directly (bypassing the {@code isEnabled()} gate).
 */
public class TestNoOpActorRuntime {

    @Test
    void isEnabledReturnsFalse() {
        assertFalse(NoOpActorRuntime.noOp().isEnabled());
    }

    @Test
    void createActorThrowsUnsupportedOperationException() {
        NoOpActorRuntime rt = NoOpActorRuntime.noOp();
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> rt.createActor("s1", "agent"));
        // The error message must explain how to enable — not a bare UOE
        assertTrue(ex.getMessage().contains("not enabled"));
    }

    @Test
    void getActorReturnsEmpty() {
        assertFalse(NoOpActorRuntime.noOp().getActor("anything").isPresent());
    }

    @Test
    void getActorBySessionReturnsEmpty() {
        assertFalse(NoOpActorRuntime.noOp().getActorBySession("anything").isPresent());
    }

    @Test
    void getActiveActorsReturnsEmpty() {
        assertTrue(NoOpActorRuntime.noOp().getActiveActors().isEmpty());
    }

    @Test
    void destroyActorReturnsFalse() {
        assertFalse(NoOpActorRuntime.noOp().destroyActor("anything"));
    }

    @Test
    void destroyAllReturnsZero() {
        assertEquals(0, NoOpActorRuntime.noOp().destroyAll());
    }

    @Test
    void singletonInstance() {
        assertTrue(NoOpActorRuntime.noOp() == NoOpActorRuntime.noOp(),
                "noOp() must return the same singleton instance");
    }
}
