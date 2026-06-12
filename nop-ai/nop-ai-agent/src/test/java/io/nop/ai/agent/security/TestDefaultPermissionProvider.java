package io.nop.ai.agent.security;

import io.nop.ai.agent.model.AgentPermissionModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultPermissionProvider {

    private AgentPermissionModel perm(String id, String resource, String action) {
        AgentPermissionModel m = new AgentPermissionModel();
        m.setId(id);
        m.setResource(resource);
        m.setAction(action);
        return m;
    }

    @Test
    void testEmptyPermissionsDeniesAll() {
        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("any_tool", "agent", "session",
                Collections.emptyList(), Collections.emptyList());
        assertFalse(p.isAllowed());
        assertNotNull(p.getReason());
    }

    @Test
    void testAgentLevelAllow() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("r1", "calculator", "allow"));
        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("calculator", "agent", "session",
                Collections.emptyList(), agentPerms);
        assertTrue(p.isAllowed());
    }

    @Test
    void testAgentLevelDeny() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("r1", "bash", "deny"));
        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("bash", "agent", "session",
                Collections.emptyList(), agentPerms);
        assertFalse(p.isAllowed());
        assertEquals("r1", p.getMatchedRuleId());
    }

    @Test
    void testSessionOverridePrecedesAgentRules() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("agent-allow", "calculator", "allow"));
        List<AgentPermissionModel> sessionPerms = List.of(
                perm("session-deny", "calculator", "deny"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("calculator", "agent", "session",
                sessionPerms, agentPerms);
        assertFalse(p.isAllowed());
        assertEquals("session-deny", p.getMatchedRuleId());
    }

    @Test
    void testDenyWinsOverAllowAtSameLevel() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("allow-all", "*", "allow"),
                perm("deny-shell", "bash", "deny"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("bash", "agent", "session",
                Collections.emptyList(), agentPerms);
        assertFalse(p.isAllowed());
        assertEquals("deny-shell", p.getMatchedRuleId());
    }

    @Test
    void testWildcardMatchesAnyTool() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("allow-all", "*", "allow"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("any_random_tool", "agent", "session",
                Collections.emptyList(), agentPerms);
        assertTrue(p.isAllowed());
    }

    @Test
    void testDefaultRulesUsedAsFallback() {
        List<AgentPermissionModel> defaultRules = List.of(
                perm("default-allow", "safe_tool", "allow"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider(defaultRules);
        Permission p = provider.resolve("safe_tool", "agent", "session",
                Collections.emptyList(), Collections.emptyList());
        assertTrue(p.isAllowed());
    }

    @Test
    void testNonListSessionPermissionsHandledGracefully() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionPermissions", "not-a-list");

        List<AgentPermissionModel> agentPerms = List.of(
                perm("r1", "calculator", "allow"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        provider.configure(agentPerms, metadata);

        Permission p = provider.resolve("calculator", "agent", "session");
        assertTrue(p.isAllowed());
    }

    @Test
    void testConfigureWithMetadata() {
        List<AgentPermissionModel> sessionPerms = List.of(
                perm("session-deny", "dangerous_tool", "deny"));
        List<AgentPermissionModel> agentPerms = List.of(
                perm("agent-allow", "*", "allow"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionPermissions", new ArrayList<>(sessionPerms));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        provider.configure(agentPerms, metadata);

        Permission p = provider.resolve("dangerous_tool", "agent", "session");
        assertFalse(p.isAllowed());
        assertEquals("session-deny", p.getMatchedRuleId());
    }

    @Test
    void testNoMatchDenies() {
        List<AgentPermissionModel> agentPerms = List.of(
                perm("r1", "other_tool", "allow"));

        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        Permission p = provider.resolve("calculator", "agent", "session",
                Collections.emptyList(), agentPerms);
        assertFalse(p.isAllowed());
    }

    @Test
    void testDefaultDenyWhenNoSources() {
        DefaultPermissionProvider provider = new DefaultPermissionProvider();
        provider.configure(null, null);
        Permission p = provider.resolve("tool", "agent", "session");
        assertFalse(p.isAllowed());
    }
}
