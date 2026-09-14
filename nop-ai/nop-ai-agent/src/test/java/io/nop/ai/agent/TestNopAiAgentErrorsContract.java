package io.nop.ai.agent;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.rule.GuardrailRule;
import io.nop.ai.agent.router.SmartModelRouter;
import io.nop.ai.agent.security.SecurityCheckpointChain;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InSessionCompactionSnapshotArchive;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INVALID_ARGUMENT;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INVALID_STATE;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error-code test nails for the three agent-side codes introduced by plan 356
 * (M5-P1 round-1 audit finding 1): {@code ERR_AGENT_INVALID_ARGUMENT},
 * {@code ERR_AGENT_INVALID_STATE}, {@code ERR_AGENT_INTERNAL_DETAIL}. Before
 * this test the codes had ~600 throw sites and zero {@code getErrorCode()}
 * assertions, so the code value / parameter contract could drift silently.
 *
 * <p>Each test triggers a REAL production throw site through its public entry
 * point (not a test stub) and asserts the error-code value, the
 * {@code nop.err.ai.agent.*} id string, the {@code detail} param, and that
 * the message template renders with the param (Minimum Rules #22/#23 — value
 * contract, not just exception type).
 *
 * <p>Representative throw-site inventory (Proof, plan M5-P1 Phase 1):
 * <ul>
 *   <li>{@code ERR_AGENT_INVALID_ARGUMENT} — engine path:
 *       plan/runtime/PlanScheduler.java:59 (null plan),
 *       plan/runtime/PlanRunner.java:58 (null phase); session path:
 *       session/InSessionCompactionSnapshotArchive.java:49 (empty messages);
 *       guardrail path: guardrail/rule/GuardrailRule.java:45 (null id).</li>
 *   <li>{@code ERR_AGENT_INVALID_STATE} — engine path:
 *       plan/runtime/PlanReplanner.java:265/270 (SPLIT_TASK without spec),
 *       plan/runtime/PlanExecutor.java:151 (recovery cycle bound); team path:
 *       team/flow/MemberExecOutcome.java:230 (toException on COMPLETED),
 *       team/InMemoryTeamTaskStore.java:82 (taskId collision); security path:
 *       security/SecurityCheckpointChain.java:40 (empty chain).</li>
 *   <li>{@code ERR_AGENT_INTERNAL_DETAIL} — engine path:
 *       router/SmartModelRouter.java:321 (no tier model), 330/334 (tier
 *       threshold ordering); session path:
 *       session/FileBackedSessionStore.java:103 (null rootDirectory),
 *       session/SessionFileWriter.java:69/72 (null args).</li>
 * </ul>
 */
public class TestNopAiAgentErrorsContract {

    private static void assertAgentCode(NopAiAgentException ex, io.nop.api.core.exceptions.ErrorCode code,
                                        String expectedId, String detailValue) {
        assertEquals(code.getErrorCode(), ex.getErrorCode(),
                "thrown exception must carry the ErrorCode value of " + expectedId);
        assertEquals(expectedId, ex.getErrorCode(),
                "error-code id must follow the nop.err.ai.agent.* dotted convention");
        assertNotNull(ex.getDescription(), "description template must be present");
        assertEquals(detailValue, ex.getParam(ARG_DETAIL),
                "detail param must be attached by the throw site");
        assertTrue(ex.getMessage().contains(detailValue),
                "message template must render the detail param. Got: " + ex.getMessage());
    }

    // ========================================================================
    // ERR_AGENT_INVALID_ARGUMENT
    // ========================================================================

    @Test
    void invalidArgumentFromSessionArchivePut() {
        InSessionCompactionSnapshotArchive archive = new InSessionCompactionSnapshotArchive("s1");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> archive.put(Collections.emptyList()));
        assertAgentCode(ex, ERR_AGENT_INVALID_ARGUMENT, "nop.err.ai.agent.invalid-argument",
                "InSessionCompactionSnapshotArchive.put requires non-null, non-empty messages");
    }

    @Test
    void invalidArgumentFromGuardrailRuleCtor() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new GuardrailRule(null, null, null, null, null, null, null, null, null));
        assertAgentCode(ex, ERR_AGENT_INVALID_ARGUMENT, "nop.err.ai.agent.invalid-argument",
                "GuardrailRule: id must not be null or empty");
    }

    // ========================================================================
    // ERR_AGENT_INVALID_STATE
    // ========================================================================

    @Test
    void invalidStateFromSecurityCheckpointChainBuild() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> SecurityCheckpointChain.builder().build());
        assertAgentCode(ex, ERR_AGENT_INVALID_STATE, "nop.err.ai.agent.invalid-state",
                "SecurityCheckpointChain must have at least one checkpoint");
    }

    // ========================================================================
    // ERR_AGENT_INTERNAL_DETAIL
    // ========================================================================

    @Test
    void internalDetailFromFileBackedSessionStoreCtor() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new FileBackedSessionStore(null));
        assertAgentCode(ex, ERR_AGENT_INTERNAL_DETAIL, "nop.err.ai.agent.internal-detail",
                "FileBackedSessionStore: rootDirectory must not be null");
    }

    @Test
    void internalDetailFromSmartModelRouterBuild() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> SmartModelRouter.builder().build());
        assertAgentCode(ex, ERR_AGENT_INTERNAL_DETAIL, "nop.err.ai.agent.internal-detail",
                "SmartModelRouter requires at least one configured tier model");
    }
}