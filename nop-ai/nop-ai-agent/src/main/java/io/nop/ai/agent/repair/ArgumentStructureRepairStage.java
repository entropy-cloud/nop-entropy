package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.api.core.json.JSON;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 2 — Argument structure repair (schema-agnostic).
 *
 * <p>Guarantees that {@code arguments} is a non-null Map. Handles common LLM
 * packaging malformations:
 * <ul>
 *   <li>{@code null}/absent arguments → empty map</li>
 *   <li>arguments delivered as a JSON string → parsed to Map</li>
 *   <li>arguments wrapped in a one-element array → unwrapped element</li>
 * </ul>
 *
 * <p>Single-wrapper-key unwrap is <b>excluded</b> from this schema-agnostic
 * stage — without schema info there is no safe way to distinguish a redundant
 * wrapper key from a legitimate single-object argument.
 *
 * <p>Due to Java type erasure and lenient JSON deserialization, the
 * {@code arguments} field (declared as {@code Map<String,Object>}) may at
 * runtime hold a {@code String} or {@code List}. This stage detects and repairs
 * those cases by checking the actual runtime type.
 */
public class ArgumentStructureRepairStage implements IToolCallRepairer {

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        Object rawArgs = toolCall.getArguments();

        if (rawArgs instanceof Map) {
            return toolCall;
        }

        ChatToolCall repaired = new ChatToolCall();
        repaired.setId(toolCall.getId());
        repaired.setName(toolCall.getName());
        repaired.setArguments(normalizeArguments(rawArgs));
        return repaired;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeArguments(Object rawArgs) {
        if (rawArgs == null) {
            return new LinkedHashMap<>();
        }

        if (rawArgs instanceof String) {
            String json = (String) rawArgs;
            try {
                Object parsed = JSON.parse(json);
                if (parsed instanceof Map) {
                    return new LinkedHashMap<>((Map<String, Object>) parsed);
                }
            } catch (Exception ignored) {
                // Malformed JSON — fall through to empty map
            }
            return new LinkedHashMap<>();
        }

        if (rawArgs instanceof List) {
            List<?> list = (List<?>) rawArgs;
            if (list.size() == 1 && list.get(0) instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) list.get(0));
            }
            return new LinkedHashMap<>();
        }

        return new LinkedHashMap<>();
    }
}
