package io.nop.ai.agent.security;

import io.nop.ai.agent.model.AgentPermissionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultPermissionProvider implements IPermissionProvider {

    private final List<AgentPermissionModel> defaultRules;
    private List<AgentPermissionModel> agentPermissions;
    private List<AgentPermissionModel> sessionPermissions;

    public DefaultPermissionProvider() {
        this.defaultRules = Collections.emptyList();
    }

    public DefaultPermissionProvider(List<AgentPermissionModel> defaultRules) {
        this.defaultRules = defaultRules != null ? defaultRules : Collections.emptyList();
    }

    public void configure(List<AgentPermissionModel> agentPermissions,
                          Map<String, Object> metadata) {
        this.agentPermissions = agentPermissions != null ? agentPermissions : Collections.emptyList();
        this.sessionPermissions = extractSessionPermissions(metadata);
    }

    @Override
    public Permission resolve(String toolName, String agentName, String sessionId) {
        List<List<AgentPermissionModel>> sources = buildSources();

        if (sources.isEmpty()) {
            return Permission.deny("No permission rules configured");
        }

        for (List<AgentPermissionModel> source : sources) {
            for (AgentPermissionModel rule : source) {
                if (matches(rule, toolName) && "deny".equals(rule.getAction())) {
                    return Permission.deny(
                            "Denied by rule: " + rule.getId(),
                            rule.getId());
                }
            }
        }

        for (List<AgentPermissionModel> source : sources) {
            for (AgentPermissionModel rule : source) {
                if (matches(rule, toolName) && "allow".equals(rule.getAction())) {
                    return Permission.allow();
                }
            }
        }

        return Permission.deny("No matching permission rule for tool: " + toolName);
    }

    public Permission resolve(String toolName, String agentName, String sessionId,
                              List<AgentPermissionModel> sessionPerms,
                              List<AgentPermissionModel> agentPerms) {
        this.sessionPermissions = sessionPerms != null ? sessionPerms : Collections.emptyList();
        this.agentPermissions = agentPerms != null ? agentPerms : Collections.emptyList();
        return resolve(toolName, agentName, sessionId);
    }

    private List<List<AgentPermissionModel>> buildSources() {
        List<List<AgentPermissionModel>> sources = new ArrayList<>(3);
        if (sessionPermissions != null && !sessionPermissions.isEmpty()) {
            sources.add(sessionPermissions);
        }
        if (agentPermissions != null && !agentPermissions.isEmpty()) {
            sources.add(agentPermissions);
        }
        if (!defaultRules.isEmpty()) {
            sources.add(defaultRules);
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private List<AgentPermissionModel> extractSessionPermissions(Map<String, Object> metadata) {
        if (metadata == null) return Collections.emptyList();
        Object value = metadata.get("sessionPermissions");
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (!(item instanceof AgentPermissionModel)) {
                    return Collections.emptyList();
                }
            }
            return (List<AgentPermissionModel>) list;
        }
        return Collections.emptyList();
    }

    private boolean matches(AgentPermissionModel rule, String toolName) {
        if (rule.getResource() == null) return false;
        return "*".equals(rule.getResource()) || rule.getResource().equals(toolName);
    }
}
