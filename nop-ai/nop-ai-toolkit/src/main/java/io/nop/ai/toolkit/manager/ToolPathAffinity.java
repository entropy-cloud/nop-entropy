package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Plan 2298 (WI17): extracts the path-affinity key from a tool call's input
 * JSON — the reserved keys are {@code path}/{@code file_path}/{@code filePath};
 * the first string value wins. The dispatch path's single data source is
 * {@link AiToolCall#getInput()} JSON (the executor-side node-attr read is a
 * different source, out of WI17 scope).
 *
 * <p>Malformed input (non-JSON, non-map, non-string values) yields {@code null}
 * = "no affinity" — the call runs unqueued. The extractor must never become a
 * failure surface for tool execution (same discipline as the WI16 budget knob).
 */
public final class ToolPathAffinity {

    private static final Logger LOG = LoggerFactory.getLogger(ToolPathAffinity.class);

    static final String[] RESERVED_KEYS = {"path", "file_path", "filePath"};

    private ToolPathAffinity() {
    }

    /**
     * @return the path-affinity key, or {@code null} when the input carries no
     *         reserved path key (or is malformed)
     */
    public static String extract(AiToolCall call) {
        if (call == null) {
            return null;
        }
        String input = call.getInput();
        if (input == null || input.isEmpty()) {
            return null;
        }
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(input);
        } catch (Exception e) {
            LOG.debug("ToolPathAffinity: input is not JSON — no path affinity");
            return null;
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return null;
        }
        for (String key : RESERVED_KEYS) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}
