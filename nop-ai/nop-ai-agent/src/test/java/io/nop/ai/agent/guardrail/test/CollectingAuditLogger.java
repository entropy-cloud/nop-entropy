package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.IAuditLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link IAuditLogger} that accumulates every {@link AuditEvent} in memory so
 * tests can assert on the recorded decisions and matched rules (design
 * {@code guardrail-contract.md} §增量 4, 裁定 D).
 *
 * <p>This is the test-time side channel used by {@link CheckpointTestHarness}
 * to recover which checkpoint layer denied a tool call:
 * {@code SecurityCheckpointChain.evaluate()} only returns a {@code Decision}
 * enum, but every deny path writes an {@code AuditEvent} carrying a
 * {@code matchedRule}. The harness captures these events and exposes the first
 * deny's {@code matchedRule} for assertion.
 *
 * <p>Extracted from the five test files that previously copy-pasted an inner
 * {@code CollectingAuditLogger} class
 * ({@code TestConflictDetectionDispatchPath},
 * {@code TestLayer23SecureDefaultImpls}, {@code TestDispatchPathApprovalGate},
 * {@code TestLayer23SecureDefaults}, {@code TestAuditLoggerDefault}). It lives
 * in main source (like {@link GuardrailTestSuite}) so downstream-module tests
 * can reuse it.
 *
 * <p><b>Not thread-safe</b>: the checkpoint chain runs sequentially within a
 * single {@code evaluate()} call, so a plain {@link ArrayList} suffices. Do not
 * share one instance across concurrent executions.
 */
public final class CollectingAuditLogger implements IAuditLogger {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void log(AuditEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    /**
     * @return an immutable snapshot of every recorded event, in insertion order
     */
    public List<AuditEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * @return the total number of recorded events
     */
    public int size() {
        return events.size();
    }

    /**
     * @return the {@code matchedRule} of the first recorded {@code DENY}
     *         event, or {@code null} if no deny was recorded. This is the
     *         checkpoint layer that actually denied the call (the chain
     *         short-circuits on first deny, so the first deny event corresponds
     *         to the denying checkpoint).
     */
    public String firstDenyMatchedRule() {
        for (AuditEvent e : events) {
            if (e.getDecision() == AuditDecision.DENY) {
                return e.getMatchedRule();
            }
        }
        return null;
    }

    /**
     * @return {@code true} if any recorded {@code DENY} event carries the given
     *         {@code matchedRule}
     */
    public boolean hasDenyWithRule(String matchedRule) {
        for (AuditEvent e : events) {
            if (e.getDecision() == AuditDecision.DENY
                    && matchedRule != null && matchedRule.equals(e.getMatchedRule())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clear all recorded events. Useful when reusing a single logger instance
     * across isolated sub-scenarios within one test method.
     */
    public void clear() {
        events.clear();
    }
}
