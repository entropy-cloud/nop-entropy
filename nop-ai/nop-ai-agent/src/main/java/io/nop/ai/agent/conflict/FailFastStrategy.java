package io.nop.ai.agent.conflict;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shipped default {@link IConflictStrategy}: deny on the first detected
 * cross-session conflict (fail-fast). When {@code existing} is empty or
 * contains only intents from the same session as {@code current}, the call
 * is allowed (single-session executions never see a denial — zero
 * regression for the common case).
 *
 * <p>This is a stateless singleton — get the instance via
 * {@link #failFast()}. Conflict detection is a coordination concern, not a
 * security boundary (the security layers remain Layer 1/2/3), so no
 * insecure-default WARN is emitted for this component.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §4.4 (default strategy).
 */
public final class FailFastStrategy implements IConflictStrategy {

    private static final FailFastStrategy INSTANCE = new FailFastStrategy();

    public static final String STRATEGY_NAME = "FailFastStrategy";

    /**
     * @return the singleton fail-fast strategy instance
     */
    public static FailFastStrategy failFast() {
        return INSTANCE;
    }

    private FailFastStrategy() {
    }

    @Override
    public ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing) {
        if (existing == null || existing.isEmpty()) {
            return ConflictResult.allow(STRATEGY_NAME);
        }
        // Existing intents are pre-filtered by the registry to those whose
        // sessionId differs from current.sessionId (see
        // IWriteIntentRegistry.registerAndGetConflicting). An empty set
        // therefore means "no cross-session conflict"; a non-empty set is
        // by construction a real cross-session conflict. Defensive: still
        // filter to be robust against a custom registry that returns same-
        // session intents.
        Set<WriteIntent> crossSession = existing.stream()
                .filter(i -> !sameSession(i, current))
                .collect(Collectors.toSet());
        if (crossSession.isEmpty()) {
            return ConflictResult.allow(STRATEGY_NAME);
        }
        String conflictPath = current.getFilePath();
        String conflictSessions = crossSession.stream()
                .map(WriteIntent::getSessionId)
                .distinct()
                .collect(Collectors.joining(","));
        String reason = "write conflict on path " + conflictPath
                + ": conflicting sessions=[" + conflictSessions + "]"
                + " (fail-fast: another session has an active write intent on this file)";
        return ConflictResult.deny(STRATEGY_NAME, reason);
    }

    @Override
    public String name() {
        return STRATEGY_NAME;
    }

    private static boolean sameSession(WriteIntent a, WriteIntent b) {
        String sa = a.getSessionId();
        String sb = b.getSessionId();
        if (sa == null || sb == null) {
            return false;
        }
        return sa.equals(sb);
    }
}
