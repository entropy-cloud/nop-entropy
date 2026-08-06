package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link CheckpointTestHarness} (plan 2249 Phase 2 exit
 * criteria): assembly success, real-chain wiring, matchedRule capture,
 * no-silent-skip, and per-case side-effect isolation.
 */
public class TestCheckpointTestHarness {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private final CheckpointTestHarness harness = new CheckpointTestHarness();

    // ===== Exit Criteria: 装配助手成功构造 consultation + chain 非 null =====

    @Test
    void buildDefaultConsultationAssemblesAllThirteenDeps() {
        CollectingAuditLogger audit = new CollectingAuditLogger();
        io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry registry =
                new io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry();
        io.nop.ai.agent.engine.AgentSecurityConsultation consultation =
                harness.buildDefaultConsultation(registry, audit);
        assertNotNull(consultation, "装配助手必须返回非 null consultation");
        assertNotNull(consultation.buildCheckpointChain(),
                "buildCheckpointChain() 必须返回非 null chain（13 个依赖全部可装配）");
    }

    // ===== Exit Criteria: 接线验证 — 真实 chain.evaluate() + matchedRule 捕获 =====

    @Test
    void knownDenyCaseProducesDenyAndCapturesMatchedRule() {
        // bash is in the hardcoded deny-list → toolAccess DENY + "hardcoded_deny_list"
        CheckpointTestCase denyCase = CheckpointTestCase.builder()
                .id("h-deny-001").category("tool-deny-list")
                .toolName("bash").args(Map.of())
                .sessionId("s-deny-1")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("hardcoded_deny_list")
                .description("deny-listed tool must DENY at toolAccess checkpoint")
                .build();

        CheckpointTestResult result = harness.runCase(denyCase);

        assertEquals(SecurityCheckpoint.Decision.DENY, result.getActualDecision(),
                "deny-listed tool must be denied by the real chain");
        assertEquals("hardcoded_deny_list", result.getActualMatchedRule(),
                "CollectingAuditLogger 必须捕获 deny 路径的 AuditEvent.matchedRule");
        assertTrue(result.isPassed(), "DENY + matchedRule 命中 → passed");
        assertNull(result.getFailureReason());
    }

    @Test
    void knownAllowCaseProducesAllowWithNoMatchedRule() {
        // read-file + safe path → ALLOW; firstDenyMatchedRule() is null (no DENY event)
        CheckpointTestCase allowCase = CheckpointTestCase.builder()
                .id("h-allow-001").category("benign")
                .toolName("read-file").args(Map.of("path", "/tmp/safe-read.txt"))
                .workDir("/tmp").sessionId("s-allow-1")
                .expectedDecision(SecurityCheckpoint.Decision.ALLOW)
                .description("benign read of a non-sensitive path must ALLOW")
                .build();

        CheckpointTestResult result = harness.runCase(allowCase);

        assertEquals(SecurityCheckpoint.Decision.ALLOW, result.getActualDecision());
        assertNull(result.getActualMatchedRule(), "ALLOW 路径无 DENY AuditEvent → matchedRule 为 null");
        assertTrue(result.isPassed());
    }

    @Test
    void pathSensitiveDenyCapturesSubRule() {
        // /etc/ is a sensitive prefix → pathAccess DENY + "sensitive_path_prefix"
        CheckpointTestCase pathCase = CheckpointTestCase.builder()
                .id("h-path-001").category("path-sensitive")
                .toolName("read-file").args(Map.of("path", "/etc/passwd"))
                .workDir("/tmp").sessionId("s-path-1")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("sensitive_path_prefix")
                .description("sensitive prefix /etc/ must DENY at pathAccess checkpoint")
                .build();

        CheckpointTestResult result = harness.runCase(pathCase);

        assertEquals(SecurityCheckpoint.Decision.DENY, result.getActualDecision());
        assertEquals("sensitive_path_prefix", result.getActualMatchedRule());
        assertTrue(result.isPassed());
    }

    // ===== Exit Criteria: 无静默跳过 — chain null / evaluate 抛异常时显式失败 =====

