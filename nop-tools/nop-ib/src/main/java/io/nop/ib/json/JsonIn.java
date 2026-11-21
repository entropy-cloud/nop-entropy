package io.nop.ib.json;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.parse.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin delegate over the platform JSON stack (analysis
 * 2026-11-22-nop-ib-nop-git-platform-reuse): the hand-rolled parser this class
 * used to contain was a full duplicate of nop-core's JsonTool/JsonParser —
 * including its own surrogate-pair and escape handling — with zero behavioral
 * difference that ib relies on. Public surface kept stable (ib call sites
 * unchanged); NopIbJsonException kept as a marker for callers that catch it,
 * now thrown for shape mismatches only (JsonTool raises NopException itself).
 */
public final class JsonIn {
    private JsonIn() {
    }

    public static Object parse(String text) {
        try {
            return JsonTool.parse(text);
        } catch (Exception e) {
            // ib's error contract (exit 3 usage/config errors) keys off
            // NopIbJsonException; platform NopException stays inside
            throw new NopIbJsonException(e.getMessage() == null ? "malformed JSON" : e.getMessage());
        }
    }

    /** Convenience: parse and require a top-level object. */
    public static Map<String, Object> asMapOrEmpty(Object parsed) {
        if (parsed == null)
            return new LinkedHashMap<>();
        if (!(parsed instanceof Map))
            throw new NopIbJsonException("expected JSON object at top level");
        return JsonOut.asMap(parsed);
    }

    public static Map<String, Object> readAsMap(String text) {
        return asMapOrEmpty(parse(text));
    }

    /** Parse or return null on any malformed input (best-effort payload extraction). */
    public static Object silentParse(String text) {
        try {
            return parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wave 10 service payload: CLI output is a whitespace-separated sequence of
     * JSON documents (NDJSON whose strings may contain newlines). Parses all
     * consecutive values via the platform JsonParser (checkEndAfterParse=false,
     * one scanner reused across values); returns a single value when one, a
     * list otherwise, null when any segment is malformed.
     */
    public static Object silentParseSequence(String text) {
        try {
            TextScanner sc = TextScanner.fromString(SourceLocation.UNKNOWN, text);
            JsonParser parser = new JsonParser().checkEndAfterParse(false);
            List<Object> values = new ArrayList<>();
            while (!sc.isEnd()) {
                values.add(parser.parseJsonDoc(sc));
            }
            if (values.isEmpty())
                return null;
            return values.size() == 1 ? values.get(0) : values;
        } catch (Exception e) {
            return null;
        }
    }

    public static class NopIbJsonException extends RuntimeException {
        public NopIbJsonException(String message) {
            super(message);
        }
    }
}
