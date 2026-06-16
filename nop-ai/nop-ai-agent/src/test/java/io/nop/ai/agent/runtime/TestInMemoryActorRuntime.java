package io.nop.ai.agent.runtime;

import io.nop.ai.agent.message.DeferredAckMailbox;
import io.nop.ai.agent.message.IMailbox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link InMemoryActorRuntime}: state transitions,
 * createActor idempotency, destroyActor graceful stop, registry wiring,
 * and {@code isEnabled()}.
 */
public class TestInMemoryActorRuntime {

    private static final long FAST_POLL_MS = 50L;
    private static final long FAST_SHUTDOWN_MS = 3000L;

    private InMemoryActorRuntime newRuntime(Map<String, IMailbox> mailboxes) {
        return new InMemoryActorRuntime(
                sessionId -> mailboxes.get(sessionId),
                FAST_POLL_MS, FAST_SHUTDOWN_MS);
    }

    @Test
    void isEnabledReturnsTrue() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        assertTrue(rt.isEnabled());
    }

    @Test
    void createActorRegistersAndTransitionsToRunningOrIdle() throws Exception {
        Map<String, IMailbox> mailboxes = new HashMap<>();
        InMemoryActorRuntime rt = newRuntime(mailboxes);

        AgentActor actor = rt.createActor("s1", "test-agent");

        assertEquals("s1", actor.getSessionId());
        assertEquals("test-agent", actor.getAgentName());
        assertNotEquals("s1", actor.getActorId()); // actorId is a UUID, not the sessionId

        // Actor should be registered in the registry
        assertTrue(rt.getActor(actor.getActorId()).isPresent());
        assertTrue(rt.getActorBySession("s1").isPresent());

        // The consumption loop runs on a dedicated thread. Give it time to
        // start (transition READY → RUNNING), then transition to IDLE (empty
        // mailbox → no message → IDLE).
        waitForStatus(actor, AgentActorStatus.IDLE, 2000);
        assertEquals(AgentActorStatus.IDLE, actor.getStatus());

        rt.destroyActor(actor.getActorId());
    }

    @Test
    void createActorIdempotentReturnsExistingForSameSession() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());

        AgentActor first = rt.createActor("s1", "agent-a");
        AgentActor second = rt.createActor("s1", "agent-a");

        assertEquals(first.getActorId(), second.getActorId(),
                "createActor with same active session must return existing instance");
        assertTrue(first == second, "must be the same object");

        rt.destroyActor(first.getActorId());
    }

    @Test
    void createActorReplacesTerminalActor() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());

        AgentActor first = rt.createActor("s1", "agent-a");
        rt.destroyActor(first.getActorId()); // → STOPPED, unregistered

        AgentActor second = rt.createActor("s1", "agent-a");
        assertNotEquals(first.getActorId(), second.getActorId(),
                "createActor after STOPPED must create a fresh instance");
        assertFalse(rt.getActor(first.getActorId()).isPresent(),
                "old actor must be gone from registry");
        assertTrue(rt.getActor(second.getActorId()).isPresent());

        rt.destroyActor(second.getActorId());
    }

    @Test
    void destroyActorSetsStoppedAndUnregisters() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        AgentActor actor = rt.createActor("s1", "agent-a");

        boolean destroyed = rt.destroyActor(actor.getActorId());
        assertTrue(destroyed);
        assertEquals(AgentActorStatus.STOPPED, actor.getStatus());
        assertFalse(rt.getActor(actor.getActorId()).isPresent());
        assertFalse(rt.getActorBySession("s1").isPresent());
    }

    @Test
    void destroyActorUnknownReturnsFalse() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        assertFalse(rt.destroyActor("nonexistent"));
        assertFalse(rt.destroyActor(null));
    }

    @Test
    void destroyAllDestroysAllAndReturnsCount() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        AgentActor a1 = rt.createActor("s1", "g1");
        AgentActor a2 = rt.createActor("s2", "g2");
        AgentActor a3 = rt.createActor("s3", "g3");

        int count = rt.destroyAll();
        assertEquals(3, count);
        assertEquals(AgentActorStatus.STOPPED, a1.getStatus());
        assertEquals(AgentActorStatus.STOPPED, a2.getStatus());
        assertEquals(AgentActorStatus.STOPPED, a3.getStatus());
        assertTrue(rt.getActiveActors().isEmpty());
    }

    @Test
    void getActiveActorsExcludesStopped() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        AgentActor a1 = rt.createActor("s1", "g1");
        rt.createActor("s2", "g2");

        assertEquals(2, rt.getActiveActors().size());

        rt.destroyActor(a1.getActorId());

        assertEquals(1, rt.getActiveActors().size());

        rt.destroyAll();
    }

    @Test
    void createActorNullSessionIdThrows() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        assertThrows(IllegalArgumentException.class, () -> rt.createActor(null, "g"));
        assertThrows(IllegalArgumentException.class, () -> rt.createActor("", "g"));
    }

    @Test
    void createActorNullAgentNameThrows() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        assertThrows(IllegalArgumentException.class, () -> rt.createActor("s", null));
        assertThrows(IllegalArgumentException.class, () -> rt.createActor("s", ""));
    }

    @Test
    void createActorWithNullMailboxGracefulIdle() throws Exception {
        // No mailbox in the map → mailboxLookup returns null → Actor idles
        // without polling (graceful, no exception).
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        AgentActor actor = rt.createActor("s-no-mbox", "g");

        waitForStatus(actor, AgentActorStatus.IDLE, 2000);
        assertEquals(AgentActorStatus.IDLE, actor.getStatus());
        assertNull(actor.getMailbox(),
                "mailbox must be null when mailboxLookup returns null");

        rt.destroyActor(actor.getActorId());
    }

    @Test
    void destroyAllAfterPartialDestroy() {
        InMemoryActorRuntime rt = newRuntime(new HashMap<>());
        rt.createActor("s1", "g");
        rt.createActor("s2", "g");

        rt.destroyActor(rt.getActorBySession("s1").get().getActorId());
        int remaining = rt.destroyAll();
        assertEquals(1, remaining);

        rt.destroyAll();
    }

    // ==================== Helpers ====================

    private static void waitForStatus(AgentActor actor, AgentActorStatus expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (actor.getStatus() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        // If we get here, the status didn't reach the expected value — let
        // the assertion in the caller fail with the actual status.
    }
}
