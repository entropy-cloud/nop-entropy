package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultToolAccessChecker {

    private final DefaultToolAccessChecker checker = new DefaultToolAccessChecker();

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    @Test
    void testBashDenied() {
        ToolAccessResult result = checker.checkAccess("bash", newContext());
        assertEquals(false, result.isAllowed());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("bash"));
        assertEquals("hardcoded_deny_list", result.getMatchedRule());
    }

    @Test
    void testWriteFileDenied() {
        ToolAccessResult result = checker.checkAccess("write-file", newContext());
        assertEquals(false, result.isAllowed());
        assertTrue(result.getReason().contains("write-file"));
    }

    @Test
    void testDeleteFileDenied() {
        ToolAccessResult result = checker.checkAccess("delete-file", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testMoveFileDenied() {
        ToolAccessResult result = checker.checkAccess("move-file", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testPatchFileDenied() {
        ToolAccessResult result = checker.checkAccess("patch-file", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testHttpRequestDenied() {
        ToolAccessResult result = checker.checkAccess("http-request", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testApplyDeltaDenied() {
        ToolAccessResult result = checker.checkAccess("apply-delta", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testGraphqlQueryDenied() {
        ToolAccessResult result = checker.checkAccess("graphql-query", newContext());
        assertEquals(false, result.isAllowed());
    }

    @Test
    void testAllowedTool() {
        ToolAccessResult result = checker.checkAccess("calculator", newContext());
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getMatchedRule());
    }

    @Test
    void testAnotherAllowedTool() {
        ToolAccessResult result = checker.checkAccess("web_search", newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testCaseInsensitiveDeny() {
        ToolAccessResult upper = checker.checkAccess("BASH", newContext());
        assertEquals(false, upper.isAllowed());

        ToolAccessResult mixed = checker.checkAccess("Bash", newContext());
        assertEquals(false, mixed.isAllowed());

        ToolAccessResult lower = checker.checkAccess("bash", newContext());
        assertEquals(false, lower.isAllowed());
    }

    @Test
    void testNullToolNameAllowed() {
        ToolAccessResult result = checker.checkAccess(null, newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testAllowAllToolAccessChecker() {
        AllowAllToolAccessChecker allowAll = new AllowAllToolAccessChecker();
        ToolAccessResult result = allowAll.checkAccess("bash", newContext());
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getMatchedRule());
    }
}
