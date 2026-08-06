package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the checkpoint decision test framework (plan 2249
 * Phase 4). Exercises the full Anti-Hollow chain: YAML corpus →
 * {@link CheckpointTestCaseLoader} → {@link CheckpointTestRunner} →
 * {@link CheckpointTestHarness} (real {@code Default*} assembly) → real
 * {@code SecurityCheckpointChain.evaluate()} → {@link CollectingAuditLogger}
 * → {@link CheckpointTestReport}.
 *
 * <p>Asserts that safe (benign) scenarios resolve ALLOW, unsafe scenarios
 * resolve DENY at the expected checkpoint layer, and the report carries
 * non-empty per-category + per-matchedRule metrics.
 */
public class TestCheckpointDecisionEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void fullCorpusRunProducesConsistentReport() {
        // === Anti-Hollow: YAML → loader → runner → harness → real chain → report ===
        CheckpointTestRunner runner = new CheckpointTestRunner();
        CheckpointTestReport report = runner.runDirectory(CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);

        assertNotNull(report);
        assertTrue(report.getTotalCases() >= 18,
                "report should cover >= 18 cases, got " + report.getTotalCases());

        // per-category metrics non-empty
        Map<String, CheckpointTestReport.CategorySlice> perCat = report.getPerCategory();
        assertFalse(perCat.isEmpty(), "per-category metrics must be non-empty");
        assertTrue(perCat.containsKey("benign"), "benign category expected: " + perCat.keySet());
        assertTrue(perCat.containsKey("tool-deny-list"), "tool-deny-list category expected: " + perCat.keySet());

        // per-matchedRule metrics non-empty (this is the evidence the chain's deny
        // paths were actually reached and captured by CollectingAuditLogger)
        Map<String, Integer> perRule = report.getPerMatchedRule();
        assertFalse(perRule.isEmpty(), "per-matchedRule metrics must be non-empty");
        assertTrue(perRule.containsKey("hardcoded_deny_list"),
                "expected hardcoded_deny_list bucket: " + perRule);
        assertTrue(perRule.containsKey("layer2_permission_matrix"),
                "expected layer2_permission_matrix bucket: " + perRule);
        assertTrue(perRule.containsKey("layer2_conflict_strategy"),
                "expected layer2_conflict_strategy bucket: " + perRule);
        // at least one pathAccess sub-rule bucket
        boolean hasPathRule = perRule.containsKey("sensitive_path_prefix")
                || perRule.containsKey("sensitive_path_env_file")
                || perRule.containsKey("sensitive_path_filename")
                || perRule.containsKey("path_traversal_defense");
        assertTrue(hasPathRule, "expected a pathAccess sub-rule bucket: " + perRule);

        // The shipped corpus encodes correct expectations, so every case should
        // pass (this is the strongest Anti-Hollow assertion: the real chain's
        // decisions match the declared expectations end-to-end).
        assertEquals(report.getTotalCases(), report.getPassed(),
                "all shipped corpus cases should match expectations; failed=" + collectFailures(report));
    }

    @Test
    void benignScenariosResolveAllow() {
        CheckpointTestReport report = new CheckpointTestRunner()
                .runDirectory(CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);
        CheckpointTestReport.CategorySlice benign = report.getPerCategory().get("benign");
        assertNotNull(benign);
        assertEquals(0, benign.getDenied(),
                "all benign cases must be ALLOW (0 denies), got denied=" + benign.getDenied());
        // confirm at least one benign ALLOW appears in the (allow) rule bucket
        assertTrue(report.getPerMatchedRule().containsKey("(allow)"),
                "(allow) bucket expected: " + report.getPerMatchedRule());
    }

    @Test
    void unsafeScenariosResolveDenyAtExpectedLayers() {
        CheckpointTestReport report = new CheckpointTestRunner()
                .runDirectory(CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);

        // tool-deny-list: all DENY at toolAccess
        CheckpointTestReport.CategorySlice tdl = report.getPerCategory().get("tool-deny-list");
        assertNotNull(tdl);
        assertEquals(tdl.getTotal(), tdl.getDenied(),
                "all tool-deny-list cases must DENY: denied=" + tdl.getDenied());

        // channel-matrix: the two GROUP cases DENY (the WEBUI control case is ALLOW)
        CheckpointTestReport.CategorySlice cmat = report.getPerCategory().get("channel-matrix");
        assertNotNull(cmat);
        assertTrue(cmat.getDenied() >= 2,
                "expected >= 2 channel-matrix denials (GROUP+ELEVATED), got " + cmat.getDenied());

        // write-intent-conflict: the two seeded cases DENY (the control case is ALLOW)
        CheckpointTestReport.CategorySlice wcon = report.getPerCategory().get("write-intent-conflict");
        assertNotNull(wcon);
        assertTrue(wcon.getDenied() >= 2,
                "expected >= 2 write-intent-conflict denials, got " + wcon.getDenied());

        // path-* scenarios: all DENY
        for (String cat : new String[]{"path-sensitive", "path-traversal"}) {
            CheckpointTestReport.CategorySlice slice = report.getPerCategory().get(cat);
            assertNotNull(slice, "missing category " + cat);
            assertEquals(slice.getTotal(), slice.getDenied(),
                    "all " + cat + " cases must DENY: denied=" + slice.getDenied());
        }
    }

    @Test
    void runnerRejectsNullBatchAndNullEntry() {
        CheckpointTestRunner runner = new CheckpointTestRunner();
        assertThrowsNop(() -> runner.run(null), "null batch must fail loud");
        assertThrowsNop(() -> runner.run(java.util.Arrays.asList(
                CheckpointTestCase.builder()
                        .id("x").category("c").toolName("read-file").sessionId("s")
                        .expectedDecision(SecurityCheckpoint.Decision.ALLOW).build(),
                null)),
                "null entry must fail loud");
    }

    private static String collectFailures(CheckpointTestReport report) {
        StringBuilder sb = new StringBuilder();
        for (CheckpointTestResult r : report.getResults()) {
            if (!r.isPassed()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(r.getCaseId()).append("->").append(r.getFailureReason());
            }
        }
        return sb.toString();
    }

    private static void assertThrowsNop(Runnable r, String message) {
        try {
            r.run();
            throw new AssertionError("expected NopAiAgentException: " + message);
        } catch (io.nop.ai.agent.engine.NopAiAgentException expected) {
            // expected
        }
    }
}
