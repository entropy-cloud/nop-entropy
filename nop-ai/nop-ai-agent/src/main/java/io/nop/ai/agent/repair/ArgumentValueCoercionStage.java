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
 * Stage 3 — Argument value coercion (schema-aware).
 *
 * <p>Coerces string-encoded scalar values to typed values where the tool's XDEF
 * schema declares the expected primitive type and the coercion is unambiguous:
 * <ul>
 *   <li>{@code "42"} → {@code 42} (int) when declared type is {@code int}</li>
 *   <li>{@code "3.14"} → {@code 3.14} (double) when declared type is
 *       {@code number}/{@code float}/{@code double}/{@code long}</li>
 *   <li>{@code "true"}/{@code "false"} → boolean when declared type is
 *       {@code boolean}</li>
 * </ul>
 *
 * <p>Non-primitive declared types ({@code string}, {@code enum:*},
 * {@code full-path}, list-typed params) are left unchanged. Ambiguous values
 * (e.g. {@code "42abc"}) are left unchanged. When {@code IToolManager} is null
 * or the tool schema is unavailable, this stage degrades to a no-op.
 *
 * <p>The declared type is obtained by parsing the XDEF attribute/body notation
 * from {@code AiToolModel.getSchema()} (NOT via {@code ToolSchemaConverter},
 * which returns null for all real tools).
 */
public class ArgumentValueCoercionStage implements IToolCallRepairer {

    private final IToolManager toolManager;

    public ArgumentValueCoercionStage(IToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        if (toolManager == null) {
            return toolCall;
        }

        Map<String, Object> args = toolCall.getArguments();
        if (args == null || args.isEmpty()) {
            return toolCall;
        }

        Map<String, String> paramTypes = resolveParamTypes(toolCall.getName());
        if (paramTypes.isEmpty()) {
            return toolCall;
        }

        boolean changed = false;
        Map<String, Object> coerced = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object value = entry.getValue();
            String declaredType = paramTypes.get(entry.getKey());
            Object coercedValue = coerceValue(value, declaredType);
            coerced.put(entry.getKey(), coercedValue);
            if (coercedValue != value) {
                changed = true;
            }
        }

        if (!changed) {
            return toolCall;
        }

        ChatToolCall repaired = toolCall.copy();
        repaired.setArguments(coerced);
        return repaired;
    }

    private Map<String, String> resolveParamTypes(String toolName) {
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

    static Object coerceValue(Object value, String declaredType) {
        if (declaredType == null || declaredType.isEmpty()) {
            return value;
        }
        if (!(value instanceof String)) {
            return value;
        }

        String strValue = (String) value;

        switch (declaredType) {
            case "int":
            case "integer":
                return coerceInt(strValue, value);
            case "long":
                return coerceLong(strValue, value);
            case "number":
            case "float":
            case "double":
                return coerceDouble(strValue, value);
            case "boolean":
            case "bool":
                return coerceBoolean(strValue, value);
            default:
                return value;
        }
    }

    private static Object coerceInt(String strValue, Object original) {
        try {
            return Integer.parseInt(strValue.trim());
        } catch (NumberFormatException e) {
            return original;
        }
    }

    private static Object coerceLong(String strValue, Object original) {
        try {
            return Long.parseLong(strValue.trim());
        } catch (NumberFormatException e) {
            return original;
        }
    }

    private static Object coerceDouble(String strValue, Object original) {
        try {
            return Double.parseDouble(strValue.trim());
        } catch (NumberFormatException e) {
            return original;
        }
    }

    private static Object coerceBoolean(String strValue, Object original) {
        String trimmed = strValue.trim();
        if ("true".equals(trimmed)) {
            return Boolean.TRUE;
        }
        if ("false".equals(trimmed)) {
            return Boolean.FALSE;
        }
        return original;
    }
}
