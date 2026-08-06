package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link CheckpointTestCaseLoader} (plan 2249 Phase 3 exit
 * criteria): loads the shipped nop-native corpus without parse errors, verifies
 * field completeness, ≥18 cases, and ≥4 distinct matchedRules.
 */
public class TestCheckpointTestCaseLoader {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void loadsFullCorpusWithoutErrors() {
        List<CheckpointTestCase> cases = CheckpointTestCaseLoader.loadDirectory(
                CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);

        assertTrue(cases.size() >= 18,
                "corpus must contain >= 18 cases, got " + cases.size());
        // ≥ 5 scenario directories → ≥ 5 distinct categories
        Set<String> categories = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Set<String> matchedRules = new HashSet<>();
        for (CheckpointTestCase tc : cases) {
            assertCaseFieldsComplete(tc);
            assertTrue(ids.add(tc.getId()), "duplicate case id: " + tc.getId());
            categories.add(tc.getCategory());
            if (tc.getExpectedMatchedRule() != null) {
                matchedRules.add(tc.getExpectedMatchedRule());
            }
        }
        assertTrue(categories.size() >= 5,
                "expected >= 5 scenario categories, got " + categories);
        assertTrue(matchedRules.size() >= 4,
                "expected >= 4 distinct expectedMatchedRule values, got " + matchedRules);
    }

    @Test
    void spotCheckParsedFields() {
        List<CheckpointTestCase> cases = CheckpointTestCaseLoader.loadDirectory(
                CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);
        CheckpointTestCase tdl001 = findById(cases, "tdl-001");
        assertNotNull(tdl001);
        assertEquals("tool-deny-list", tdl001.getCategory());
        assertEquals("bash", tdl001.getToolName());
        assertEquals("rm -rf /tmp/scratch", tdl001.getArgs().get("command"));
        assertEquals(SecurityCheckpoint.Decision.DENY, tdl001.getExpectedDecision());
        assertEquals("hardcoded_deny_list", tdl001.getExpectedMatchedRule());
        assertEquals("s-tdl-001", tdl001.getSessionId());

        // channel/principal parsed from YAML
        CheckpointTestCase cmat001 = findById(cases, "cmat-001");
        assertNotNull(cmat001);
        assertEquals(io.nop.ai.agent.security.ChannelKind.GROUP, cmat001.getChannelKind());
        assertNotNull(cmat001.getPrincipal());

        // conflict-seed fields parsed from YAML
        CheckpointTestCase wcon001 = findById(cases, "wcon-001");
        assertNotNull(wcon001);
        assertTrue(wcon001.hasConflictSeed());
        assertEquals("/tmp/wf-target1.txt", wcon001.getPrePopConflictPath());
        assertEquals("other-session", wcon001.getPrePopConflictSession());
    }

    @Test
    void corpusCoversAtLeastFourDistinctMatchedRules() {
        List<CheckpointTestCase> cases = CheckpointTestCaseLoader.loadDirectory(
                CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR);
        Set<String> rules = new HashSet<>();
        for (CheckpointTestCase tc : cases) {
            if (tc.getExpectedMatchedRule() != null) {
                rules.add(tc.getExpectedMatchedRule());
            }
        }
        // hardcoded_deny_list + sensitive_path_* + path_traversal_defense +
        // layer2_permission_matrix + layer2_conflict_strategy
        assertTrue(rules.contains("hardcoded_deny_list"), "missing hardcoded_deny_list: " + rules);
        assertTrue(rules.contains("path_traversal_defense")
                        || rules.contains("sensitive_path_prefix")
                        || rules.contains("sensitive_path_env_file")
                        || rules.contains("sensitive_path_filename"),
                "missing a pathAccess sub-rule: " + rules);
        assertTrue(rules.contains("layer2_permission_matrix"), "missing layer2_permission_matrix: " + rules);
        assertTrue(rules.contains("layer2_conflict_strategy"), "missing layer2_conflict_strategy: " + rules);
    }

    @Test
    void missingDirectoryReturnsEmptyList() {
        List<CheckpointTestCase> cases =
                CheckpointTestCaseLoader.loadDirectory("/nop/ai/agent/checkpoint-test/does-not-exist");
        assertNotNull(cases);
        assertTrue(cases.isEmpty());
    }

    @Test
    void malformedYamlFailsLoud() {
        // A YAML resource whose root is not a list must fail loud, not silently skip.
        // Kept in a separate fixtures directory so it does not pollute the corpus scan.
        IResource bad = VirtualFileSystem.instance().getResource(
                "/nop/ai/agent/checkpoint-test-fixtures/malformed-nonlist.yaml");
        assertTrue(bad.exists(), "malformed fixture must be on the test classpath");
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> CheckpointTestCaseLoader.load(bad));
    }

    private static void assertCaseFieldsComplete(CheckpointTestCase tc) {
        assertNotNull(tc.getId(), "id required");
        assertNotNull(tc.getCategory(), "category required");
        assertNotNull(tc.getToolName(), "toolName required");
        assertNotNull(tc.getSessionId(), "sessionId required");
        assertNotNull(tc.getArgs(), "args must be non-null (empty map allowed)");
        assertNotNull(tc.getExpectedDecision(), "expectedDecision required");
        assertFalse(tc.getToolName().isEmpty(), "toolName must not be empty");
        assertFalse(tc.getSessionId().isEmpty(), "sessionId must not be empty");
    }

    private static CheckpointTestCase findById(List<CheckpointTestCase> cases, String id) {
        for (CheckpointTestCase tc : cases) {
            if (id.equals(tc.getId())) {
                return tc;
            }
        }
        return null;
    }
}