    @Test
    void runCaseRejectsNullCase() {
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> harness.runCase(null),
                "null case must fail loud, not silently skip");
    }

    @Test
    void buildDefaultConsultationRejectsNullDeps() {
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> harness.buildDefaultConsultation(null, new CollectingAuditLogger()));
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> harness.buildDefaultConsultation(
                        new io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry(), null));
    }

    // ===== Exit Criteria: 副作用隔离 — 连续跑 case，状态不泄漏 =====

    @Test
    void denialLedgerDoesNotLeakAcrossCases() {
        // Run the SAME deny case (same sessionId) three times. If the
        // DefaultDenialLedger leaked across runs, the third run would hit the
        // threshold (3) and return DENY_AND_BREAK. Because each runCase builds
        // a fresh consultation (fresh ledger), all three must return plain DENY.
        CheckpointTestCase denyCase = CheckpointTestCase.builder()
                .id("h-iso-001").category("tool-deny-list")
                .toolName("bash").args(Map.of())
                .sessionId("shared-session-id")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("hardcoded_deny_list")
                .build();

        for (int i = 0; i < 3; i++) {
            CheckpointTestResult result = harness.runCase(denyCase);
            assertEquals(SecurityCheckpoint.Decision.DENY, result.getActualDecision(),
                    "run #" + i + ": ledger must be fresh (DENY, not DENY_AND_BREAK from accumulated count)");
            assertFalse(result.getActualDecision() == SecurityCheckpoint.Decision.DENY_AND_BREAK,
                    "run #" + i + ": leaked ledger would push the 3rd run over the threshold");
            assertTrue(result.isPassed(), "run #" + i + " should pass");
        }
    }

    @Test
    void postDenialGuardDoesNotLeakAcrossCases() {
        // Two distinct deny cases: if the post-denial-guard fingerprint leaked,
        // the second case could be denied at the postDenial checkpoint with
        // matchedRule "layer3_post_denial_guard" instead of the expected layer.
        CheckpointTestCase first = CheckpointTestCase.builder()
                .id("h-guard-001").category("tool-deny-list")
                .toolName("bash").args(Map.of())
                .sessionId("sess-a")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("hardcoded_deny_list")
                .build();
        CheckpointTestCase second = CheckpointTestCase.builder()
                .id("h-guard-002").category("path-sensitive")
                .toolName("read-file").args(Map.of("path", "/etc/shadow"))
                .workDir("/tmp").sessionId("sess-b")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("sensitive_path_prefix")
                .build();

        CheckpointTestResult r1 = harness.runCase(first);
        CheckpointTestResult r2 = harness.runCase(second);

        assertEquals("hardcoded_deny_list", r1.getActualMatchedRule(),
                "first case denied at toolAccess, not postDenial");
        assertEquals("sensitive_path_prefix", r2.getActualMatchedRule(),
                "second case denied at pathAccess, not postDenial (guard did not leak)");
        assertFalse("layer3_post_denial_guard".equals(r2.getActualMatchedRule()),
                "postDenial guard must not carry over from the first case");
    }

    // ===== Additional wiring: channel-matrix + write-intent-conflict =====

    @Test
    void channelMatrixDeniesElevatedOnGroupChannel() {
        // shell.exec is high-impact → trusted ELEVATED; GROUP channel denies
        // ELEVATED → layer2_permission_matrix
        CheckpointTestCase groupCase = CheckpointTestCase.builder()
                .id("h-matrix-001").category("channel-matrix")
                .toolName("shell.exec").args(Map.of("command", "ls -la"))
                .channelKind(ChannelKind.GROUP).principal(Principal.user())
                .sessionId("s-group-1")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("layer2_permission_matrix")
                .description("ELEVATED operation over GROUP channel must DENY at layer2")
                .build();

        CheckpointTestResult result = harness.runCase(groupCase);

        assertEquals(SecurityCheckpoint.Decision.DENY, result.getActualDecision());
        assertEquals("layer2_permission_matrix", result.getActualMatchedRule());
        assertTrue(result.isPassed());
    }

    @Test
    void writeIntentConflictDeniesWhenRegistryPrePopulated() {
        // edit-file on a path pre-populated with another session's intent →
        // FailFastStrategy DENY → layer2_conflict_strategy
        String conflictPath = "/tmp/harness-conflict-target.txt";
        CheckpointTestCase conflictCase = CheckpointTestCase.builder()
                .id("h-conflict-001").category("write-intent-conflict")
                .toolName("edit-file").args(Map.of("path", conflictPath))
                .workDir("/tmp").sessionId("s-conflict-1")
                .expectedDecision(SecurityCheckpoint.Decision.DENY)
                .expectedMatchedRule("layer2_conflict_strategy")
                .description("cross-session write-intent conflict must DENY at conflict checkpoint")
                .prePopConflict(conflictPath, "other-session")
                .build();

        CheckpointTestResult result = harness.runCase(conflictCase);

        assertEquals(SecurityCheckpoint.Decision.DENY, result.getActualDecision());
        assertEquals("layer2_conflict_strategy", result.getActualMatchedRule());
        assertTrue(result.isPassed(), "conflict case should pass; failureReason=" + result.getFailureReason());
    }

    @Test
    void unexpectedDecisionProducesFailedResultWithReason() {
        // Declare a wrong expectation to verify the failure-reason path.
        CheckpointTestCase bashExpectedAllow = CheckpointTestCase.builder()
                .id("h-fail-001").category("tool-deny-list")
                .toolName("bash").args(Map.of())
                .sessionId("s-fail-1")
                .expectedDecision(SecurityCheckpoint.Decision.ALLOW) // wrong on purpose
                .build();

        CheckpointTestResult result = harness.runCase(bashExpectedAllow);

        assertFalse(result.isPassed());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("decision mismatch"),
                "failureReason should explain the mismatch: " + result.getFailureReason());
    }
}
