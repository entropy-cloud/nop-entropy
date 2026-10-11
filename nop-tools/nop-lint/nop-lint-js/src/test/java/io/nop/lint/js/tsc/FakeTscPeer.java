package io.nop.lint.js.tsc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The scripted fake Node peer of the Phase 1 unit matrix (plan item: "用可
 * scripting 的 fake Node 入口模拟"): a plain executable speaking the exact
 * wire protocol, one deterministic behavior per argv mode. Spawning the
 * current JVM again needs no Node installation, so the process-management
 * matrix is pure JUnit. Deterministic contract of the {@code ok} mode:
 * {@code getTypeAtLocation} renders {@code FakeType:<line>:<col>}, and
 * {@code isTypeAssignableTo} answers assignable unless {@code expectedType}
 * is {@code never}; the program cache key is the tsconfig file's content
 * hash, so rewriting the file invalidates it exactly like the real peer.
 */
public final class FakeTscPeer {

    private final String mode;
    private final String markerFile;
    private final Map<String, String> cacheKeys = new HashMap<>();

    public static void main(String[] args) throws Exception {
        FakeTscPeer peer = new FakeTscPeer(args.length > 0 ? args[0] : "ok",
                args.length > 1 ? args[1] : null);
        peer.run();
    }

    private FakeTscPeer(String mode, String markerFile) {
        this.mode = mode;
        this.markerFile = markerFile;
    }

    private void run() throws IOException {
        switch (mode) {
            case "exit-before-ready" -> System.exit(3);
            case "no-typescript" -> emit(map("type", "ready", "node", "v25.0.0"));
            default -> emit(ready());
        }
        serveRequests();
    }

    private Map<String, Object> ready() {
        return map("type", "ready", "node", "v25.3.0", "typescript", "9.9.9-fake");
    }

    private void serveRequests() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> request = parse(line);
            int id = ((Number) request.get("id")).intValue();
            String op = String.valueOf(request.get("op"));
            boolean isQuery = op.startsWith("getTypeAtLocation") || op.startsWith("isTypeAssignableTo");
            if (isQuery) {
                // The once-modes leave their marker behind before hanging or
                // crashing, so the recycled incarnation knows the failure
                // already happened and answers normally.
                if ("hang-once-then-ok".equals(mode) && !markerExists()) {
                    touchMarker();
                    parkForever();
                }
                if (shouldCrashQuery()) {
                    touchMarker();
                    System.exit(5);
                }
            }
            switch (mode) {
                case "garbage-response" -> System.out.println("definitely not json");
                default -> System.out.println(encode(answer(id, request)));
            }
            System.out.flush();
        }
    }

    /**
     * True when this query should die: {@code crash-on-first-query} dies on
     * every query (exhausting any restart budget), {@code crash-once-then-ok}
     * dies only before its marker file exists.
     */
    private boolean shouldCrashQuery() throws IOException {
        if ("crash-on-first-query".equals(mode)) {
            return true;
        }
        if ("crash-once-then-ok".equals(mode)) {
            return !markerExists();
        }
        return false;
    }

    private boolean markerExists() {
        return markerFile != null && Files.exists(Path.of(markerFile));
    }

    private void touchMarker() throws IOException {
        if (markerFile == null) {
            throw new IllegalStateException("mode '" + mode + "' needs a marker file argument");
        }
        Files.createFile(Path.of(markerFile));
    }

    private Map<String, Object> answer(int id, Map<String, Object> request) {
        return switch (String.valueOf(request.get("op"))) {
            case "initProject" -> initProject(id, request);
            case "getTypeAtLocation" -> map("id", id, "ok", true, "result",
                    map("type", "FakeType:" + request.get("line") + ":" + request.get("col")));
            case "isTypeAssignableTo" -> assignable(id, request);
            case "shutdown" -> map("id", id, "ok", true, "result", map("bye", true));
            default -> error(id, "UNKNOWN_OP", "fake peer knows no op " + request.get("op"));
        };
    }

    private Map<String, Object> initProject(int id, Map<String, Object> request) {
        Path tsconfig = Path.of(String.valueOf(request.get("tsConfigPath")));
        if (!Files.isRegularFile(tsconfig)) {
            return error(id, "TSCONFIG_INVALID", "fake peer found no tsconfig at " + tsconfig);
        }
        String content;
        try {
            content = Files.readString(tsconfig);
        } catch (IOException e) {
            return error(id, "TSCONFIG_INVALID", "fake peer cannot read " + tsconfig + ": " + e);
        }
        String key = "hash-" + Integer.toHexString(content.hashCode());
        String path = tsconfig.toAbsolutePath().normalize().toString();
        String previous = cacheKeys.put(path, key);
        return map("id", id, "ok", true, "result", map(
                "cacheKey", key,
                "rebuilt", !key.equals(previous),
                "fileCount", content.split("\n").length));
    }

    private Map<String, Object> assignable(int id, Map<String, Object> request) {
        Object expected = request.get("expectedType");
        boolean ok = expected == null || !"never".equals(expected);
        return map("id", id, "ok", true, "result", map(
                "assignable", ok,
                "actualType", "FakeType",
                "expectedType", String.valueOf(expected)));
    }

    private void parkForever() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> parse(String line) throws IOException {
        // The fake peer only needs the flat fields the tests send; the real
        // JSON codec is exercised from the Java side (TscProtocolTest).
        Map<String, Object> fields = new LinkedHashMap<>();
        String body = line.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IOException("not a JSON object: " + line);
        }
        for (String pair : body.substring(1, body.length() - 1).split(",")) {
            int colon = pair.indexOf(':');
            String key = unquote(pair.substring(0, colon).trim());
            String rawValue = pair.substring(colon + 1).trim();
            Object value;
            if (rawValue.startsWith("\"")) {
                value = unquote(rawValue);
            } else if ("true".equals(rawValue) || "false".equals(rawValue)) {
                value = Boolean.parseBoolean(rawValue);
            } else {
                value = Long.parseLong(rawValue);
            }
            fields.put(key, value);
        }
        return fields;
    }

    private static String unquote(String raw) {
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String encode(Map<String, Object> frame) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : frame.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':');
            encodeValue(sb, entry.getValue());
        }
        return sb.append('}').toString();
    }

    /**
     * Values are strings, numbers, booleans, or nested maps (the {@code
     * result}/{@code error} objects) — nested containers encode recursively
     * so every frame is real JSON, never a Java {@code toString}.
     */
    private static void encodeValue(StringBuilder sb, Object value) {
        if (value instanceof Map<?, ?> nested) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(String.valueOf(entry.getKey()))).append(':');
                encodeValue(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof String s) {
            sb.append(quote(s));
        } else {
            sb.append(value);
        }
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Map<String, Object> error(int id, String code, String message) {
        return map("id", id, "ok", false, "error", map("code", code, "message", message));
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    private static void emit(Map<String, Object> frame) {
        System.out.println(encode(frame));
        System.out.flush();
    }
}
