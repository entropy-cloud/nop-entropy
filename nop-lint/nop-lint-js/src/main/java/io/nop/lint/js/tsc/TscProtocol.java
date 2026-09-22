package io.nop.lint.js.tsc;

import io.nop.core.lang.json.JsonTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Java half of the tsc bridge wire protocol (design 06 §5.3, item 20
 * Decision): newline-delimited JSON frames over the resident process's
 * stdin/stdout. One startup ready frame carries the peer's Node and
 * typescript versions (the handshake rejects a peer whose typescript
 * binding is missing or unidentifiable); every request is a JSON object
 * with a correlation {@code id} and an {@code op} field, and every response
 * either carries {@code ok:true} plus the op's result fields or
 * {@code ok:false} plus a structured {@code error:{code,message}} frame.
 * Nothing on this level returns a silent null.
 */
public final class TscProtocol {

    /**
     * The ready-frame discriminator.
     */
    public static final String READY_TYPE = "ready";

    private TscProtocol() {
    }

    /**
     * Encodes one request frame (plus the trailing newline the peer's line
     * reader needs).
     */
    public static String encode(Map<String, Object> request) {
        return JsonTool.stringify(request) + "\n";
    }

    /**
     * Decodes one response frame; a line that does not parse is a protocol
     * violation surfaced as {@code BAD_FRAME}, never swallowed.
     */
    public static Map<String, Object> decode(String line) {
        try {
            return JsonTool.parseMap(line);
        } catch (Exception e) {
            throw new TscQueryException("BAD_FRAME", "peer frame is not valid JSON: " + line, e);
        }
    }

    /**
     * The next correlation id frame for {@code op}, with string parameters.
     */
    public static Map<String, Object> request(int id, String op, Object... keyValues) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", id);
        request.put("op", op);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            request.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return request;
    }

    /**
     * Validates the handshake frame: it must be the ready frame and must
     * identify a usable typescript binding (version string present), per the
     * fail-visible environment contract — a peer without typescript is an
     * unavailable bridge, not a degraded one.
     *
     * @throws TscBridgeUnavailableException describing exactly what the
     *                                      handshake is missing
     */
    public static void validateReady(Map<String, Object> frame) {
        if (frame == null || !READY_TYPE.equals(text(frame, "type"))) {
            throw new TscBridgeUnavailableException(
                    "tsc bridge handshake failed: first peer frame was not a ready frame: " + frame);
        }
        String typescriptVersion = text(frame, "typescript");
        if (typescriptVersion == null || typescriptVersion.isBlank()) {
            throw new TscBridgeUnavailableException("tsc bridge handshake failed: peer reports no typescript "
                    + "binding (node " + text(frame, "node") + "); install the 'typescript' package "
                    + "next to the helper script (ai-dev/tools devDependency)");
        }
    }

    /**
     * Extracts a string field, or null when absent.
     */
    public static String text(Map<String, Object> frame, String key) {
        Object value = frame.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Extracts an int field; a missing or malformed field is a protocol
     * violation, not a zero.
     */
    public static int intValue(Map<String, Object> frame, String key) {
        Object value = frame.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new TscQueryException("BAD_FRAME", "frame field '" + key + "' is not a number: " + frame);
    }

    /**
     * True when the decoded frame reports success; frames without the field
     * are protocol violations.
     */
    public static boolean isOk(Map<String, Object> frame) {
        Object ok = frame.get("ok");
        if (!(ok instanceof Boolean success)) {
            throw new TscQueryException("BAD_FRAME", "frame carries no ok flag: " + frame);
        }
        return success;
    }

    /**
     * The structured error of a failed response frame.
     */
    public static TscQueryException errorOf(int id, Map<String, Object> frame) {
        Object error = frame.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            return new TscQueryException(String.valueOf(errorMap.get("code")),
                    "tsc bridge request " + id + " failed: " + errorMap.get("message"));
        }
        return new TscQueryException("BAD_FRAME", "error frame without error object: " + frame);
    }

    /**
     * The {@code result} object of a successful response frame.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resultOf(int id, Map<String, Object> frame) {
        Object result = frame.get("result");
        if (!(result instanceof Map)) {
            throw new TscQueryException("BAD_FRAME", "success frame without result object: " + frame);
        }
        return (Map<String, Object>) result;
    }
}
