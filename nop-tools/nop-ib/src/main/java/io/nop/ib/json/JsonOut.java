package io.nop.ib.json;

import io.nop.core.lang.json.JsonTool;

import java.util.List;
import java.util.Map;

/**
 * Thin delegate over the platform JSON stack (analysis
 * 2026-11-22-nop-ib-nop-git-platform-reuse): the hand-rolled writer this class
 * used to contain (own surrogate-pair and escape handling) was a behavioral
 * duplicate of nop-core JsonTool.stringify. Public surface kept stable — ib
 * call sites unchanged.
 */
public final class JsonOut {
    private JsonOut() {
    }

    public static String write(Object value) {
        return JsonTool.stringify(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
