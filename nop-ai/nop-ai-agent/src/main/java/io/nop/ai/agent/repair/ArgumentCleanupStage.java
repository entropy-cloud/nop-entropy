package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.core.lang.xml.XNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stage 4 — Argument cleanup (schema-aware).
 *
 * <p>Removes arguments whose value is {@code null}. When the tool's XDEF schema
 * is available, also removes arguments not declared in the schema (noise/extra
 * arguments that confuse tool execution). Required-argument gaps are
 * <b>not</b> fabricated — the call passes through so downstream tool execution
 * reports the gap with an attributable error.
 *
 * <p>When {@code IToolManager} is null or the tool schema is unavailable, only
 * null removal applies (schema-dependent noise removal is skipped).
 *
 * <p>The declared-parameter set is obtained by parsing the XDEF attribute/body
 * notation from {@code AiToolModel.getSchema()} (NOT via
 * {@code ToolSchemaConverter}, which returns null for all real tools).
 */
public class ArgumentCleanupStage implements IToolCallRepairer {

    private final IToolManager toolManager;

    public ArgumentCleanupStage(IToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        Map<String, Object> args = toolCall.getArguments();

        if (args == null) {
            ChatToolCall repaired = new ChatToolCall();
            repaired.setId(toolCall.getId());
            repaired.setName(toolCall.getName());
            repaired.setArguments(new LinkedHashMap<>());
            return repaired;
        }

        if (args.isEmpty()) {
            return toolCall;
        }

        Map<String, String> declaredParams = resolveParamTypes(toolCall.getName());
        boolean hasSchema = !declaredParams.isEmpty();

        boolean changed = false;
        Map<String, Object> cleaned = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getValue() == null) {
                changed = true;
                continue;
            }

            if (hasSchema && !declaredParams.containsKey(entry.getKey())) {
                changed = true;
                continue;
            }

            cleaned.put(entry.getKey(), entry.getValue());
        }

        if (!changed) {
            return toolCall;
        }

        ChatToolCall repaired = toolCall.copy();
        repaired.setArguments(cleaned);
        return repaired;
    }

    private Map<String, String> resolveParamTypes(String toolName) {
        if (toolManager == null) {
            return Collections.emptyMap();
        }
        try {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null) {
                return Collections.emptyMap();
            }
            XNode schema = toolModel.getSchema();
            return ToolSchemaParser.parseParameterTypes(schema);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
