package io.nop.ai.agent.conflict;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 214 focused tests for {@link FailFastStrategy}: verifies the
 * allow-on-no-conflict, allow-on-same-session, and deny-on-cross-session
 * semantics described in design {@code nop-ai-agent-multi-agent.md} §4.4.
 */
public class TestFailFastStrategy {

    private static final String PATH = "/workspace/src/main/Foo.java";

    private static WriteIntent intent(String session, String path) {
        return new WriteIntent(session, "agent-" + session, path, "write-file", 1000L);
    }

    @Test
    void emptyExistingReturnsAllow() {
        FailFastStrategy strategy = FailFastStrategy.failFast();
        WriteIntent current = intent("s1", PATH);

        ConflictResult result = strategy.resolve(current, Collections.emptySet());

        assertEquals(ConflictDecision.ALLOW, result.getDecision(),
                "no existing intents → ALLOW");
        assertNull(result.getReason(),
                "allow result carries no diagnostic reason");
        assertEquals(FailFastStrategy.STRATEGY_NAME, result.getStrategyName());
    }

    @Test
    void nullExistingReturnsAllow() {
        // Defensive: a custom registry may pass null; the strategy must
        // treat it as "no conflict" rather than NPE.
        FailFastStrategy strategy = FailFastStrategy.failFast();

        ConflictResult result = strategy.resolve(intent("s1", PATH), null);

        assertEquals(ConflictDecision.ALLOW, result.getDecision(),
                "null existing set → ALLOW (treated as empty)");
    }

    @Test
    void sameSessionExistingReturnsAllow() {
        FailFastStrategy strategy = FailFastStrategy.failFast();
        WriteIntent current = intent("s1", PATH);
        // Same session, same path: even if a custom registry leaked these
        // into the existing set, the strategy must filter them out.
        Set<WriteIntent> existing = new HashSet<>();
        existing.add(intent("s1", PATH));
        existing.add(intent("s1", PATH));

        ConflictResult result = strategy.resolve(current, existing);

        assertEquals(ConflictDecision.ALLOW, result.getDecision(),
                "all-same-session existing → ALLOW (not a conflict)");
    }

    @Test
    void crossSessionConflictReturnsDeny() {
        FailFastStrategy strategy = FailFastStrategy.failFast();
        WriteIntent current = intent("s1", PATH);
        Set<WriteIntent> existing = new HashSet<>();
        existing.add(intent("s2", PATH));

        ConflictResult result = strategy.resolve(current, existing);

        assertEquals(ConflictDecision.DENY, result.getDecision(),
                "cross-session conflict → DENY (fail-fast)");
        assertTrue(result.isDenied(), "isDenied() must mirror the decision");
        assertNotNull(result.getReason(),
                "deny result must carry a human-readable reason");
        // The reason must mention the path and the conflicting session.
        assertTrue(result.getReason().contains(PATH),
                "deny reason must reference the conflicting path; got: " + result.getReason());
        assertTrue(result.getReason().contains("s2"),
                "deny reason must reference the conflicting session id; got: " + result.getReason());
        assertEquals(FailFastStrategy.STRATEGY_NAME, result.getStrategyName());
    }

    @Test
    void multipleCrossSessionConflictsListsAllSessions() {
        FailFastStrategy strategy = FailFastStrategy.failFast();
        WriteIntent current = intent("s1", PATH);
        Set<WriteIntent> existing = new HashSet<>();
        existing.add(intent("s2", PATH));
        existing.add(intent("s3", PATH));

        ConflictResult result = strategy.resolve(current, existing);

        assertEquals(ConflictDecision.DENY, result.getDecision());
        String reason = result.getReason();
        assertTrue(reason.contains("s2") && reason.contains("s3"),
                "deny reason must list every conflicting session; got: " + reason);
    }

    @Test
    void mixedSameAndCrossSessionReturnsDeny() {
        // Same-session intents are filtered; a single cross-session intent
        // is still a real conflict.
        FailFastStrategy strategy = FailFastStrategy.failFast();
        WriteIntent current = intent("s1", PATH);
        Set<WriteIntent> existing = new HashSet<>();
        existing.add(intent("s1", PATH));
        existing.add(intent("s2", PATH));

        ConflictResult result = strategy.resolve(current, existing);

        assertEquals(ConflictDecision.DENY, result.getDecision(),
                "any cross-session intent in existing → DENY");
        assertTrue(result.getReason().contains("s2"));
        assertTrue(!result.getReason().contains("s1,"),
                "deny reason should not list the current session as a conflict");
    }

    @Test
    void singletonFactoryReturnsSameInstance() {
        assertSame(FailFastStrategy.failFast(), FailFastStrategy.failFast(),
                "failFast() must return the singleton");
    }

    @Test
    void nameReturnsStableStrategyName() {
        assertEquals(FailFastStrategy.STRATEGY_NAME, FailFastStrategy.failFast().name());
    }
}
