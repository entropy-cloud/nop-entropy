package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;

import java.util.Set;

/**
 * Stage 1 — Tool-name normalization.
 *
 * <p>Canonicalizes the tool name by lowercasing and normalizing separators
 * ({@code -} and {@code .} to {@code _}). If the canonical form uniquely matches
 * a tool in the agent's declared tool set ({@code ctx.getAgentModel().getTools()}),
 * the name is repaired to the declared tool name. If there is no match or the
 * match is ambiguous, the name is left unchanged so the downstream access-check
 * denies unknown tools with a clear reason.
 *
 * <p>This stage is schema-agnostic (does not need {@code IToolManager}). It
 * degrades to canonicalize-only (no set-match) when the declared tool set is
 * unavailable or empty.
 */
public class ToolNameNormalizationStage implements IToolCallRepairer {

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        String name = toolCall.getName();
        if (name == null || name.isEmpty()) {
            return toolCall;
        }

        String canonical = canonicalize(name);
        if (canonical.equals(name)) {
            return toolCall;
        }

        Set<String> declaredTools = getDeclaredTools(ctx);
        if (declaredTools == null || declaredTools.isEmpty()) {
            return toolCall;
        }

        String matchedName = null;
        int matchCount = 0;
        for (String declared : declaredTools) {
            if (declared != null && canonical.equals(canonicalize(declared))) {
                matchedName = declared;
                matchCount++;
            }
        }

        if (matchCount == 1 && matchedName != null) {
            ChatToolCall repaired = toolCall.copy();
            repaired.setName(matchedName);
            return repaired;
        }

        return toolCall;
    }

    static String canonicalize(String name) {
        return name.toLowerCase().replace('-', '_').replace('.', '_');
    }

    private static Set<String> getDeclaredTools(AgentExecutionContext ctx) {
        if (ctx == null) {
            return null;
        }
        AgentModel model = ctx.getAgentModel();
        if (model == null) {
            return null;
        }
        return model.getTools();
    }
}
