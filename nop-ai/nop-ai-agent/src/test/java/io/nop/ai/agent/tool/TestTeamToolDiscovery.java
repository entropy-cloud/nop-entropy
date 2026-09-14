package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolPlanResolver;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M7-P1 round-3 (plan 2026-09-15-0116-1, Phase 3): proves the 5 team tools are
 * discoverable through the LLM standard discovery path — the VFS
 * {@code /nop/ai/tools/*.tool.xml} files loaded by {@link ToolManagerImpl}.
 *
 * <p>Prior to the fix the executors existed as IoC beans (collected by the
 * toolkit provider) and in the ACL matrix, but there was no {@code .tool.xml}
 * definition: {@code listTools()} only enumerates VFS {@code .tool.xml} files
 * and {@code AgentToolPlanResolver.buildToolDefinitions} silently skips a
 * whitelisted tool whose {@code loadTool} returns null — so the LLM could
 * never discover or plan a team tool call.
 *
 * <ul>
 *   <li>{@code loadTool(name)} non-null for all 5 team tools (VFS resource +
 *       tool.xdef schema parse).</li>
 *   <li>{@code listTools()} enumerates all 5 (standard discovery path).</li>
 *   <li>{@code AgentToolPlanResolver.buildToolDefinitions} with a whitelist
 *       containing the 5 names yields all 5 definitions — the silent-null skip
 *       path no longer applies.</li>
 * </ul>
 */
class TestTeamToolDiscovery {

    private static final Set<String> TEAM_TOOLS = new HashSet<>(Arrays.asList(
            "team-send-message",
            "team-status",
            "team-task-create",
            "team-task-update",
            "team-execute-flow"
    ));

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void loadToolResolvesAllFiveTeamTools() {
        ToolManagerImpl manager = new ToolManagerImpl();
        for (String name : TEAM_TOOLS) {
            AiToolModel model = manager.loadTool(name);
            assertNotNull(model, "loadTool(" + name + ") must resolve a tool model from VFS .tool.xml");
            assertNotNull(model.getDescription(), name + " must carry a description");
        }
    }

    @Test
    void listToolsEnumeratesAllFiveTeamTools() {
        ToolManagerImpl manager = new ToolManagerImpl();
        List<AiToolModel> tools = manager.listTools();
        Set<String> names = tools.stream()
                .map(AiToolModel::getName)
                .collect(Collectors.toSet());
        assertTrue(names.containsAll(TEAM_TOOLS),
                "listTools() must enumerate all 5 team tools. Missing: " + TEAM_TOOLS.stream()
                        .filter(n -> !names.contains(n)).collect(Collectors.toSet()));
    }

    @Test
    void buildToolDefinitionsWhitelistKeepsTeamTools() {
        ToolManagerImpl manager = new ToolManagerImpl();
        AgentToolPlanResolver resolver = new AgentToolPlanResolver(manager);

        AgentModel model = new AgentModel();
        model.setTools(TEAM_TOOLS);

        List<ChatToolDefinition> defs = resolver.buildToolDefinitions(model, null);
        Set<String> defNames = defs.stream()
                .map(ChatToolDefinition::getName)
                .collect(Collectors.toSet());
        assertTrue(defNames.containsAll(TEAM_TOOLS),
                "whitelisted team tools must not be silently skipped. Missing: " + TEAM_TOOLS.stream()
                        .filter(n -> !defNames.contains(n)).collect(Collectors.toSet()));
    }
}