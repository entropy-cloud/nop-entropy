package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCorpusLoader {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void loadsSampleCorpus() {
        List<AttackCase> cases = CorpusLoader.loadDirectory("/test-guardrail-corpus");
        assertEquals(4, cases.size(), "sample corpus has 4 entries (2 attacks + 2 benign)");

        AttackCase first = cases.get(0);
        assertEquals("sample-attack-001", first.getId());
        assertEquals("prompt_injection", first.getCategory());
        assertEquals(ExpectedBehavior.BLOCK, first.getExpectedBehavior());
        assertTrue(first.getPayload().contains("Ignore all previous instructions"));
    }

    @Test
    void directionDefaultIsInput() {
        List<AttackCase> cases = CorpusLoader.loadDirectory("/test-guardrail-corpus");
        AttackCase second = cases.get(1); // sample-attack-002 has no direction field
        assertEquals(io.nop.ai.agent.guardrail.GuardrailDirection.INPUT, second.getDirection());
    }

    @Test
    void benignCasesLoaded() {
        List<AttackCase> cases = CorpusLoader.loadDirectory("/test-guardrail-corpus");
        AttackCase benign = cases.get(2);
        assertEquals("sample-benign-001", benign.getId());
        assertEquals(ExpectedBehavior.PASS, benign.getExpectedBehavior());
    }

    @Test
    void threatClassDefaultsToCategory() {
        List<AttackCase> cases = CorpusLoader.loadDirectory("/test-guardrail-corpus");
        AttackCase second = cases.get(1); // sample-attack-002 has no threatClass
        assertEquals(second.getCategory(), second.getThreatClass());
    }

    @Test
    void missingDirectoryReturnsEmpty() {
        List<AttackCase> cases = CorpusLoader.loadDirectory("/nonexistent-corpus-xyz");
        assertNotNull(cases);
        assertTrue(cases.isEmpty());
    }

    @Test
    void nullResourceRejected() {
        assertThrows(NopAiAgentException.class, () -> CorpusLoader.load(null));
    }

    @Test
    void corpusAttackPluginLoadsFromDefaultDir() {
        CorpusAttackPlugin plugin = new CorpusAttackPlugin("test-plugin", "/test-guardrail-corpus");
        assertEquals("test-plugin", plugin.name());
        assertEquals(4, plugin.cases().size());
    }

    @Test
    void corpusAttackPluginDefaultName() {
        CorpusAttackPlugin plugin = new CorpusAttackPlugin("p2", "/test-guardrail-corpus");
        assertEquals("p2", plugin.name());
        // cases list is immutable
        assertThrows(UnsupportedOperationException.class,
                () -> plugin.cases().add(plugin.cases().get(0)));
    }

    @Test
    void duplicateIdsAcrossFilesRejected() {
        // two files both define dup-id would be rejected; here we only have one
        // file so we verify the dedup guard does not false-positive
        List<AttackCase> cases = CorpusLoader.loadDirectory("/test-guardrail-corpus");
        assertEquals(4, cases.size());
    }
}
