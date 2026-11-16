package io.nop.ib.json;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer for nop-ib's own state and feedback documents.
 *
 * <p>nop-ib deliberately does not pull a full JSON stack: every document it emits
 * (model.json, unit index, --json feedback) is a controlled map/list of strings,
 * numbers, booleans and null. If a document ever needs richer types, migrate to
 * the platform JSON stack as one step and delete this class.
 */
public final class JsonOut {
    private JsonOut() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Map<?, ?> map) {
            writeMap(sb, map);
        } else if (value instanceof Iterable<?> iter) {
            writeArray(sb, iter);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first)
                sb.append(',');
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> iter) {
        sb.append('[');
        boolean first = true;
        for (Object item : iter) {
            if (!first)
                sb.append(',');
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)) {
                        // constant-pool values can carry unpaired surrogates; replace to
                        // keep the encoded document valid UTF-8
                        sb.append(i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1))
                                ? c
                                : '\uFFFD');
                    } else if (Character.isLowSurrogate(c)) {
                        // keep only as the pair of a high surrogate; lone ones become U+FFFD
                        if (i > 0 && Character.isHighSurrogate(s.charAt(i - 1)))
                            sb.append(c);
                        else
                            sb.append('\uFFFD');
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
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
