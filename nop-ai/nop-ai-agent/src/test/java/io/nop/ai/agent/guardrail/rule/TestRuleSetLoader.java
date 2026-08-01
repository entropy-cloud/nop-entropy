package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RuleSetLoader} YAML loading (design Decision A/F, mirrors
 * {@code CorpusLoader} pipeline). Self-contained: uses a test-resources VFS
 * rule-set, not the shipped Phase-3 sample.
 */
public class TestRuleSetLoader {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static GuardrailRuleSet loadSample() {
        IResource res = VirtualFileSystem.instance().getResource("/test-guardrail-rules/sample-ruleset.yaml");
        return new RuleSetLoader().load(res);
    }

    @Test
    void loadsValidRuleSetFromYaml() {
        GuardrailRuleSet rs = loadSample();
        assertNotNull(rs);
        assertEquals("test-ruleset", rs.getId());
        assertEquals(4, rs.size());
    }

    @Test
    void parsedRelationshipsPreserved() {
        GuardrailRuleSet rs = loadSample();
        GuardrailRule fin = rs.getRule("fin-transaction");
        assertNotNull(fin);
        assertTrue(fin.getExcludes().contains("general-chat"));
        assertTrue(fin.getDependsOn().contains("audit-trail"));
    }

    @Test
    void parsedDirectionAndAction() {
        GuardrailRuleSet rs = loadSample();
        assertEquals(GuardrailDirection.INPUT, rs.getRule("fin-transaction").getDirection());
        assertEquals(RuleAction.BLOCK, rs.getRule("fin-transaction").getAction());

        GuardrailRule redact = rs.getRule("redact-token");
        assertEquals(GuardrailDirection.OUTPUT, redact.getDirection());
        assertEquals(RuleAction.MODIFY, redact.getAction());
        assertEquals("[REDACTED]", redact.getModifyReplacement());

        // null direction (both) for general-chat
        GuardrailRule chat = rs.getRule("general-chat");
        assertNullDirection(chat);
    }

    private static void assertNullDirection(GuardrailRule r) {
        assertTrue(r.getDirection() == null, "rule without 'direction' field should be null (both)");
    }

    @Test
    void loadedRuleSetPassesFullValidation() {
        // Loading implies validation ran (id uniqueness + refs + acyclic).
        // Additionally assert no false-positive cycle on this diamond-ish graph.
        GuardrailRuleSet rs = loadSample();
        assertTrue(rs.contains("audit-trail"));
        assertFalse(rs.contains("ghost"));
    }

    @Test
    void nullResourceRejected() {
        assertThrows(NopAiAgentException.class, () -> new RuleSetLoader().load(null));
    }

    @Test
    void missingFileRejected() {
        IResource res = VirtualFileSystem.instance().getResource(
                "/test-guardrail-rules/_does_not_exist.yaml");
        assertThrows(NopAiAgentException.class, () -> new RuleSetLoader().load(res));
    }

    @Test
    void loadedThenResolverWorks() {
        GuardrailRuleSet rs = loadSample();
        RuleGraphResolver resolver = new RuleGraphResolver(rs);
        // hitting fin-transaction pulls in audit-trail and excludes general-chat
        java.util.Set<String> active = resolver.resolve(Collections.singleton("fin-transaction"));
        assertTrue(active.contains("fin-transaction"));
        assertTrue(active.contains("audit-trail"));
        assertFalse(active.contains("general-chat"));
    }

    @Test
    void multipleRuleSetsNoCrossContamination() {
        GuardrailRuleSet a = loadSample();
        GuardrailRule localRule = new GuardrailRule("local", null, "x", RuleAction.BLOCK,
                null, null, null, null, null);
        GuardrailRuleSet b = new GuardrailRuleSet("local-set", Arrays.asList(localRule));
        assertEquals("test-ruleset", a.getId());
        assertEquals("local-set", b.getId());
        assertTrue(a.contains("fin-transaction"));
        assertTrue(b.contains("local"));
        assertFalse(a.contains("local"));
        assertFalse(b.contains("fin-transaction"));
    }
}
